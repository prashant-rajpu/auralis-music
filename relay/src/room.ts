import type { IceConfig } from "./ice.js";
import { mintIceServers } from "./ice.js";
import {
  MAX_CHAT_HISTORY,
  MAX_MEMBERS,
  MAX_QUEUE_ITEMS,
  RateLimiter,
  ValidationError,
  parseClientMessage,
  removeFirstMatch,
  type ClientMessage,
  type IceServer,
  type Member,
  type QueueEntry,
  type RoomSnapshot,
  type ServerMessage,
} from "./protocol.js";
import type { Playback, RoomRecord, RoomStore } from "./store.js";

/** Members may send ten messages a second, bursting to twenty. */
const RATE_CAPACITY = 20;
const RATE_PER_SECOND = 10;

/** Re-mint a little before expiry, so a call never starts on a credential about to die. */
const ICE_REFRESH_MARGIN_MS = 30 * 60 * 1000;

/**
 * Just enough of a socket for a room to talk to one.
 *
 * The room is the part worth testing — presence, host handover, what a hostile frame does to a
 * running room — and none of that should need a listening port. `ws` satisfies this as it is.
 */
export interface Sink {
  send(data: string): void;
  close(code?: number, reason?: string): void;
}

export interface Connection {
  readonly socket: Sink;
  readonly memberId: string;
  readonly name: string;
  readonly token: string;
  readonly joinedAtMs: number;
  readonly timeZone: string;
  buffering: boolean;
  limiter: RateLimiter;
}

export type JoinRefusal = "room_full";

/**
 * One listening session.
 *
 * The reason this exists rather than a public pub/sub topic: it is a trustworthy source of
 * `serverMs`. Two phones cannot agree on a position from their own clocks — phone clocks drift by
 * seconds — so every broadcast carries the room's clock and each client measures its offset from
 * it. Presence and identity come along for free, because the socket *is* the identity: the server
 * stamps `senderId` on everything, and a client cannot claim to be the other person.
 *
 * Durable state lives in [RoomStore]; who is connected right now lives here and is deliberately
 * not persisted, because a member list restored from disk is a list of people who are not there.
 */
export class Room {
  private readonly connections = new Set<Connection>();
  private cachedIce: { iceServers: IceServer[]; expiresAtMs: number } | null = null;

  constructor(
    readonly record: RoomRecord,
    private readonly store: RoomStore,
    private readonly ice: IceConfig,
    private readonly now: () => number = Date.now,
  ) {}

  get isEmpty(): boolean {
    return this.connections.size === 0;
  }

  get memberCount(): number {
    return this.connections.size;
  }

  join(
    socket: Sink,
    options: { name: string; token: string; timeZone: string; memberId: string },
  ): Connection | JoinRefusal {
    if (this.connections.size >= MAX_MEMBERS) return "room_full";

    const nowMs = this.now();
    const connection: Connection = {
      socket,
      memberId: options.memberId,
      name: options.name.slice(0, 64),
      token: options.token,
      joinedAtMs: nowMs,
      timeZone: options.timeZone.slice(0, 64),
      buffering: false,
      limiter: new RateLimiter(RATE_CAPACITY, RATE_PER_SECOND, nowMs),
    };
    this.connections.add(connection);

    // First socket in an empty room hosts it; a returning host reclaims by token.
    const hostPresent = [...this.connections].some((c) => c.memberId === this.record.hostId);
    if (!hostPresent) {
      this.record.hostId = connection.memberId;
    } else if (options.token === this.record.hostToken) {
      this.record.hostId = connection.memberId;
    }

    this.touch(nowMs);
    this.send(connection, {
      type: "welcome",
      memberId: connection.memberId,
      hostId: this.record.hostId,
      serverMs: nowMs,
      state: this.snapshot(),
    });
    this.broadcastPresence();
    return connection;
  }

  /** A raw frame off the wire. Size and shape are checked before anything is done with it. */
  receive(connection: Connection, raw: string): void {
    const nowMs = this.now();

    if (!connection.limiter.tryConsume(nowMs)) {
      this.send(connection, { type: "error", code: "rate_limited", message: "Slow down" });
      return;
    }

    let message: ClientMessage;
    try {
      message = parseClientMessage(raw);
    } catch (error) {
      const failure = error instanceof ValidationError ? error : null;
      this.send(connection, {
        type: "error",
        code: failure?.code ?? "bad_message",
        message: failure?.message ?? "Could not read that message",
      });
      return;
    }

    this.touch(nowMs);
    this.handle(connection, message, nowMs);
  }

  leave(connection: Connection): void {
    if (!this.connections.delete(connection)) return;

    if (this.record.hostId === connection.memberId) {
      // Do not strand the room on a host that left. Hand over immediately to whoever is still
      // here rather than running a grace timer: the original host reclaims with their token the
      // moment they reconnect, which is the same outcome without a window where nobody can drive.
      const next = [...this.connections][0];
      this.record.hostId = next?.memberId ?? "";
      this.store.touch();
    }

    this.broadcastPresence();
  }

