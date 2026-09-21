# The Together relay

`relay/` is the only server component Auralis has: a plain Node WebSocket
server, around 800 lines of TypeScript, deployed separately from the app and
not part of the Gradle build.

It ran on Cloudflare Workers and Durable Objects first. That was elegant and it
tied the whole feature to one company's account, so it now runs anywhere that
can run a container — Fly, Render, a VPS, a Raspberry Pi. The wire format did
not change when it moved, and neither did a line of the app.

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
| Message size | 16 KB | Enforced twice: by the socket layer, so an oversized frame is refused before it is buffered, and again by the parser. A cap that only the parser knows about is no cap at all — it can measure a message only once the whole thing is in memory. |
| Members per room | 16 | Built for two; sized for a small group. |
| Messages per member | 10/s, bursting to 20 | Continuously refilled token bucket, so a burst on a window boundary cannot get through at double rate. |
| Joins per IP | 30 per minute | Stops room-code mining and join loops. The address comes from `X-Forwarded-For` counted from the *right*, so a forged prefix cannot borrow someone else's budget — see `TRUSTED_PROXY_HOPS`. |
| Buffered per socket | 1 MB | A phone that stops reading does not get to grow the server's write queue until the process dies. |
| Queue | 200 tracks | |
| Chat history | last 50 | Enough for a rejoin to see context. |
| Idle room TTL | 30 days | Long enough that a couple's code stays theirs between sessions. |

A host that disconnects hands the room over immediately to whoever is still
connected, so playback never freezes. The original host reclaims it by
presenting their `hostToken` when they reconnect.

## Running it

```bash
cd relay
npm install
npm test            # protocol, room, store and ICE — 52 tests, no server needed
npm run typecheck
npm run build
DATA_DIR=./data npm start
npm run smoke -- http://localhost:8787   # 21 end-to-end checks against that process
```

Do that before every deploy. The smoke test talks to a real process over real
sockets and catches what unit tests cannot — including whatever proxy and TLS
terminator your host puts in front of it, once you point it at the deployed
address instead.

## Deploying

The relay is one container with one port and one optional volume. Every option
below is the same image; pick on cost and whether it is allowed to fall asleep.

### Fly.io — the recommendation

Closest thing to "it just works", and it can sit in Mumbai, which is the
nearest region to both of you. `fly.toml` is committed.

```bash
cd relay
fly launch --no-deploy --copy-config --name auralis-relay
fly volumes create relay_data --region bom --size 1   # keeps room codes across deploys
fly deploy
```

Roughly $2–3 a month for a machine that never sleeps. `auto_stop_machines` is
deliberately off: a relay that is asleep when she opens the app is a relay that
is not there, and a cold start on the one path that has to feel instant is the
wrong trade.

### Render — free, with two things to know

`render.yaml` is committed. Point Render at the repo and it builds the
Dockerfile. No card, no cost. Two properties of the free plan shape how this is
set up.

**It sleeps after 15 minutes with no traffic**, and the next request then waits
about a minute while it comes back. `.github/workflows/relay-keepalive.yml`
pings `/health` every ten minutes so that never happens. Set
`AURALIS_RELAY_URL` as a repository secret and it starts working; without the
secret it does nothing rather than failing every run.

That workflow is also the only monitoring this project has: when `/health` does
not answer, the job fails and GitHub emails you. A relay that is down at 11pm
her time is the failure that actually matters, and nothing else would tell you.

Two caveats, both GitHub's. Scheduled runs are best-effort and often minutes
late — ten minutes against a fifteen-minute timer leaves room for that, but not
much. And GitHub disables scheduled workflows in a repository with no activity
for 60 days. If that is a risk, point a free uptime monitor at `/health`
instead: UptimeRobot checks every five minutes, cron-job.org every one, and
neither cares how long since you last pushed.

Note Render's account-wide budget of **750 instance-hours a month**. One service
kept awake around the clock uses roughly 730 of them, so this can be the only
free service in the account.

**It has no persistent disk**, which is why `DATA_DIR` is empty there: rooms
live in memory and are forgotten on every deploy. On its own that would mean
the room you were both using last night comes back "not found" because of a
restart neither of you did — so the relay adopts a code it does not recognise
when it has no disk (`ADOPT_UNKNOWN_ROOMS`, on by default in that case). The
first person in hosts it, and the two of you keep your code.

