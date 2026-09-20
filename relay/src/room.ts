import { DurableObject } from "cloudflare:workers";
import type { Env } from "./index";
import {
  MAX_CHAT_HISTORY,
  MAX_MEMBERS,
  MAX_QUEUE_ITEMS,
  PUBLIC_STUN,
  type ClientMessage,
  type IceServer,
  type Member,
  type QueueEntry,
  type RoomSnapshot,
  type ServerMessage,
  ValidationError,
  RateLimiter,
  normaliseIceServers,
  parseClientMessage,
  removeFirstMatch,
} from "./protocol";

/** Members may send ten messages a second, bursting to twenty. */
const RATE_CAPACITY = 20;
const RATE_PER_SECOND = 10;

/** Long enough that a couple's code stays theirs between sessions. */
const ROOM_IDLE_TTL_MS = 30 * 24 * 60 * 60 * 1000;

/**
 * How stale the liveness stamp is allowed to get before it is rewritten. Clock sync pings arrive
 * every few seconds; without this, every one of them would cost a storage write and an alarm reset.
 */
const TOUCH_INTERVAL_MS = 5 * 60 * 1000;

/**
 * How long a minted TURN credential lives, and how much of that is left before it is replaced.
 *
 * Twelve hours is far longer than any call, which is the point: the credential is fetched once and
 * a call that starts hours later still works. The margin means a call never begins with a
 * credential that is about to expire underneath it.
 */
const ICE_TTL_SECONDS = 12 * 60 * 60;
const ICE_REFRESH_MARGIN_MS = 30 * 60 * 1000;

interface CachedIce {
  iceServers: IceServer[];
  expiresAtMs: number;
}

interface Attachment {
  memberId: string;
  name: string;
  token: string;
  joinedAtMs: number;
  buffering: boolean;
  timeZone: string;
}

interface Playback {
  track: QueueEntry["track"];
  positionMs: number;
  isPlaying: boolean;
  speed: number;
  atServerMs: number;
}

/**
 * One listening session.
 *
 * The reason this exists rather than a public pub/sub topic: it is a trustworthy source of
 * `serverMs`. Two phones cannot agree on a position from their own clocks — phone clocks drift by
 * seconds — so every broadcast carries the room's clock, and each client measures its offset from
 * it. Presence and identity come along for free because the socket is the identity.
 *
 * Uses WebSocket Hibernation: between messages the object is evicted from memory while the sockets
 * stay open, so an idle room costs nothing. All durable state therefore lives in storage or in the
 * socket attachments, never in instance fields that would not survive eviction.
 */
export class Room extends DurableObject<Env> {
  /**
   * Deliberately not durable. Eviction only happens when a room has been quiet, and a quiet
   * member's bucket would have refilled to capacity anyway, so losing it changes nothing.
   */
  private readonly limiters = new Map<string, RateLimiter>();

  constructor(ctx: DurableObjectState, env: Env) {
    super(ctx, env);
  }

  async fetch(request: Request): Promise<Response> {
    const url = new URL(request.url);

    if (url.pathname.endsWith("/create")) return this.create(request);
    if (url.pathname.endsWith("/ws")) return this.openSession(request, url);

    return new Response("Not found", { status: 404 });
  }

  private async create(request: Request): Promise<Response> {
    const body = (await request.json().catch(() => ({}))) as { hostToken?: string };
    const hostToken = body.hostToken;
    if (typeof hostToken !== "string" || hostToken.length < 16) {
      return Response.json({ error: "bad_request" }, { status: 400 });
    }

    // Room codes are short enough to collide eventually, and a second create must never be able to
    // overwrite a live room's host token — that would hand an existing couple's room to a stranger.
    // The caller retries with a fresh code instead.
    const existing = await this.ctx.storage.get<number>("createdAtMs");
    if (existing !== undefined) return Response.json({ error: "code_taken" }, { status: 409 });

    await this.ctx.storage.put("hostToken", hostToken);
    await this.ctx.storage.put("createdAtMs", Date.now());
    await this.touch();
    return Response.json({ ok: true });
  }

