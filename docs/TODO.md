# What is left

Everything in v4.0 and v4.1 is merged. This is the ordered list of what remains,
written so any item can be picked up cold.

Companion docs: [`HANDOVER.md`](HANDOVER.md) for what exists and how it was
verified, [`RELAY.md`](RELAY.md) for the server, [`PRIVACY.md`](PRIVACY.md) for
what the app knows about you.

---

## 0. Blocked on you

Small, but nothing else about Together can be trusted until they are done.

- [ ] **Settle on one relay.** Two are live. `auralis-relay.wood-repair.workers.dev`
      is the claimed preview account running the *old* code, and is what the last
      APK points at. A second `auralis-relay`, created 2026-09-20 09:02 in your
      main account, has the current code — CSPRNG room codes, logs, traces — and
      its URL is not recorded anywhere. Get it from the `wrangler deploy` output,
      then either rebuild with `-Pauralis.relayUrl=<url>` or set it in
      Settings → Together.
- [ ] **Delete whichever relay loses**, and the empty `auralis-music` Pages
      project, so there is one address and no confusion later.
- [ ] **Put the URL somewhere permanent** — `auralis.relayUrl` in
      `~/.gradle/gradle.properties` for local builds, and an `AURALIS_RELAY_URL`
      secret for CI. There is deliberately no default in the repo.
- [ ] **Run the two-phone test** and keep the numbers: the Together tab shows
      live drift in ms and the round trip. Those two are what turn the sync
      thresholds from reasoned guesses into measurements.

---

## 1. Nothing here has run on a phone but you

Worth stating plainly before anything below gets planned on top of it.

Machine-checked: compilation, 283 unit tests on `play` / 290 on `plus`, 26 relay
protocol tests, 17 end-to-end checks against a live relay, lint, R8, and APK-level
proof that `play` contains no scraped-source code.

**Not checked by anyone but you:** whether it feels right, whether contrast holds
on real album art, whether playback resumes correctly after a force-stop, and
whether two phones actually *sound* together. The last one depends on decode
latency and Bluetooth output delay (150–300 ms, device-specific) and cannot be
measured from a container.

Also untested anywhere: Durable Object hibernation and the 30-day room TTL, both
of which take real time to observe. `@cloudflare/vitest-pool-workers` would cover
them; it will not install here (npm fails resolving its vitest 4 peer).

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

- [ ] **Durable Object tests.** Retry `@cloudflare/vitest-pool-workers` — the npm
      resolver bug may be fixed by now. It is the only way to cover hibernation
      and the alarm-driven TTL. `npm run smoke` covers everything else, but needs
      a deployment.
- [ ] **A drift soak test on real hardware.** `SyncControllerTest` runs an hour of
      simulated ticks; nothing has run an hour of real ones. Log RTT, offset,
      drift samples and corrections, and compare against the table in
      `SyncConfig`.
- [ ] **Macrobenchmark** for the player once v4.2 lands.
- [ ] Consider a custom domain for the relay. A `workers.dev` address has no WAF
      settings, so the managed bot challenge in front of it cannot be turned off —
      the app detects it and says so, but cannot pass it.

---

## The order I would actually go in

1. **§0** — one relay, one URL, the two-phone test. Everything about Together is
   guesswork until this is done.
2. **§3 delete the old path** — assuming the relay holds up. Two Together entry
   points is confusing and the ntfy code is dead weight.
3. **§2 the service rework** — alone, in its own release, tested on a device. It
   is the biggest correctness risk left in the app.
4. **§4 the showpiece player** — the first release since v4.0 that is purely
   about how the app feels, and the one that unblocks lyric moments.
5. Then §5, §6, §7 in order; §8 alongside whatever is in flight.
