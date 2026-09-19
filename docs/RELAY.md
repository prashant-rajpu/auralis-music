# The Together relay

`relay/` is the only server component Auralis has. It is a Cloudflare Worker
with one Durable Object per listening room, around 700 lines of TypeScript.
It is deployed separately from the app and is not part of the Gradle build.

## Why it exists

Two phones cannot agree on a playback position from their own clocks. Phone
clocks drift by seconds, and the old ntfy.sh transport had no shared clock at
all, so drift correction was guesswork with a 1500 ms tolerance.

The relay's entire job is to be a trustworthy source of `serverMs`. Every
message it forwards carries the room's clock, and each phone measures its own
offset from it with a ping/pong exchange. Presence and identity come along for
free, because the socket *is* the identity.

## What it is not

**It never sees a playable stream URL.** Peers exchange a `TrackRef` —
provider, id, title, artist, duration, optional cover art — and each phone
resolves the audio locally through its own `StreamResolverRegistry`. This is
enforced in `src/protocol.ts` with allowlist validation: a message carrying an
unknown field is rejected rather than ignored, so a new field cannot smuggle a
URL in. `test/protocol.test.ts` asserts this for `mediaUrl`, `url`,
`streamUrl`, `src`, `audioUrl` and `href`, and that cover art is https-only.

**It cannot read chat.** Chat, dedications and lyric moments are encrypted on
the client with a key derived from the room code. The relay stores and forwards
ciphertext; the wire format has no plaintext field to put a message in.

A compromised relay can therefore stall a session or see which track ids are
being played. It cannot read anything private, and it cannot make someone's
player fetch a URL of its choosing.

## API

| Route | Purpose |
|---|---|
| `POST /rooms` | Creates a room. Returns `{ code, hostToken }`. |
| `GET /rooms/{code}/ws?token=…&name=…` | Joins it. Upgrades to a WebSocket. |
| `GET /health` | `{ ok, serverMs }`. |

Room codes are 6 characters from `23456789ABCDEFGHJKMNPQRSTUVWXYZ` — no
`0`/`O`, no `1`/`I`/`L`, because codes get read aloud and typed by hand. A code
that is already taken is refused rather than overwritten, so a collision costs
the caller a retry instead of handing someone else's live room away.

There is deliberately no room listing and no lookup by anything but the exact
code. A room is private to whoever was told the code.

### Client messages

`ping`, `playback`, `seek`, `queueAdd`, `queueRemove`, `buffering`, `chat`,
`reaction`, `bye`. The server injects `senderId` and `serverMs` on every
broadcast; a client cannot claim to be someone else or claim a different time.

### Server messages

`welcome` (with a full room snapshot so a late joiner catches up in one round
trip), `pong`, `presence`, `playback`, `seek`, `queue`, `chat`, `reaction`,
`error`.

## Limits

| Limit | Value | Why |
|---|---|---|
| Message size | 4 KB | Checked before parsing. |
| Members per room | 16 | Built for two; sized for a small group. |
| Messages per member | 10/s, bursting to 20 | Continuously refilled token bucket, so a burst on a window boundary cannot get through at double rate. |
| Joins per IP | 30 per minute | Stops room-code mining and join loops. |
| Queue | 200 tracks | |
| Chat history | last 50 | Enough for a rejoin to see context. |
| Idle room TTL | 30 days | Long enough that a couple's code stays theirs between sessions. |

A host that disconnects hands the room over immediately to whoever is still
connected, so playback never freezes. The original host reclaims it by
presenting their `hostToken` when they reconnect.

## Deploying

```bash
cd relay
npm install
npm test          # protocol and validation tests
npm run typecheck
npx wrangler login
npm run deploy
```

`wrangler deploy` prints the worker URL. The app does not read it yet — the
Android side of Together 2.0 is the next piece of work, and it will expose the
URL as a Settings field rather than baking it in, so the relay can be
self-hosted. That is what keeps Together working in an F-Droid build.

Local development: `npm run dev` runs the worker and the Durable Object in
Workers' local runtime at `http://localhost:8787`.

## Cost

Workers' free tier is 100,000 requests a day. A WebSocket connection counts as
one request; the messages over it do not. Durable Objects on the free tier
include 1 GB of SQLite storage and enough request volume that a handful of
couples will not approach it.

WebSocket Hibernation is why an idle room costs nothing: between messages the
Durable Object is evicted from memory while the sockets stay open. All durable
state therefore lives in storage or in the socket attachments, never in
instance fields that would not survive eviction. Duration billing stops during
hibernation, so a session left open overnight is charged for the moments
someone actually did something.

## Testing

```bash
cd relay && npm test
```

`src/protocol.ts` is deliberately pure — no Workers APIs, no I/O — because it
holds the entire security boundary and is worth testing without a runtime.

The Durable Object wiring in `src/room.ts` is **not** covered by automated
tests. `@cloudflare/vitest-pool-workers`, which would run it in the real
`workerd` runtime, could not be installed (npm fails resolving its vitest 4
peer with an internal `edgesOut` error). Until that is fixed, room behaviour —
host handover, presence, hibernation, the alarm-driven TTL — is exercised only
by hand against `npm run dev`. The logic that a malicious peer could reach is
all in `protocol.ts`, which is covered.
