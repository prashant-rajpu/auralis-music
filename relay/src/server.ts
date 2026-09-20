import { randomBytes, randomUUID } from "node:crypto";
import { createServer, type IncomingMessage, type ServerResponse } from "node:http";
import { WebSocketServer, type WebSocket } from "ws";
import { iceConfigFromEnv } from "./ice.js";
import { CODE_LENGTH, generateRoomCode, isValidRoomCode } from "./protocol.js";
import { Room, type Connection } from "./room.js";
import { RoomStore } from "./store.js";

/**
 * The Auralis listen-together relay.
 *
 * Two routes, and nothing else:
 *
 *   POST /rooms              -> { code, hostToken }
 *   GET  /rooms/{code}/ws    -> the session socket
 *
 * A room is private to whoever was told its code: there is deliberately no room listing and no way
 * to look one up by anything but the exact code.
 *
 * A plain Node process on purpose. It ran on Durable Objects before, which was elegant and tied
 * the whole feature to one company's account. Everything here runs anywhere that can run a
 * container, and the wire format did not change, so nothing in the app had to.
 */

/** Long enough that a couple's code stays theirs between sessions. */
const ROOM_IDLE_TTL_MS = 30 * 24 * 60 * 60 * 1000;
const SWEEP_INTERVAL_MS = 60 * 60 * 1000;

/** Per-IP join budget, so one caller cannot mint rooms or hammer join in a loop. */
const JOIN_WINDOW_MS = 60_000;
const JOIN_MAX_PER_WINDOW = 30;

/** Enough that exhausting them means the code space is full, not that we were unlucky. */
const CODE_ATTEMPTS = 8;

/** A ceiling on memory, and on what an abusive caller can cost. */
const MAX_ROOMS = 50_000;

/** A socket that stops answering pings is gone, whatever TCP still believes. */
const HEARTBEAT_MS = 30_000;

const port = Number.parseInt(process.env.PORT ?? "8787", 10);
const dataDir = process.env.DATA_DIR === "" ? null : (process.env.DATA_DIR ?? "./data");
const ice = iceConfigFromEnv(process.env);
const store = new RoomStore(dataDir, ROOM_IDLE_TTL_MS);
const live = new Map<string, Room>();

/**
 * Whether someone arriving with a code this relay has never heard of gets the room anyway.
 *
 * It turns on by itself when there is no persistent store, and that is the case it exists for.
 * Without a disk, every redeploy forgets every room — so a code the two of you have been using
 * all week comes back "not found", and a working room has to be abandoned over a server restart
 * neither of you did. When the relay cannot know whether a code was live before, refusing it is
 * a guess, and it is the guess that breaks things.
 *
 * With a disk it stays off, because there a missing room really is missing: expired after thirty
 * days, or mistyped. Saying so is more useful than quietly opening an empty room to sit in.
 */
const adoptUnknownRooms =
  process.env.ADOPT_UNKNOWN_ROOMS === "true" ||
  (process.env.ADOPT_UNKNOWN_ROOMS !== "false" && store.isEphemeral);

function token(): string {
  return randomBytes(24).toString("hex");
}

/**
 * Behind a load balancer the socket address is the balancer's. Every host worth using sets
 * `X-Forwarded-For`; the first entry is the client, the rest are proxies.
 */
function clientIp(request: IncomingMessage): string {
  const forwarded = request.headers["x-forwarded-for"];
  const header = Array.isArray(forwarded) ? forwarded[0] : forwarded;
  const first = header?.split(",")[0]?.trim();
  return first || request.socket.remoteAddress || "unknown";
}

const joinAttempts = new Map<string, { count: number; windowStartMs: number }>();

function rateLimited(ip: string): boolean {
  const now = Date.now();
  if (joinAttempts.size >= 4096) {
    for (const [key, entry] of joinAttempts) {
      if (now - entry.windowStartMs > JOIN_WINDOW_MS) joinAttempts.delete(key);
    }
  }
  const entry = joinAttempts.get(ip);
  if (entry === undefined || now - entry.windowStartMs > JOIN_WINDOW_MS) {
    joinAttempts.set(ip, { count: 1, windowStartMs: now });
    return false;
  }
  entry.count += 1;
  return entry.count > JOIN_MAX_PER_WINDOW;
}

/** A room only exists in memory while someone is in it; its durable half is always in the store. */
function roomFor(code: string): Room | null {
  const existing = live.get(code);
  if (existing !== undefined) return existing;

  let record = store.get(code);
  if (record === undefined) {
    if (!adoptUnknownRooms || store.size >= MAX_ROOMS) return null;
    // No host token: whoever arrives first hosts it, and nobody can later claim it by token.
    // Handing out a token here would hand it to whoever asked, which is worse than no token.
    record = store.create(code, "", Date.now()) ?? undefined;
    if (record === undefined) return null;
  }

  const room = new Room(record, store, ice);
  live.set(code, room);
  return room;
}

