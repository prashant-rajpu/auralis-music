/**
 * End-to-end smoke test against a *deployed* relay.
 *
 * The unit tests in test/ cover protocol.ts, which holds the security rules but touches no Workers
 * API. This covers the half they cannot: the Durable Object itself — presence, the injected server
 * clock, host handover, and whether a hostile frame really is refused by a running room rather than
 * only by a pure function.
 *
 * Needs a URL, because it talks to a real deployment:
 *
 *     npm run smoke -- https://auralis-relay.<subdomain>.workers.dev
 *
 * Not part of CI: CI has no relay to point at. Run it after every deploy.
 */
import WebSocket from "ws";

const RELAY = (process.argv[2] ?? process.env.AURALIS_RELAY_URL ?? "").replace(/\/+$/, "");
if (!RELAY) {
  console.error("usage: npm run smoke -- https://your-relay.workers.dev");
  process.exit(2);
}

/**
 * A browser-ish User-Agent. A workers.dev route sits behind a managed challenge that scores the
 * caller, and a bare script signature from a datacenter is exactly what it scores badly.
 */
const UA =
  "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0 Mobile Safari/537.36";

const results = [];
const check = (name, condition, detail = "") =>
  results.push({ name, ok: Boolean(condition), detail });

const settle = (ms = 1200) => new Promise((resolve) => setTimeout(resolve, ms));
const of = (socket, type) => socket.inbox.filter((m) => m.type === type);

function connect(code, token, name, timeZone) {
  return new Promise((resolve, reject) => {
    const url =
      `${RELAY.replace(/^https/, "wss")}/rooms/${code}/ws` +
      `?token=${encodeURIComponent(token)}&name=${encodeURIComponent(name)}&tz=${encodeURIComponent(timeZone)}`;
    const socket = new WebSocket(url, { headers: { "user-agent": UA } });
    socket.inbox = [];
    socket.on("message", (raw) => socket.inbox.push(JSON.parse(raw.toString())));
    socket.on("open", () => resolve(socket));
    socket.on("error", reject);
    setTimeout(() => reject(new Error(`timed out opening ${url}`)), 15_000);
  });
}

const health = await fetch(`${RELAY}/health`, { headers: { "user-agent": UA } }).then((r) => r.json());
check("the relay is up", health?.ok === true, `serverMs=${health?.serverMs}`);

const room = await fetch(`${RELAY}/rooms`, { method: "POST", headers: { "user-agent": UA } })
  .then((r) => r.json());
check("a room can be created", Boolean(room.code && room.hostToken), `code=${room.code}`);

const host = await connect(room.code, room.hostToken, "Host", "Asia/Kolkata");
const guest = await connect(room.code, "guest-token-0123456789abcd", "Guest", "Europe/London");
await settle();

const hostWelcome = of(host, "welcome")[0];
const guestWelcome = of(guest, "welcome")[0];
check("both sides are welcomed", Boolean(hostWelcome && guestWelcome));
check("the first in hosts the room", hostWelcome?.hostId === hostWelcome?.memberId);
check("the welcome carries a snapshot", Array.isArray(hostWelcome?.state?.members));

const presence = of(guest, "presence").at(-1) ?? guestWelcome?.state;
const members = presence?.members ?? [];
check("both members are listed", members.length === 2, `n=${members.length}`);
check(
  "time zones survive the trip",
  members.some((m) => m.timeZone === "Asia/Kolkata") && members.some((m) => m.timeZone === "Europe/London"),
  members.map((m) => `${m.name}:${m.timeZone}`).join(" "),
);

// The entire reason a relay exists rather than a pub/sub topic.
const sentAt = Date.now();
host.send(JSON.stringify({ type: "ping", at: sentAt }));
await settle(800);
const pong = of(host, "pong").at(-1);
const roundTrip = Date.now() - sentAt;
check(
  "a ping comes back with the room clock",
  pong?.at === sentAt && typeof pong?.serverMs === "number",
  `rtt≈${roundTrip}ms`,
);

host.send(JSON.stringify({
  type: "playback",
  positionMs: 30_000,
  isPlaying: true,
  speed: 1,
  track: { provider: "audius", providerId: "abc", title: "A Song", artist: "An Artist", durationMs: 210_000 },
}));
await settle();
const playback = of(guest, "playback").at(-1);
check("playback reaches the other phone", playback?.positionMs === 30_000);
check("the server stamps who sent it", playback?.senderId === hostWelcome?.memberId);
check("the server stamps when", Math.abs((playback?.serverMs ?? 0) - Date.now()) < 60_000);

// The rule the whole design exists to hold, checked against a running room.
guest.send(JSON.stringify({
  type: "playback",
  positionMs: 0,
  isPlaying: true,
  speed: 1,
  track: {
    provider: "audius", providerId: "x", title: "T", artist: "A", durationMs: 1000,
    mediaUrl: "https://evil.invalid/payload.mp3",
  },
}));
await settle();
const refusal = of(guest, "error").at(-1);
check("a stream URL is refused", /mediaUrl/.test(refusal?.message ?? ""), refusal?.message ?? "NOT REFUSED");
check(
  "and never reaches the peer",
  of(host, "playback").every((m) => !JSON.stringify(m).includes("evil.invalid")),
);

guest.send(JSON.stringify({ type: "chat", ciphertext: "AAECAwQFBgc=" }));
guest.send(JSON.stringify({
  type: "queueAdd",
  track: { provider: "jamendo", providerId: "77", title: "Next", artist: "An Artist", durationMs: 1000 },
}));
await settle();
check("chat arrives as ciphertext", of(host, "chat").at(-1)?.ciphertext === "AAECAwQFBgc=");
check("the shared queue updates", of(host, "queue").at(-1)?.items?.length === 1);

host.close();
await settle(1500);
const afterHostLeft = of(guest, "presence").at(-1);
check("the room hands over when the host leaves", afterHostLeft?.hostId === guestWelcome?.memberId);
check("whoever left is dropped from presence", afterHostLeft?.members?.length === 1);
guest.close();

for (const { name, ok, detail } of results) {
  console.log(`${ok ? "PASS" : "FAIL"} ${name}${detail ? ` — ${detail}` : ""}`);
}
const failed = results.filter((r) => !r.ok).length;
console.log(`\n${results.length - failed} passed, ${failed} failed`);
process.exit(failed ? 1 : 0);
