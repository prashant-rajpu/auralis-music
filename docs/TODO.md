# What is left

Everything in v4.0 and v4.1 is merged, plus persistent chat, the call, and the
relay's move off Cloudflare. This is the ordered list of what remains, written
so any item can be picked up cold — including by someone who is not me.

Companion docs: [`HANDOVER.md`](HANDOVER.md) for what exists and how it was
verified, [`RELAY.md`](RELAY.md) for the server, [`CODESPACES.md`](CODESPACES.md)
for building it in the cloud, [`PRIVACY.md`](PRIVACY.md) for what the app knows
about you.

**Where it stands.** 345 unit tests on `play`, 63 relay unit tests, 22
end-to-end relay checks, lint, R8, and APK-level proof that `play` carries no
scraped-source code — all green. Nothing has run on two phones. That is the
sentence that matters, and §0 and §1 are about it.

---

## 0. Blocked on you

Small, but nothing else about Together can be trusted until they are done.

- [ ] **Deploy the relay to Render.** It is a container now, not a Worker.
      Point Render at this repo; `render.yaml` is committed and it builds the
      `Dockerfile` on the free plan. Then:

      ```bash
      cd relay && npm run build
      npm run smoke -- https://<your relay>.onrender.com
      ```
- [ ] **Set `AURALIS_RELAY_URL` as a repository secret.** That switches on
      `.github/workflows/relay-keepalive.yml`, which pings `/health` every ten
      minutes so Render never sleeps the service — and emails you when the
      relay is down, which is the only monitoring this project has.

      Two caveats: GitHub's scheduled runs are often minutes late, and GitHub
      disables them in a repo with no activity for 60 days. If either worries
      you, point UptimeRobot or cron-job.org at `/health` instead.
- [ ] **Delete the two Cloudflare relays** and the empty `auralis-music` Pages
      project, so there is one address and no confusion later.
- [ ] **Put the URL somewhere permanent** — `auralis.relayUrl` in
      `~/.gradle/gradle.properties` for local builds, and an `AURALIS_RELAY_URL`
      secret for CI. There is deliberately no default in the repo.
- [ ] **Run the two-phone test** and keep the numbers: the Together tab shows
      live drift in ms and the round trip. Those two are what turn the sync
      thresholds from reasoned guesses into measurements.
- [ ] **Set up TURN, then prove it works.** Without it a call has STUN only,
      which works on most home networks and fails on the awkward ones — and one
      of the two phones this is for is on a network that restricts consumer
      VoIP, which is the awkward case.

      Start free: Metered's public Open Relay needs no account, and the values
      are commented into `relay/render.yaml` ready to uncomment.

      Then check it, because a dead TURN address is worse than none:

      ```bash
      cd relay && npm run build
      npm run check:turn -- https://<your relay>.onrender.com
      ```

      It speaks real STUN and TURN and exits non-zero unless a relay address
      comes back over TLS on 443. Run it from her network too, not just yours —
      that is the one the answer depends on.

---

## 1. Nothing here has run on a phone but you

Worth stating plainly before anything below gets planned on top of it.

Machine-checked: compilation, 341 unit tests on `play`, 63 relay unit tests, 22
end-to-end checks against a running relay, lint, R8, and APK-level proof that
`play` contains no scraped-source code. The relay's end-to-end checks now run in
CI, because the relay is an ordinary process that CI can start.

**Not checked by anyone but you:** whether it feels right, whether contrast holds
on real album art, whether playback resumes correctly after a force-stop, and
whether two phones actually *sound* together. The last one depends on decode
latency and Bluetooth output delay (150–300 ms, device-specific) and cannot be
measured from a container.

**The call has never run.** Every part of it that can be tested without a camera
is — the state machine, the signalling round trip, candidate holding, the ICE
servers it is given, the microphone being held for the length of the call. What
no test here can tell you is whether a real peer connection forms between a
phone in the UAE and a phone in India, which is the only question that matters
about it. Test that before the birthday, not on it.

The relay's own gaps closed with the move off Durable Objects: the room, the
30-day TTL sweep and the on-disk store are all unit-tested now, because none of
them needs a runtime to exercise any more.

---

## 2. The structural debt: the player still lives in the UI process

