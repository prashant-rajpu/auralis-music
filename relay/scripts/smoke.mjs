/**
 * End-to-end smoke test against a running relay.
 *
 * The unit tests cover the protocol and the room in isolation. This covers what they cannot: a
 * real process, real sockets, and whether two clients that have never met end up agreeing about
 * the time — over whatever proxy and TLS terminator the host puts in front of it.
 *
 *     npm run smoke -- http://localhost:8787
 *     npm run smoke -- https://your-relay.example.com
 *
 * Run it after every deploy, and against a local `npm start` before you push one.
 */
import WebSocket from "ws";

const RELAY = (process.argv[2] ?? process.env.AURALIS_RELAY_URL ?? "").replace(/\/+$/, "");
if (!RELAY) {
  console.error("usage: npm run smoke -- http://localhost:8787");
  process.exit(2);
}

const UA = "auralis-relay-smoke/2";

const results = [];
const check = (name, condition, detail = "") =>
  results.push({ name, ok: Boolean(condition), detail });

const settle = (ms = 1200) => new Promise((resolve) => setTimeout(resolve, ms));
const of = (socket, type) => socket.inbox.filter((m) => m.type === type);

function connect(code, token, name, timeZone) {
  return new Promise((resolve, reject) => {
    const url =
      `${RELAY.replace(/^http/, "ws")}/rooms/${code}/ws` +
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

// Where a call is told to look for the other phone.
guest.send(JSON.stringify({ type: "ice" }));
await settle();
const ice = of(guest, "ice").at(-1);
const urls = (ice?.iceServers ?? []).flatMap((server) => server.urls ?? []);
check("the relay answers with ice servers", urls.length > 0, urls.join(" "));
check(
  "every one of them is a stun or turn address",
  urls.every((url) => /^stuns?:|^turns?:/.test(url)),
  urls.join(" "),
);
// Only meaningful once TURN is configured; says so rather than failing when it is not.
const relayed = urls.filter((url) => url.startsWith("turn"));
check(
  relayed.length > 0
    ? "TURN is configured, and reachable over TLS on 443"
    : "TURN is not configured (STUN only — a call will fail on a restrictive network)",
  relayed.length === 0 || relayed.some((url) => url.startsWith("turns:") && url.includes(":443")),
  relayed.join(" "),
);

// A rejoin has to see what was said while it was away.
const rejoined = await connect(room.code, "rejoin-token-0123456789ab", "Rejoin", "Asia/Dubai");
await settle();
const replayed = of(rejoined, "welcome")[0]?.state?.chat ?? [];
check("a rejoin is handed the recent history", replayed.length === 1, `n=${replayed.length}`);
rejoined.close();
await settle(600);

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
