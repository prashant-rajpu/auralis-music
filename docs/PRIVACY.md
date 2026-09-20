# What Auralis knows about you

Short version: almost nothing leaves your phone, and the one thing that does —
a Together session — is built so the server carrying it cannot read the private
parts.

This file describes what the code actually does. If you find a place where it
does not match, that is a bug and worth reporting.

## On your phone

Your library, likes, playlists, play history, downloads and settings live in a
local Room database and in `SharedPreferences`. None of it is uploaded, and
there is no account to sign into.

Play history is what personalization will be built on (daily mixes, smart
shuffle, Wrapped). It is computed locally, and Settings will carry a switch to
turn it off and a button to erase it.

Downloaded music is excluded from Android's cloud backup, so your files do not
end up in someone else's data centre by default.

## Talking to music sources

Searching and streaming means talking to whichever source the track came from —
Audius, Jamendo, and on the `plus` edition YouTube Music and JioSaavn. Those
requests go directly from your phone to them, carrying whatever an HTTP request
normally carries. Auralis adds no identifier of its own.

The `play` edition does not contain the scraped-source code at all. That is
checked on the built APK, not just the source tree, by
`scripts/verify-play-flavor.sh`.

## Together sessions

A Together session goes through a relay — a small Cloudflare Worker, documented
in [`RELAY.md`](RELAY.md). It exists for one reason: two phones cannot agree on
a playback position from their own clocks, so something has to hold the shared
one.

**The relay never sees a playable stream URL.** Peers exchange a track
reference — provider, id, title, artist, duration, cover art — and each phone
resolves its own audio locally. This is enforced on both sides: the relay
rejects any message carrying an unknown field, and the app's `TrackRef` has no
field a URL could occupy.

**The relay cannot read what you say.** Chat, dedications, lyric moments and the
notes attached to them are encrypted on your phone with AES-256-GCM before they
are sent. The relay stores and forwards ciphertext. The wire format has no
plaintext field to put a message in.

**Playback state is not encrypted**, deliberately. The relay needs the track id
and the timestamp to do its job, and a track id is not a private message. If
you are in a session, assume the relay operator could see what you are playing
and when.

### How strong that encryption is depends on how you shared the invite

This is the part most apps do not tell you, so here it is plainly.

The key is derived from a secret carried in the invite. When you share a
**link or a QR code**, that invite carries a 20-character secret that is never
sent to the relay, and the encryption is as strong as it sounds.

When someone **reads you the six-character room code** instead, there is no
room in six characters for that secret, so the key comes from the code itself.
Six characters is about thirty bits. Nobody is going to guess it, but whoever
holds the ciphertext could try every possibility offline. So a code-only
session is private from other users, and not private from a determined relay
operator.

The code tracks which of the two is in force (`EncryptionStrength`), and the
Together screen will show it rather than implying they are the same — that
screen is not built yet. If it matters to you, share the link.

### What the relay stores

A room holds its members, the current playback state, the queue, and the last
50 chat messages — the chat as ciphertext. It is addressed only by its exact
code: there is no room listing and no way to look one up by anything else.

A room that nobody touches for 30 days deletes itself. The TTL is that long on
purpose, so a couple's code stays theirs between sessions.

### Chat is also kept on your phone

Messages are written to a database in the app's own storage, in plaintext,
because that is your phone and the point of chat is that it is still there
tomorrow. Nothing syncs it anywhere. Uninstalling the app deletes it.

The encryption above is about the relay, not about your device. Anyone who can
unlock your phone can read the conversation, exactly as they could read your
messaging app.

## Calls

A call is peer-to-peer WebRTC. The audio and video go directly between the two
phones whenever the networks allow it, encrypted end to end by DTLS-SRTP, which
WebRTC does by default and cannot be turned off.

**The relay never learns that a call is happening.** The whole handshake — the
invite, the session descriptions, every candidate address — rides the same
encrypted chat channel as everything else. To the relay it is another message
it cannot read. A candidate list is a list of the addresses your phone can be
reached at, which is the one genuinely sensitive thing a call produces, and it
is not sent in the clear.

**When the two networks will not allow a direct connection**, the media is
relayed through Cloudflare's TURN service instead. A TURN server forwards
encrypted packets; it does not hold the keys and cannot decrypt the call. It
does see that two endpoints are talking, and how much.

The credentials for it are minted by the relay and expire, so the app never
carries a long-lived key. See [`RELAY.md`](RELAY.md).

**The camera and microphone are asked for when you answer a call**, not at
install, and only for what that call needs — an audio call never asks for the
camera. While a call runs, a notification says so, because that is what keeps
Android from taking the microphone back when the screen goes off.

Nothing about a call is recorded. There is no code to record one.

### Running your own

The relay address is a setting. `wrangler deploy` from the `relay/` folder
gives you your own, and the app will talk to it instead. That is also what
keeps Together working in an F-Droid build.

## What Auralis does not do

No analytics. No crash reporting. No advertising identifiers. No account. No
telemetry of any kind. There is no code in the app that sends usage data
anywhere, because none was ever written.