**This is the largest single piece of remaining work, and it was deferred on
purpose** — see `HANDOVER.md` §6. It is also the thing most likely to cause the
next crash.

`PlaybackManager` owns two `ExoPlayer`s in the UI process. A force-stop loses the
player; `PlaybackStatePersister` papers over that by restoring the queue and
position, but the player itself is still in the wrong place. Its methods also
touch ExoPlayer with no thread hop, which is what made `TogetherSession` crash
until the adapter started posting to the main thread — the same trap is waiting
for the next background caller.

Split it into named components under `playback/`:

| File | Responsibility |
|---|---|
| `PlaybackService` (replaces `AuralisMediaSessionService`) | Owns the single `ExoPlayer`, hosts a `MediaLibrarySession`, handles `onPlaybackResumption` |
| `PlayerFactory` | `ExoPlayer.Builder` + the existing `ResolvingDataSource.Factory`, audio attributes, becoming-noisy, skip-silence |
| `PlayerConnection` (`@Singleton`) | The UI's only handle: `MediaController` + `StateFlow`s. Position as a cold `Flow` polled at 250 ms **only while collected** |
| `AutoplayController` | Appends via `MusicSource.related()` when ≤2 remain — replaces the 5-second polling loop |
| `TransitionFader` | Fade at track boundaries and on pause/resume via `createMessage().setPosition()` |

The dual-`ExoPlayer` crossfade goes; gapless + a 0–12 s fade + skip-silence
replaces it. True overlap needs `CrossfadePlayer : SimpleBasePlayer` (~800 lines)
and belongs in a backlog, not here. The pure `QueueState`/`QueueOps` in
`playback/queue/` are already the tested source of truth and just get a new owner.

**Land this alone, in its own release**, and run the notification / lockscreen /
Bluetooth / Android Auto checklist on a real device before tagging.

---

## 3. Together: finish and clean up

### Delete the old path (once the relay is proven on two phones)

- [ ] Remove `network/JamWebSocketClient.kt` and `network/JamProtocolHelper.kt`.
      `cleanSearchQuery` already moved to `core/util/SearchQueryNormalizer.kt`;
      check what else `JamProtocolHelper` still holds before deleting.
- [ ] Remove `PlaybackManager`'s `jamClient` dependency and its
      `broadcastPlaybackState` calls.
- [ ] Remove `TogetherModeBottomSheet` from `HomeScreen` and `FullPlayerScreen`.
      Two Together entry points in one app is the current wart; the tab is the
      real one.

### Still to build

- [ ] **Scheduled sessions** — "listen at 9pm your time / 11:30pm theirs".
      Both phones get a notification; tapping either opens the room. Needs
      `androidx.work`. The timezone maths is already there in `PartnerClock`.
- [ ] **Dedications inbox** — the data is being written (`jam_events`, type
      `dedication`); there is no screen for it. `CoupleRepository.dedications()`
      already returns the flow.
- [ ] **Couple profile** — names, avatar emoji and colour, optional anniversary,
      and a shared accent both apps adopt during a session.
- [ ] **Editable name and reaction packs** — ship Couple (default), Neutral,
      Party. The romantic copy becomes data rather than hardcoded strings.
- [ ] **Skip voting** when both are present.
- [ ] **Lyric moments are unreachable** — `TogetherSession.sendLyricMoment` works
      and is tested, but nothing calls it. It needs the full-screen lyrics view
      from §4 to have something to long-press.

### The call: what is built, and what it still cannot do

Built: `WebRtcEngine` (peer connection, mic, camera), `CallSession` (the glue,
on the Together session's own thread), `CallService` (so a call survives the
screen going off), `CallOverlay` (ringing, live call, controls), TURN credentials
minted by the relay, and permissions asked for at the moment someone answers.

- [x] ~~**A voice call crashed the app on Android 14+.**~~ The foreground
      service claimed the camera service type unconditionally, and the voice
      button only ever asks for `RECORD_AUDIO` — so `startForeground` threw a
      `SecurityException` the moment a voice call connected. It now claims only
      the types whose permission has actually been granted, and a refused start
      stops the service instead of the process. `CallForegroundTypesTest`.