  private async openSession(request: Request, url: URL): Promise<Response> {
    if (request.headers.get("Upgrade") !== "websocket") {
      return new Response("Expected websocket", { status: 426 });
    }

    const createdAt = await this.ctx.storage.get<number>("createdAtMs");
    if (createdAt === undefined) return Response.json({ error: "not_found" }, { status: 404 });

    if (this.ctx.getWebSockets().length >= MAX_MEMBERS) {
      return Response.json({ error: "room_full" }, { status: 409 });
    }

    const token = url.searchParams.get("token") ?? "";
    if (token.length < 16) return Response.json({ error: "unauthorized" }, { status: 401 });

    const name = (url.searchParams.get("name") ?? "Listener").slice(0, 64);
    const timeZone = (url.searchParams.get("tz") ?? "").slice(0, 64);
    const memberId = crypto.randomUUID();

    const pair = new WebSocketPair();
    const [client, server] = [pair[0], pair[1]];

    this.ctx.acceptWebSocket(server);
    const attachment: Attachment = {
      memberId,
      name,
      token,
      joinedAtMs: Date.now(),
      buffering: false,
      timeZone,
    };
    server.serializeAttachment(attachment);

    // First socket in an empty room hosts it; a returning host reclaims by token.
    const hostToken = await this.ctx.storage.get<string>("hostToken");
    let hostId = await this.ctx.storage.get<string>("hostId");
    if (hostId === undefined || this.memberById(hostId) === null) {
      hostId = memberId;
      await this.ctx.storage.put("hostId", hostId);
    } else if (hostToken !== undefined && token === hostToken && hostId !== memberId) {
      hostId = memberId;
      await this.ctx.storage.put("hostId", hostId);
    }

    await this.touch();

    const snapshot = await this.snapshot(hostId);
    this.send(server, {
      type: "welcome",
      memberId,
      hostId,
      serverMs: Date.now(),
      state: snapshot,
    });
    await this.broadcastPresence(hostId);

    return new Response(null, { status: 101, webSocket: client });
  }

  async webSocketMessage(socket: WebSocket, raw: string | ArrayBuffer): Promise<void> {
    const attachment = socket.deserializeAttachment() as Attachment | null;
    if (attachment === null) return;

    const text = typeof raw === "string" ? raw : new TextDecoder().decode(raw);

    if (!this.allow(attachment.memberId)) {
      this.send(socket, { type: "error", code: "rate_limited", message: "Slow down" });
      return;
    }

    let message: ClientMessage;
    try {
      message = parseClientMessage(text);
    } catch (error) {
      const failure = error instanceof ValidationError ? error : null;
      this.send(socket, {
        type: "error",
        code: failure?.code ?? "bad_message",
        message: failure?.message ?? "Could not read that message",
      });
      return;
    }

    await this.handle(socket, attachment, message);
  }

