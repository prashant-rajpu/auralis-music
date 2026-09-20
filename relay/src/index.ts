import { CODE_LENGTH, generateRoomCode, isValidRoomCode } from "./protocol";
import { Room } from "./room";

export { Room };

export interface Env {
  ROOMS: DurableObjectNamespace<Room>;
}

/** Per-IP join budget, so one host cannot mint rooms or hammer join in a loop. */
const JOIN_WINDOW_MS = 60_000;
const JOIN_MAX_PER_WINDOW = 30;
const joinAttempts = new Map<string, { count: number; windowStartMs: number }>();

/** A busy isolate would otherwise hold an entry per IP for as long as it lives. */
function sweep(now: number): void {
  if (joinAttempts.size < 4096) return;
  for (const [ip, entry] of joinAttempts) {
    if (now - entry.windowStartMs > JOIN_WINDOW_MS) joinAttempts.delete(ip);
  }
}

function rateLimited(ip: string): boolean {
  const now = Date.now();
  sweep(now);
  const entry = joinAttempts.get(ip);
  if (entry === undefined || now - entry.windowStartMs > JOIN_WINDOW_MS) {
    joinAttempts.set(ip, { count: 1, windowStartMs: now });
    return false;
  }
  entry.count += 1;
  return entry.count > JOIN_MAX_PER_WINDOW;
}

/** Enough that exhausting them means the code space is genuinely full, not that we were unlucky. */
const CODE_ATTEMPTS = 8;

function clientIp(request: Request): string {
  return request.headers.get("CF-Connecting-IP") ?? "unknown";
}

function token(): string {
  const bytes = new Uint8Array(24);
  crypto.getRandomValues(bytes);
  return Array.from(bytes, (b) => b.toString(16).padStart(2, "0")).join("");
}

/**
 * Two routes, and nothing else:
 *
 *   POST /rooms              -> { code, hostToken }
 *   GET  /rooms/{code}/ws    -> the session socket
 *
 * The Durable Object is addressed by room code, so every member of a room lands on the same
 * instance and therefore the same clock. Deliberately no room listing and no lookup by anything
 * but the exact code: a room is private to whoever was told the code.
 */
export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);

    if (url.pathname === "/health") {
      return Response.json({ ok: true, serverMs: Date.now() });
    }

    if (request.method === "POST" && url.pathname === "/rooms") {
      if (rateLimited(clientIp(request))) {
        return Response.json({ error: "rate_limited" }, { status: 429 });
      }

      const hostToken = token();
      // A room that already exists refuses to be re-created, so a code collision costs a retry
      // rather than silently handing someone else's live room to this caller.
      for (let attempt = 0; attempt < CODE_ATTEMPTS; attempt++) {
        const code = generateRoomCode();
        const room = env.ROOMS.getByName(code);
        const created = await room.fetch(
          new Request("https://relay.invalid/create", {
            method: "POST",
            body: JSON.stringify({ hostToken }),
          }),
        );
        if (created.ok) return Response.json({ code, hostToken });
        if (created.status !== 409) break;
      }

      return Response.json({ error: "create_failed" }, { status: 500 });
    }

    const match = url.pathname.match(/^\/rooms\/([^/]+)\/ws$/);
    if (match !== null) {
      const code = (match[1] ?? "").toUpperCase();
      if (!isValidRoomCode(code)) {
        return Response.json(
          { error: "bad_code", message: `Room codes are ${CODE_LENGTH} characters` },
          { status: 400 },
        );
      }
      if (rateLimited(clientIp(request))) {
        return Response.json({ error: "rate_limited" }, { status: 429 });
      }

      const room = env.ROOMS.getByName(code);
      const forwarded = new URL(request.url);
      forwarded.pathname = "/ws";
      return room.fetch(new Request(forwarded.toString(), request));
    }

    return new Response("Not found", { status: 404 });
  },
};
