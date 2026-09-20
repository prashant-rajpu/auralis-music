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
`reaction`, `ice`, `bye`. The server injects `senderId` and `serverMs` on every
broadcast; a client cannot claim to be someone else or claim a different time.

### Server messages

`welcome` (with a full room snapshot so a late joiner catches up in one round
trip), `pong`, `presence`, `playback`, `seek`, `queue`, `chat`, `reaction`,
`ice`, `error`.

Call signalling is not in this list on purpose. An offer, an answer and every
ICE candidate ride the encrypted `chat` channel, so the relay never learns that
a call is happening, let alone the addresses inside it.

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
npm run smoke -- <the url it printed>
```

`wrangler login` is interactive, so it has to be you: an agent in a headless
container cannot complete the browser flow, and handing one an API token to
work around that is a worse trade than typing one command.

`wrangler deploy` prints the worker URL. It looks like
`https://auralis-relay.<your-subdomain>.workers.dev` — the subdomain is yours,
so there is no address this repo could have guessed.

Give it to the app in either of two ways:

- **Per build**, so an APK ships ready to use: put
  `auralis.relayUrl=https://…` in `~/.gradle/gradle.properties`, or set the
  `AURALIS_RELAY_URL` environment variable (CI reads it as a secret).
- **Per device**, under Settings → Together → Relay address. This always wins
  over the build value, and it is what makes the relay self-hostable — which
  is what keeps Together working in an F-Droid build.

With neither set, the Together tab says so plainly instead of failing as a
socket error, because a plausible-but-wrong default is worse than none.

Local development: `npm run dev` runs the worker and the Durable Object in
Workers' local runtime at `http://localhost:8787`.

## What is configured, and why

`wrangler.toml` turns on Workers Logs and Traces. Without them a session that
misbehaves on two real phones leaves nothing behind to look at, which is the
difference between a bug report and a diagnosis.

Room codes come from `crypto.getRandomValues`, not `Math.random`. The code is
the only thing keeping a room private, and for a session opened by typing the
code rather than following a link it is also what the chat key is derived
from — so it has to be unguessable, which `Math.random` is not built to be.

`Room` extends `DurableObject` from `cloudflare:workers` rather than merely
implementing the interface, so it inherits the runtime behaviour and `this.ctx`
that the base class provides, and the namespace is typed by the class.

**Not configurable on a `workers.dev` address:** the managed bot challenge in
front of it. Requests that score badly — anything from a datacenter IP, for
instance — get an HTML interstitial that no HTTP client can solve. A phone on
an ordinary network scores fine, and the app now detects the challenge and says
so rather than retrying into it forever. Turning it off needs a custom domain
on a zone you control; a `workers.dev` subdomain has no WAF settings.

## TURN, and why the call needs it

A video call tries to connect the two phones directly. Often it can. When it
cannot — one side behind carrier-grade NAT, or on a network that blocks
peer-to-peer traffic outright — the audio and video have to be relayed through
a TURN server, and *which* TURN address is on offer decides whether the call
connects at all.

The one that matters is `turns:turn.cloudflare.com:443`. That is TURN inside
TLS on the port every HTTPS request already uses, so a network filter cannot
pick it out from ordinary web traffic without breaking the web. Plain
`turn:…:3478/udp` is faster and is tried first; 443 is the fallback that still
works when the network is hostile to VoIP. **This is not a theoretical
concern** — the UAE restricts consumer VoIP, and one half of this app's
intended pair is there.

The relay mints the credentials rather than the app carrying them, so the
long-lived key never ships inside an APK:

```bash
# Create a TURN key at dash.cloudflare.com -> Realtime -> TURN, then:
cd relay
npx wrangler secret put TURN_KEY_ID
npx wrangler secret put TURN_KEY_API_TOKEN
```

A member asks for `ice`; the room mints a credential with a 12-hour TTL, caches
it, and sends back the `iceServers` list. With no key configured it returns
public STUN alone — `stun.cloudflare.com` is free and unlimited — which is a
working call on most home networks and a failed one on the awkward ones. The
app can see which it got and say so rather than just failing.

Cloudflare Realtime TURN is $0.05/GB after a free 1,000 GB a month. A relayed
video call runs around 0.5 GB an hour, and only a call that could not connect
directly is relayed at all, so two people will not reach the free tier.

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

Two suites, and they cover different halves.

```bash
cd relay
npm test                                      # the protocol, no runtime needed
npm run smoke -- https://your-relay.workers.dev   # a real deployment
```

`src/protocol.ts` is deliberately pure — no Workers APIs, no I/O — because it
holds the entire security boundary and is worth testing without a runtime.
`npm test` covers it.

`npm run smoke` covers what a pure test cannot: it creates a room on a live
relay, connects two clients, and checks presence, the injected server clock,
the shared queue, opaque chat, and host handover when the host drops. It also
re-checks the rule that matters against a *running* room rather than a
function — a frame carrying `mediaUrl` is refused, and never reaches the peer.

It needs a URL because it talks to a real deployment, so it is not part of CI.
**Run it after every deploy.**

Still uncovered: hibernation and the alarm-driven 30-day TTL, both of which
take real time to observe. `@cloudflare/vitest-pool-workers` would let those be
tested in `workerd`, but it cannot be installed here — npm fails resolving its
vitest 4 peer with an internal `edgesOut` error.