  private handle(connection: Connection, message: ClientMessage, serverMs: number): void {
    switch (message.type) {
      case "ping":
        this.send(connection, { type: "pong", at: message.at, serverMs });
        return;

      case "playback": {
        const playback: Playback = {
          track: message.track,
          positionMs: message.positionMs,
          isPlaying: message.isPlaying,
          speed: message.speed,
          atServerMs: serverMs,
        };
        this.record.playback = playback;
        this.store.touch();
        this.broadcast({
          type: "playback",
          senderId: connection.memberId,
          serverMs,
          track: message.track,
          positionMs: message.positionMs,
          isPlaying: message.isPlaying,
          speed: message.speed,
        });
        return;
      }

      case "seek": {
        if (this.record.playback !== null) {
          this.record.playback = {
            ...this.record.playback,
            positionMs: message.positionMs,
            atServerMs: serverMs,
          };
          this.store.touch();
        }
        this.broadcast({
          type: "seek",
          senderId: connection.memberId,
          serverMs,
          positionMs: message.positionMs,
        });
        return;
      }

      case "queueAdd": {
        if (this.record.queue.length >= MAX_QUEUE_ITEMS) {
          this.send(connection, { type: "error", code: "bad_message", message: "Queue is full" });
          return;
        }
        this.record.queue.push({ track: message.track, addedBy: connection.memberId });
        this.store.touch();
        this.broadcast({ type: "queue", serverMs, items: this.record.queue });
        return;
      }

      case "queueRemove": {
        this.record.queue = removeFirstMatch(this.record.queue, message.providerId);
        this.store.touch();
        this.broadcast({ type: "queue", serverMs, items: this.record.queue });
        return;
      }

      case "buffering": {
        connection.buffering = message.buffering;
        this.broadcastPresence();
        return;
      }

      case "chat": {
        this.record.chat.push({
          senderId: connection.memberId,
          serverMs,
          ciphertext: message.ciphertext,
        });
        while (this.record.chat.length > MAX_CHAT_HISTORY) this.record.chat.shift();
        this.store.touch();
        this.broadcast({
          type: "chat",
          senderId: connection.memberId,
          serverMs,
          ciphertext: message.ciphertext,
        });
        return;
      }

      case "reaction":
        this.broadcast({
          type: "reaction",
          senderId: connection.memberId,
          serverMs,
          emoji: message.emoji,
        });
        return;

      case "ice": {
        const ice = this.iceServers(serverMs);
        this.send(connection, {
          type: "ice",
          serverMs,
          iceServers: ice.iceServers,
          expiresAtMs: ice.expiresAtMs,
        });
        return;
      }

      case "bye":
        connection.socket.close(1000, "bye");
        return;
    }
  }

  /**
   * Cached per room rather than per member: both members of a call can share one credential, and
   * without the cache every rejoin would re-derive one on the join path.
   */
  private iceServers(nowMs: number): { iceServers: IceServer[]; expiresAtMs: number } {
    const cached = this.cachedIce;
    if (cached !== null) {
      const permanent = cached.expiresAtMs === 0;
      if (permanent || cached.expiresAtMs - nowMs > ICE_REFRESH_MARGIN_MS) return cached;
    }
    const fresh = mintIceServers(this.ice, nowMs);
    this.cachedIce = fresh;
    return fresh;
  }

  private touch(nowMs: number): void {
    this.record.lastSeenMs = nowMs;
    this.store.touch();
  }

  private members(excludeId?: string): Member[] {
    return [...this.connections]
      .filter((connection) => connection.memberId !== excludeId)
      .map((connection) => ({
        id: connection.memberId,
        name: connection.name,
        isHost: connection.memberId === this.record.hostId,
        buffering: connection.buffering,
        joinedAtMs: connection.joinedAtMs,
        timeZone: connection.timeZone,
      }));
  }

  private snapshot(): RoomSnapshot {
    return {
      members: this.members(),
      hostId: this.record.hostId,
      playback: this.record.playback,
      queue: this.record.queue,
      chat: this.record.chat,
    };
  }

  private broadcastPresence(): void {
    this.broadcast({
      type: "presence",
      members: this.members(),
      hostId: this.record.hostId,
      serverMs: this.now(),
    });
  }

  private broadcast(message: ServerMessage): void {
    const payload = JSON.stringify(message);
    for (const connection of this.connections) {
      try {
        connection.socket.send(payload);
      } catch {
        // A socket that died between listing and sending is handled by its close event.
      }
    }
  }

  private send(connection: Connection, message: ServerMessage): void {
    try {
      connection.socket.send(JSON.stringify(message));
    } catch {
      // Same as broadcast: the close handler cleans up.
    }
  }
}