  private async handle(
    socket: WebSocket,
    attachment: Attachment,
    message: ClientMessage,
  ): Promise<void> {
    const serverMs = Date.now();
    await this.touch();

    switch (message.type) {
      case "ping":
        this.send(socket, { type: "pong", at: message.at, serverMs });
        return;

      case "playback": {
        const playback: Playback = {
          track: message.track,
          positionMs: message.positionMs,
          isPlaying: message.isPlaying,
          speed: message.speed,
          atServerMs: serverMs,
        };
        await this.ctx.storage.put("playback", playback);
        this.broadcast({
          type: "playback",
          senderId: attachment.memberId,
          serverMs,
          track: message.track,
          positionMs: message.positionMs,
          isPlaying: message.isPlaying,
          speed: message.speed,
        });
        return;
      }

      case "seek": {
        const playback = await this.ctx.storage.get<Playback>("playback");
        if (playback !== undefined) {
          await this.ctx.storage.put("playback", {
            ...playback,
            positionMs: message.positionMs,
            atServerMs: serverMs,
          });
        }
        this.broadcast({
          type: "seek",
          senderId: attachment.memberId,
          serverMs,
          positionMs: message.positionMs,
        });
        return;
      }

      case "queueAdd": {
        const queue = (await this.ctx.storage.get<QueueEntry[]>("queue")) ?? [];
        if (queue.length >= MAX_QUEUE_ITEMS) {
          this.send(socket, { type: "error", code: "bad_message", message: "Queue is full" });
          return;
        }
        queue.push({ track: message.track, addedBy: attachment.memberId });
        await this.ctx.storage.put("queue", queue);
        this.broadcast({ type: "queue", serverMs, items: queue });
        return;
      }

      case "queueRemove": {
        const queue = (await this.ctx.storage.get<QueueEntry[]>("queue")) ?? [];
        const next = removeFirstMatch(queue, message.providerId);
        await this.ctx.storage.put("queue", next);
        this.broadcast({ type: "queue", serverMs, items: next });
        return;
      }

      case "buffering": {
        socket.serializeAttachment({ ...attachment, buffering: message.buffering });
        await this.broadcastPresence();
        return;
      }

      case "chat": {
        const chat = (await this.ctx.storage.get<RoomSnapshot["chat"]>("chat")) ?? [];
        chat.push({ senderId: attachment.memberId, serverMs, ciphertext: message.ciphertext });
        while (chat.length > MAX_CHAT_HISTORY) chat.shift();
        await this.ctx.storage.put("chat", chat);
        this.broadcast({
          type: "chat",
          senderId: attachment.memberId,
          serverMs,
          ciphertext: message.ciphertext,
        });
        return;
      }

      case "reaction":
        this.broadcast({
          type: "reaction",
          senderId: attachment.memberId,
          serverMs,
          emoji: message.emoji,
        });
        return;

      case "ice": {
        const ice = await this.iceServers();
        this.send(socket, {
          type: "ice",
          serverMs,
          iceServers: ice.iceServers,
          expiresAtMs: ice.expiresAtMs,
        });
        return;
      }

      case "bye":
        socket.close(1000, "bye");
        return;
    }
  }

  async webSocketClose(socket: WebSocket): Promise<void> {
    await this.afterDeparture(socket);
  }

  async webSocketError(socket: WebSocket): Promise<void> {
    await this.afterDeparture(socket);
  }