With a disk it stays off: there, a missing room really is missing — expired
after thirty days, or mistyped — and saying so beats quietly opening an empty
room to sit in.

### Your own server

Any VPS, or a machine at home. `docker-compose.yml` is committed; put Caddy or
nginx in front for TLS.

```bash
cd relay
docker compose up -d
```

Oracle Cloud's Always Free tier gives an ARM VM permanently at no cost, which
is the only genuinely free option here that never sleeps. It costs you an
afternoon of setup and a domain name.

### Anything else

`docker build -t auralis-relay .` and run it. Railway, Koyeb, Hetzner, Google
Cloud Run, AWS App Runner, Azure Container Apps — the relay needs a container,
one TCP port, and a host that does not kill idle WebSockets. Cloud Run is
serverless and will scale to zero, so it has the same cold-start caveat as
Render's free plan.

## Telling the app where it is

Whatever you deploy to prints or shows a URL. Give it to the app in either of
two ways:

- **Per build**, so an APK ships ready to use: put
  `auralis.relayUrl=https://…` in `~/.gradle/gradle.properties`, or set the
  `AURALIS_RELAY_URL` environment variable (CI reads it as a secret).
- **Per device**, under Settings → Together → Relay address. This always wins
  over the build value, and it is what makes the relay self-hostable — which
  is what keeps Together working in an F-Droid build.

With neither set, the Together tab says so plainly instead of failing as a
socket error, because a plausible-but-wrong default is worse than none.

## What is configured, and why

Room codes come from `crypto.getRandomValues`, not `Math.random`. The code is
the only thing keeping a room private, and for a session opened by typing the
code rather than following a link it is also what the chat key is derived
from — so it has to be unguessable, which `Math.random` is not built to be.

`Room` is a plain object over a `Sink` — anything with `send` and `close` —
rather than being tied to a particular socket library. That is the reason the
room's own behaviour is unit-tested at all: presence, host handover and what a
hostile frame does to a *running* room used to need a deployment to exercise.

Rooms are kept in memory and written to one JSON file, debounced and atomically
(a temporary file renamed over the real one, so a process killed mid-write
leaves the previous version rather than half of the new one). A database would
be the obvious reach and the wrong one: the entire state is a handful of rooms
holding at most fifty short messages each.

Set `DATA_DIR` to an empty string to keep everything in memory. That is the
right setting on a host with no persistent disk, where a file would only give
the false impression of durability.

## TURN, and why the call needs it

A video call tries to connect the two phones directly. Often it can. When it
cannot — one side behind carrier-grade NAT, or on a network that blocks
peer-to-peer traffic outright — the audio and video have to be relayed through
a TURN server, and *which* TURN address is on offer decides whether the call
connects at all.

The one that matters is `turns:<host>:443`. That is TURN inside TLS on the port
every HTTPS request already uses, so a network filter cannot pick it out from
ordinary web traffic without breaking the web. Plain `turn:…:3478/udp` is
faster and is tried first; 443 is the fallback that still works when the
network is hostile to calls. **This is not a theoretical concern** — the UAE
restricts consumer VoIP, and one half of this app's intended pair is there.

The relay hands the credentials out rather than the app carrying them, so
nothing long-lived ever ships inside an APK. A member sends `ice`; the room
answers with the server list, cached for the room and re-derived before it
expires. With nothing configured it returns public STUN alone — a working call
on most home networks and a failed one on the awkward ones. The app can see
which it got.

### Running your own (coturn)

The best option if you already have a server: no account, no quota, no third
party in the media path.

```bash
# /etc/turnserver.conf
listening-port=3478
tls-listening-port=443
fingerprint
use-auth-secret
static-auth-secret=<a long random string>
realm=turn.example.com
cert=/etc/letsencrypt/live/turn.example.com/fullchain.pem
pkey=/etc/letsencrypt/live/turn.example.com/privkey.pem
no-multicast-peers
```

Then give the relay the same secret:

```bash
TURN_URLS=turn:turn.example.com:3478?transport=udp,turn:turn.example.com:80?transport=tcp,turns:turn.example.com:443?transport=tcp
TURN_SECRET=<the same static-auth-secret>
```