- [ ] **The camera toggle does nothing in a voice call.** Turning the camera on
      mid-call needs `CAMERA`, which an audio call never requested, so
      `openCamera` fails quietly and the button looks broken. Ask for the
      permission at that tap, the way answering does — and if it is refused,
      say so rather than leaving a dead control.
- [ ] **A call cannot ring a phone that is not in a session.** The invite rides
      the room's WebSocket, and that socket is gone once the app is closed.
      Calling someone who is not already in the room does nothing. This needs a
      push channel — either FCM, which means a Google dependency in `play` and
      a `plus` build that cannot use it, or a self-hosted `ntfy` topic per
      couple, which the repo already knows how to talk to. Decide before
      promising anyone they can be called.
- [ ] **Echo on speakerphone.** The music plays on the media stream and the call
      records on the voice stream, so the platform's echo canceller has nothing
      to subtract — your microphone picks up the song and sends it back. Fine on
      headphones, which the session screen now says. A real fix means routing
      the music through WebRTC's own output, which is a much bigger change.
- [ ] **Switching the camera on mid-call adds a track after the offer.** It
      works when the call started with video. Starting audio-only and then
      turning the camera on needs a renegotiation the controller does not do
      yet; today the track is added but the other side may not see it until the
      call is re-placed.
- [ ] **The APK is now ~49 MB**, up from ~13. WebRTC ships native libraries for
      four ABIs. An ABI split or an App Bundle would put a phone back at ~20 MB;
      one APK for both of you is simpler for now.
- [ ] **No call history.** A call is not written to `jam_events`, so it does not
      appear in session memories. Cheap to add once the rest is proven.

---

## 4. v4.2 — the showpiece player

`FullPlayerScreen.kt` is 1528 lines; dismantle into `feature/player/`.

- [ ] **Artwork-driven colour** — `androidx.palette`, vibrant swatch, then a
      **contrast guard**: desaturate and lighten/darken until text hits 4.5:1.
      Arbitrary album art includes near-white and near-black covers; without the
      guard roughly one album in twenty is unreadable. Modes: off / player-only
      (default) / whole-app.
- [ ] **Waveform scrubber**, hand-built on `Canvas`. Material3 is **1.4.0** here:
      it has `MotionScheme`, `MaterialExpressiveTheme`, `SwipeToDismissBox` and
      `carousel`, but **not** `WavyProgressIndicator` or `MaterialShapes` — those
      are 1.5.0-alpha. Building it avoids an alpha dependency and gets real
      amplitude: a `WaveformExtractor` decodes local and downloaded files with
      `MediaExtractor`/`MediaCodec` into ~200 RMS buckets cached in a `waveforms`
      table. Streamed tracks get a procedural wave, since amplitude needs the file.
- [ ] **Swipe the artwork** to change track (`HorizontalPager` over the queue),
      drag-to-reorder queue, shared-element transition from row to player,
      double-tap edges to seek ±10 s, predictive back.
- [ ] **Full-screen lyrics** — large type, active line lit, tap to seek, per-track
      offset for badly timed LRC, and the lyric-card share that doubles as the
      Together lyric moment.
- [ ] `MotionScheme.expressive()` app-wide via `MaterialExpressiveTheme`; a
      bundled variable font.

**Verify:** Macrobenchmark `FrameTimingMetric` holds 60 fps on drag and swipe; an
automated contrast test over a fixture set of extreme covers asserts ≥4.5:1.

---

## 5. v4.3 — personalization

All local; nothing leaves the device.

- [ ] `PlayEventRecorder` on `onMediaItemTransition`.
- [ ] `AffinityScorer` (pure, unit-tested):
      `trackScore = Σ w(event) · 0.5^(ageDays/14)` — full play 1.0, partial ≥0.5 →
      0.5, skip −0.7, like +3, queued +0.8, dislike excluded.
- [ ] `CooccurrenceStore` from session adjacency; `DailyMixWorker` clusters into
      3 mixes of ~25 nightly.
- [ ] Smart shuffle with artist spacing ≥3; moods that learn from what is actually
      played under them.
- [ ] **Wrapped**, and **Wrapped For Two** off `shared_plays` — most-played-together
      song, the month you listened most, the track only one of you likes.
- [ ] Settings: a Personalization toggle and Clear History.

---

## 6. v4.4 — customization