function json(response: ServerResponse, status: number, body: unknown): void {
  const payload = JSON.stringify(body);
  response.writeHead(status, {
    "content-type": "application/json",
    "content-length": Buffer.byteLength(payload),
  });
  response.end(payload);
}

const server = createServer((request, response) => {
  const url = new URL(request.url ?? "/", `http://${request.headers.host ?? "relay.invalid"}`);

  if (url.pathname === "/health") {
    json(response, 200, { ok: true, serverMs: Date.now(), rooms: store.size, live: live.size });
    return;
  }

  if (request.method === "POST" && url.pathname === "/rooms") {
    if (rateLimited(clientIp(request))) {
      json(response, 429, { error: "rate_limited" });
      return;
    }
    if (store.size >= MAX_ROOMS) {
      json(response, 503, { error: "at_capacity" });
      return;
    }

    const hostToken = token();
    const now = Date.now();
    // A code that is already taken is refused rather than overwritten, so a collision costs the
    // caller a retry instead of handing someone else's live room away.
    for (let attempt = 0; attempt < CODE_ATTEMPTS; attempt++) {
      const code = generateRoomCode();
      if (store.create(code, hostToken, now) !== null) {
        json(response, 200, { code, hostToken });
        return;
      }
    }
    json(response, 500, { error: "create_failed" });
    return;
  }

  json(response, 404, { error: "not_found" });
});

const sockets = new WebSocketServer({ noServer: true });

server.on("upgrade", (request, socket, head) => {
  const url = new URL(request.url ?? "/", `http://${request.headers.host ?? "relay.invalid"}`);
  const match = url.pathname.match(/^\/rooms\/([^/]+)\/ws$/);

  const refuse = (status: number, reason: string): void => {
    socket.write(`HTTP/1.1 ${status} ${reason}\r\nConnection: close\r\n\r\n`);
    socket.destroy();
  };

  if (match === null) return refuse(404, "Not Found");

  const code = (match[1] ?? "").toUpperCase();
  if (!isValidRoomCode(code)) return refuse(400, `Room codes are ${CODE_LENGTH} characters`);
  if (rateLimited(clientIp(request))) return refuse(429, "Too Many Requests");

  const joinToken = url.searchParams.get("token") ?? "";
  if (joinToken.length < 16) return refuse(401, "Unauthorized");

  const room = roomFor(code);
  if (room === null) return refuse(404, "Not Found");

  sockets.handleUpgrade(request, socket, head, (ws) => {
    const connection = room.join(ws, {
      name: url.searchParams.get("name") ?? "Listener",
      token: joinToken,
      timeZone: url.searchParams.get("tz") ?? "",
      memberId: randomUUID(),
    });

    if (connection === "room_full") {
      ws.close(1013, "room_full");
      return;
    }

    attach(ws, room, connection, code);
  });
});

function attach(ws: WebSocket, room: Room, connection: Connection, code: string): void {
  let alive = true;
  ws.on("pong", () => {
    alive = true;
  });

  const heartbeat = setInterval(() => {
    if (!alive) {
      ws.terminate();
      return;
    }
    alive = false;
    ws.ping();
  }, HEARTBEAT_MS);
  heartbeat.unref?.();

  ws.on("message", (data, isBinary) => {
    room.receive(connection, isBinary ? "" : data.toString());
  });

  const done = (): void => {
    clearInterval(heartbeat);
    room.leave(connection);
    // Nobody left: drop the in-memory room. Its durable half stays in the store, so the code
    // still belongs to whoever created it.
    if (room.isEmpty) live.delete(code);
  };

  ws.on("close", done);
  ws.on("error", done);
}

const sweeper = setInterval(() => {
  const removed = store.sweep(Date.now());
  if (removed > 0) console.log(`swept ${removed} idle rooms`);
}, SWEEP_INTERVAL_MS);
sweeper.unref?.();

server.listen(port, () => {
  const turn = ice.turnUrls.length > 0 ? `${ice.turnUrls.length} TURN url(s)` : "STUN only";
  const adopt = adoptUnknownRooms ? ", adopting unknown codes" : "";
  console.log(
    `auralis relay listening on :${port} (${turn}, data: ${dataDir ?? "memory"}${adopt})`,
  );
});

/** A host that stops a container sends SIGTERM and waits a moment; use it to land the last write. */
function shutdown(signal: string): void {
  console.log(`${signal}: closing`);
  clearInterval(sweeper);
  store.close();
  sockets.close();
  server.close(() => process.exit(0));
  setTimeout(() => process.exit(0), 5_000).unref?.();
}

process.on("SIGTERM", () => shutdown("SIGTERM"));
process.on("SIGINT", () => shutdown("SIGINT"));