`use-auth-secret` is coturn's REST scheme: the relay derives a username that
carries its own expiry and a password that is an HMAC of it. Nothing is stored
and nothing has to be revoked — a credential simply stops working.

Note that TLS on 443 wants a port to itself. If the relay is on the same
machine, put them on different addresses, or give coturn the box and host the
relay elsewhere.

### A hosted TURN provider

Any of them work; the relay only needs URLs and a credential.

```bash
TURN_URLS=turns:turn.provider.example:443?transport=tcp
TURN_USERNAME=<from the provider>
TURN_CREDENTIAL=<from the provider>
```

**Free, and the quickest way to find out whether TURN fixes a call:** Metered
runs a public Open Relay whose credentials are published rather than issued,
so there is nothing to sign up for.

```bash
TURN_URLS=turn:openrelay.metered.ca:80,turn:openrelay.metered.ca:443,turns:openrelay.metered.ca:443?transport=tcp
TURN_USERNAME=openrelayproject
TURN_CREDENTIAL=openrelayproject
```

It is a shared service with no promises attached: it can be slow, and it can go
away. Treat it as the thing that proves a call *can* be relayed, then decide
whether to keep it. A free Metered account gives you your own credentials and a
monthly allowance; Twilio's Network Traversal Service is the paid, dependable
end; coturn on a free Oracle Cloud VM is the only option that is both free
forever and yours.

**Do not trust any of them without checking.** A dead TURN address is worse
than none, because every one of them is a connection attempt the call waits on
before giving up:

```bash
npm run build
npm run check:turn                          # from TURN_URLS in the environment
npm run check:turn -- https://your-relay    # or from whatever the relay hands out
```

That speaks real STUN and TURN — a Binding request, then a full Allocate with
long-term credential authentication — and tells you, per address, whether a
relay address actually came back. It exits non-zero unless TURN works over TLS
on 443, because that is the address that matters here.

Run it from the network you are worried about. Passing on your home wifi says
the credentials are good; it says nothing about a network that blocks calls.

## Cost

The relay itself is one small container: a couple of dollars a month on Fly,
nothing on a Render free plan that sleeps, nothing on a server you already own.
Two people generate a few kilobytes a second while a session is open and
nothing at all the rest of the time.

TURN is the part with real bandwidth attached, and only for calls that could
not connect directly. A relayed video call is around 0.5 GB an hour. Self-hosted
that is just your server's egress; on a hosted provider it is whatever they
charge per gigabyte.

## Testing

Three layers, and they cover different things.

```bash
cd relay
npm test                                  # 63 unit tests, no server needed
npm run build && DATA_DIR="" npm start    # in one terminal
npm run smoke -- http://localhost:8787    # 22 end-to-end checks, in another
npm run check:turn -- http://localhost:8787   # and whether calls have a way through
```

`src/protocol.ts` is deliberately pure — no I/O, no framework — because it
holds the entire security boundary. `src/room.ts` talks to a `Sink` rather than
a socket, so presence, host handover, rate limiting and the chat history cap
are unit-tested too; that half used to need a deployment to exercise at all.

`npm run smoke` covers what a unit test cannot: a real process, real sockets,
and whatever proxy sits in front of it. It creates a room, connects two
clients, and checks presence, the injected server clock, the shared queue,
opaque chat, the ICE list, history on rejoin, and host handover when the host
drops. It also re-checks the rule that matters against a *running* room — a
frame carrying `mediaUrl` is refused, and never reaches the peer.

`src/stun.ts` is checked against the official test vectors in RFC 5769, which
exist because these are the bytes everyone gets wrong. That is not ceremony: a
bad MESSAGE-INTEGRITY comes back as `401 Unauthorized`, indistinguishable from
a bad password, so a TURN check built on an unverified codec would confidently
report the wrong cause. Those vectors caught two real bugs when they were first
run here.

CI runs the unit tests and the smoke test: it builds the relay, starts it, and
points the smoke test at the running process. Run the same smoke test against
the deployed address after every deploy — that is the only way to catch a host
that mangles WebSockets — and `check:turn` whenever the TURN configuration
changes.