- [ ] `SettingsScreen.kt` (862 lines) becomes `feature/settings/` with **search
      across every setting**.
- [ ] **Every setting either works or is deleted.** A `SettingsAuditTest` asserts
      each declared key is read outside its own preferences class, so a dead
      setting fails CI instead of shipping.
- [ ] Genuinely wired: crossfade/fade 0–12 s, gapless, skip silence with
      threshold, volume normalization, mono, pause-on-disconnect, speed with
      independent pitch.
- [ ] A real **equalizer UI** over the existing `AudioEffectManager` — per-band
      sliders, presets, save-your-own, bass boost, virtualizer, loudness.
- [ ] Home layout editor, navigation editor, player layout presets, startup
      screen, per-section sort, row swipe actions, grid density, keep-screen-on.
- [ ] **Backup/restore** of the whole library — playlists, likes, history,
      Together memories, settings — as versioned JSON via SAF, plus M3U8 export
      with `#AURALIS:provider:id`.

---

## 7. v4.5 — platform surfaces

- [ ] Glance widgets: 2×2 / 4×1 / 4×2 now-playing, a Daily Mixes shortcut, and a
      **Together widget showing whether your partner is listening**.
- [ ] Android Auto browse tree, verified on DHU.
- [ ] Quick Settings tile.
- [ ] Folder browsing with excludable folders and a min-duration filter.
- [ ] Tag editor for local files (`jaudiotagger`).
- [ ] Sleep timer upgrades: end-of-track, end-of-queue, shake-to-extend.
- [ ] **Audio visualizer — `plus` only, opt-in.** `android.media.audiofx.Visualizer`
      needs `RECORD_AUDIO`, which on a music player triggers a Play
      privacy-policy requirement and alarms users, for a decorative feature. Keep
      it out of `play` entirely, and add an assertion to
      `scripts/verify-play-flavor.sh` that `RECORD_AUDIO` never reaches the `play`
      manifest.

---

## 8. Quality and infrastructure

- [ ] **A drift soak test on real hardware.** `SyncControllerTest` runs an hour of
      simulated ticks; nothing has run an hour of real ones. Log RTT, offset,
      drift samples and corrections, and compare against the table in
      `SyncConfig`.
- [ ] **Macrobenchmark** for the player once v4.2 lands.
- [ ] **A second relay instance, once there is a reason for one.** The rooms are
      held in a single process, so scaling out would need them shared — sticky
      routing by room code is the cheap answer, a shared store the thorough one.
      Two people do not need either; write it down rather than build it.

---

## The order I would actually go in

Before the birthday, in this order, and stop when it works:

1. **§0** — deploy the relay, set the keep-awake secret, turn on TURN, prove it
   with `check:turn`. An afternoon at most, and nothing else can be trusted
   until it is done.
2. **§1 the two-phone test.** Listen together for twenty minutes, write down the
   drift and round-trip numbers the Together tab shows. Then a call, on both
   networks, audio first and video second. This is the only test that decides
   whether the gift works.
3. **Whatever that test breaks.** Expect something. Keep the logcat.

After the birthday, when it is a project again rather than a deadline:

4. **§3 delete the old path** — assuming the relay held up. Two Together entry
   points is confusing and the ntfy code is dead weight.
5. **§2 the service rework** — alone, in its own release, tested on a device. It
   is the biggest correctness risk left in the app.
6. **§4 the showpiece player** — the first release since v4.0 that is purely
   about how the app feels, and the one that unblocks lyric moments.
7. Then §5, §6, §7 in order; §8 alongside whatever is in flight.

## If you are handing this to someone else

Read in this order: [`HANDOVER.md`](HANDOVER.md) for what exists,
[`AGENTS.md`](../AGENTS.md) for the six things that will bite you, then §1 here
for what has and has not been verified. The build is one command and CI runs it
on every push; [`CODESPACES.md`](CODESPACES.md) gets you an environment without
installing anything.

The two rules that are not negotiable, because breaking either bricks installs
or breaks Play compliance: never hand-write migration SQL (change the entity,
let KSP export the schema, copy the statements from it), and nothing scraped
ever reaches the `play` flavor — `scripts/verify-play-flavor.sh` checks the
built APK rather than trusting the source layout.