  /**
   * Short-lived TURN credentials, minted from the account's TURN key and cached for the room.
   *
   * The key itself never leaves the relay. Each phone gets a credential that expires, which is
   * what stops the relay's URL being usable as free bandwidth by anyone who finds it.
   *
   * Cached per room rather than per member: both members of a call can share one credential, and
   * without the cache every rejoin would cost an API round trip on the join path.
   *
   * With no TURN key configured this falls back to public STUN, and so does a failed mint. That is
   * a working call for most networks and a failed one for the rest — better than no call at all,
   * and the app can see which it got.
   */
  private async iceServers(): Promise<CachedIce> {
    const cached = await this.ctx.storage.get<CachedIce>("ice");
    if (cached !== undefined && cached.expiresAtMs - Date.now() > ICE_REFRESH_MARGIN_MS) {
      return cached;
    }

    const keyId = this.env.TURN_KEY_ID;
    const apiToken = this.env.TURN_KEY_API_TOKEN;
    if (!keyId || !apiToken) return { iceServers: [PUBLIC_STUN], expiresAtMs: 0 };

    // Deliberately no customIdentifier: the only identifier this room has is its code, and the
    // code is what the chat key is derived from. It does not go into anyone's analytics.
    const minted = await fetch(
      `https://rtc.live.cloudflare.com/v1/turn/keys/${encodeURIComponent(keyId)}/credentials/generate-ice-servers`,
      {
        method: "POST",
        headers: {
          Authorization: `Bearer ${apiToken}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({ ttl: ICE_TTL_SECONDS }),
      },
    ).catch(() => null);

    if (minted === null || !minted.ok) {
      console.warn("turn mint failed", minted?.status ?? "network");
      return { iceServers: [PUBLIC_STUN], expiresAtMs: 0 };
    }

    const payload = await minted.json().catch(() => null);
    const fresh: CachedIce = {
      iceServers: normaliseIceServers(payload),
      expiresAtMs: Date.now() + ICE_TTL_SECONDS * 1000,
    };
    await this.ctx.storage.put("ice", fresh);
    return fresh;
  }

  private async afterDeparture(socket: WebSocket): Promise<void> {
    const attachment = socket.deserializeAttachment() as Attachment | null;
    const goneId = attachment?.memberId;
    if (goneId !== undefined) this.limiters.delete(goneId);

    const hostId = await this.ctx.storage.get<string>("hostId");
    if (hostId !== undefined && goneId === hostId) {
      // Do not strand the room on a host that left. Hand over immediately to whoever is still here
      // rather than running a grace timer: the original host reclaims with their token the moment
      // they reconnect, which is the same outcome without a window where nobody can drive playback.
      const next = this.members(undefined, goneId)[0];
      if (next !== undefined) await this.ctx.storage.put("hostId", next.id);
    }

    await this.broadcastPresence(undefined, goneId);
  }

  /** Called on every interaction; an untouched room expires so old codes do not accumulate. */
  private async touch(): Promise<void> {
    const now = Date.now();
    const lastSeen = (await this.ctx.storage.get<number>("lastSeenMs")) ?? 0;
    if (now - lastSeen < TOUCH_INTERVAL_MS) return;
    await this.ctx.storage.put("lastSeenMs", now);
    await this.ctx.storage.setAlarm(now + ROOM_IDLE_TTL_MS);
  }

  async alarm(): Promise<void> {
    const lastSeen = (await this.ctx.storage.get<number>("lastSeenMs")) ?? 0;
    if (Date.now() - lastSeen >= ROOM_IDLE_TTL_MS) {
      await this.ctx.storage.deleteAll();
      return;
    }
    // Touched since this alarm was set: re-arm for the remaining life rather than expiring early.
    await this.ctx.storage.setAlarm(lastSeen + ROOM_IDLE_TTL_MS);
  }

  private allow(memberId: string): boolean {
    const now = Date.now();
    let limiter = this.limiters.get(memberId);
    if (limiter === undefined) {
      limiter = new RateLimiter(RATE_CAPACITY, RATE_PER_SECOND, now);
      this.limiters.set(memberId, limiter);
    }
    return limiter.tryConsume(now);
  }

  private attachments(excludeId?: string): Attachment[] {
    return this.ctx
      .getWebSockets()
      .map((socket) => socket.deserializeAttachment() as Attachment | null)
      .filter((a): a is Attachment => a !== null && a.memberId !== excludeId);
  }

  /**
   * `excludeId` drops a member who is on their way out. A socket may still be listed while its
   * close handler runs, and a presence update that includes someone who has just left is worse
   * than one that is a moment early.
   */
  private members(hostId?: string, excludeId?: string): Member[] {
    return this.attachments(excludeId).map((attachment) => ({
      id: attachment.memberId,
      name: attachment.name,
      isHost: attachment.memberId === hostId,
      buffering: attachment.buffering,
      joinedAtMs: attachment.joinedAtMs,
      timeZone: attachment.timeZone,
    }));
  }

  private memberById(memberId: string): Attachment | null {
    return this.attachments().find((a) => a.memberId === memberId) ?? null;
  }

  private async snapshot(hostId: string): Promise<RoomSnapshot> {
    const [playback, queue, chat] = await Promise.all([
      this.ctx.storage.get<Playback>("playback"),
      this.ctx.storage.get<QueueEntry[]>("queue"),
      this.ctx.storage.get<RoomSnapshot["chat"]>("chat"),
    ]);
    return {
      members: this.members(hostId),
      hostId,
      playback: playback ?? null,
      queue: queue ?? [],
      chat: chat ?? [],
    };
  }

  private async broadcastPresence(knownHostId?: string, excludeId?: string): Promise<void> {
    const hostId = knownHostId ?? (await this.ctx.storage.get<string>("hostId")) ?? "";
    this.broadcast({
      type: "presence",
      members: this.members(hostId, excludeId),
      hostId,
      serverMs: Date.now(),
    });
  }

  private broadcast(message: ServerMessage): void {
    const payload = JSON.stringify(message);
    for (const socket of this.ctx.getWebSockets()) {
      try {
        socket.send(payload);
      } catch {
        // A socket that died between listing and sending is handled by webSocketClose.
      }
    }
  }

  private send(socket: WebSocket, message: ServerMessage): void {
    try {
      socket.send(JSON.stringify(message));
    } catch {
      // Same as broadcast: the close handler cleans up.
    }
  }
}
