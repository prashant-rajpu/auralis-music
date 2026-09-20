# Auralis — Handover

**Looking for what to do next?** That is
[`TODO.md`](TODO.md). This file is what exists and how it was verified.

Everything done, everything left, and how to pick it up on a desktop.
Accurate as of `a487287` on `main`.

---

## 1. Where things stand right now

| | |
|---|---|
| `main` | `a487287` — everything through PR #6 is merged. All of v4.0 and v4.1: Room v4, the relay, the sync layer, encryption, the Together tab, the couple layer, and the two crash fixes that came out of testing on real phones |
| Working branch | `claude/grill-me-c8klg5`, restarted from `main`. No open PR |
| CI | **Green** on every commit |
| Tests | **283** on `play`, **290** on `plus`, **26** in `relay/`, plus **17** end-to-end checks against a live relay. 0 failures |
| Size | ~15,000 lines of Kotlin in `app/src/main` |
| Editions | `play` (Play-Store-safe) and `plus` (sideload, adds YouTube Music + JioSaavn) |

Nothing is half-finished in the tree. Every commit builds both flavors in
debug and R8 release, passes lint with zero errors, and passes the `play`
APK purity check.

Two things outside the tree are unfinished, and both are in
[`TODO.md`](TODO.md) §0: there are two relays deployed and the app points at
the older one, and no session has yet been measured on two real phones.

---

## 2. What's done

### Phase 0 — Stabilise (merged to `main` as PR #1)

The app could not produce a release APK at all when this started.

- Gradle wrapper committed; version catalog; AGP 9.4.0, Kotlin 2.4.20, KSP
  (replacing kapt), Hilt 2.60.1, Compose BOM 2026.09.00, Media3 1.11.1,
  Room 2.8.5. `compileSdk 37`, `targetSdk 36`, `minSdk 26`, Java 17.
- Release signing from a gitignored `keystore.properties` or
  `AURALIS_KEYSTORE_*` env vars; `proguard-rules.pro` so R8 does not break
  Gson DTOs.
- **Security:** HTTP header logging no longer ships in release; Together
  Mode peers can no longer inject arbitrary stream URLs (inbound `mediaUrl`
  is discarded and re-resolved locally, art hosts allowlisted);
  `MediaSession.onConnect` stopped accepting every controller; WebSocket and
  HTTP transports deduplicate; downloaded music excluded from cloud backup.
- **Correctness:** `isCrossfading` no longer sticks on cancel; the played-id
  set is capped; offline paths bypass the network resolver; audio effects
  attach on `onAudioSessionIdChanged` rather than every `STATE_READY`.
- CI runs lint + tests + both release assembles on every push; `release.yml`
  publishes a signed APK on a `v*` tag.

### Phase 1 — Editions and content architecture (merged in PR #1)

- `play` / `plus` product flavors. `plus` carries `applicationIdSuffix
  ".plus"` so both install side by side and Play cannot replace a sideloaded
  build.
- `MusicSource` / `StreamResolver` abstractions; `AggregatedMusicRepository`
  fans out across sources. Audius, Jamendo and on-device files in both
  editions; YouTube Music, JioSaavn, SponsorBlock and NetEase compiled only
  into `plus` (`app/src/plus/`).
- On-device music via MediaStore with the runtime permission flow.
- `scripts/verify-play-flavor.sh` proves it on the *built artifact*, not the
  source layout: it unzips the `play` APK and fails if any `plus`-only
  package or the JioSaavn key appears.

### Phase 2 (partial) — Playback internals

- **`5e93cbf`** — the queue became pure, tested logic (`QueueState` /
  `QueueOps`). This fixed three real bugs: shuffle was picking a random
  index on every skip (so tracks repeated forever and others never played),
  `addToQueue` inserted user picks in the wrong place when the queue had
  duplicates, and Infinite Radio could skip a track. 31 tests.
- **`9c08fc9`** — resolved stream URLs cached in Room, with expiry read from
  the URL's own `expire=` parameter. Removed
  `fallbackToDestructiveMigration`, which would have silently deleted every
  download on the next schema change, and replaced it with a real migration.

### Design pass — the UI

- **`ae29dee`** — a real theme. Colours were one hardcoded baby-pink
  palette; now there is a raw palette plus semantic tokens, and
  `AuralisTheme(themeMode, accent)` gives Follow-system / Light / Dark /
  AMOLED and six accents, persisted and applied live. ~125 hardcoded colours
  became tokens. `values-night` stops the cold start flashing white.
- **`1a88e2d`** — the player is a draggable sheet over the tabs, not a
  separate route. Offset and alpha are read in the layout and draw phases,
  so dragging does not recompose the full player every frame.
- **`6d2ae50`** — Explore and Library were both the Home screen with a
  different filter, and the Home feed was one list cut into three with
  `take(4)`/`drop(4)`. Now: Home is a feed of independently-fetched shelves,
  Explore is search with mood tiles and catalog filters, Library is real.

### v4.0 — The library becomes real

- **`584e517`** — Room v4. Eleven new tables: `liked_tracks`,
  `disliked_tracks`, `playlists`, `playlist_tracks`, `play_history`,
  `queue_items`, `player_state`, `catalog_tracks`, and `jam_sessions` /
  `jam_events` / `shared_plays`. The migration is **generated from the
  exported schema**, not hand-written. `LibraryRepository` is the façade the
  UI uses; `PrefsToRoomImporter` moves the old SharedPreferences blobs
  across once.
- **`912d1bf`** — likes now survive a force-stop (they were an in-memory
  `Set<String>` in `PlayerViewModel`). Library gained Playlists, Liked, Most
  played and Artists. `PlaybackHistoryRecorder` writes one `play_history`
  row per track actually listened to. "Add to playlist" lives inside the
  track context menu, so every screen got it at once.
- **`4c8a9da`** — the queue and position survive a force-stop, via
  `PlaybackStatePersister`. A restored session does not touch the network
  until you press play.

### Testing approach worth knowing about

Room migrations normally need `MigrationTestHelper`, an instrumented test,
and a device. CI has none. So `sqlite-jdbc` runs the real migration against
a real v3 database on the JVM instead.

**Both migration suites were mutation-tested**, not assumed: planting
`DROP TABLE tracks` fails four tests including
`migrationKeepsEveryDownloadedTrack`; flipping one column from `INTEGER` to
`TEXT` fails `everyV4TableIsCreatedExactlyAsRoomGeneratedIt`. Same for
`PlaybackStatePersisterTest` — deleting the partial-queue guard fails
`gives up when some tracks can no longer be resolved`.

---

## 3. What's left — the balance

The approved plan is in `/root/.claude/plans/` in the session, and
summarised here. Ordered as agreed: foundation, then Jam (the headline),
then the rest.

### v4.0 — remaining

| Item | Status |
|---|---|
| **Player moves into the service** — delete `PlaybackManager` (943 lines) and `AuralisQueueForwardingPlayer`; add `PlaybackService`, `PlayerFactory`, `PlayerConnection` (MediaController), `AutoplayController`, `TransitionFader` | **Not started.** Deliberately deferred — see §6 |

Everything else in v4.0 is done. The two observers
(`PlaybackHistoryRecorder`, `PlaybackStatePersister`) watch the player's
flows rather than being called from mutation sites, so both survive this
rework unchanged.

### v4.1 — Together 2.0 (the headline, **next up**)

Built for long-distance couples. Currently Together runs over *public*
ntfy.sh topics: anyone who guesses a room code can join and push tracks,
there is no server clock so drift correction is guesswork (the current
`shouldSeek` tolerates 1500 ms), and there is no presence.

| Part | Contents |
|---|---|
| **A. Relay** | **Done** — `relay/`, documented in [`docs/RELAY.md`](RELAY.md). Cloudflare Worker + one Durable Object per room with WebSocket Hibernation. `POST /rooms` → `{code, hostToken}`; `GET /rooms/{code}/ws`. Server injects `senderId` and `serverMs`. Allowlist validation (no stream URL can reach the wire), 4 KB cap, 16 members, 10 msg/s, per-IP join limits, 30-day idle TTL, immediate host handover with token reclaim. Not yet deployed, and not yet spoken to by the app. |
| **B. Protocol + sync** | **Done** — `app/src/main/java/com/auralis/app/together/`. `TogetherProtocol` (kotlinx.serialization, mirroring the relay's wire format); **`TrackRef` never carries a stream URL**, and `toTrack` always produces an empty `mediaUrl`, so the Phase-0 fix holds by construction rather than by allowlist. `ClockSync` (min-RTT-gated EMA offset). `SyncController` drift table: >400 ms hard seek, 120–400 ms speed nudge to 0.98×/1.02× for ≤3 s, <120 ms leave alone, hold while the peer buffers. `RelayWebSocketTransport` behind a `TogetherTransport` interface with jittered reconnect and token rejoin. **Not yet wired to the player or the UI** — that is what remains of v4.1. |
| **C. Social** | **Done bar skip voting** — shared queue, live presence, encrypted chat, reactions, rejoin from a stored token, and invites as a code, a link, a share-sheet message and a QR code. `auralis://join/CODE` opens the app straight into the session. Either partner can drive: a local play/pause/skip takes control of the room rather than being corrected away. |
| **D. Couple layer** | **Mostly done.** Our Songs (ranked by shared plays), the listening streak, their local time, dedicate-a-song, lyric moments, goodnight mode (both phones fade out on the same room timestamp), knock, and session memories — all of it fed by `CoupleRepository` writing `jam_sessions` / `jam_events` / `shared_plays` as a session runs. Dedications, lyric moments and knocks ride inside the one encrypted channel, so the relay cannot even tell them apart. **Still to build:** scheduled sessions, a dedications inbox screen, the couple profile, and editable name/reaction packs. |
| **E. Privacy** | **Done** — `TogetherCrypto` (HKDF-SHA256 → AES-256-GCM, random nonce per message) and `TogetherInvite`. Chat, dedications and lyric moments are encrypted before they leave the phone; the relay stores ciphertext. Playback state stays plaintext — the relay needs the timestamps and they are not sensitive. The key comes from a 20-character secret the invite link and QR carry and the relay never sees; a hand-typed six-character code cannot carry that, so those sessions key from the code and are marked `CODE_ONLY`. [`docs/PRIVACY.md`](PRIVACY.md) states the difference plainly. |
| **F. UI** | **Done** — Together is the fourth nav tab. Out of a session: your name, a start button, an invite to paste, and the room you were in last. In one: the partner card with their local time, the sync indicator, the shared queue, chat and the reaction tray. Settings gains a Together section for the relay address. **The old ntfy sheet is still wired to Home and the full player** — deliberately, since the relay path has not run on a real device yet. It goes once this one is proven. |

### v4.2 — Showpiece player

Artwork-driven colour (`androidx.palette`) with a **contrast guard** —
desaturate until text hits 4.5:1, because near-white and near-black covers
otherwise make the player unreadable; three modes, default player-only.
Hand-built waveform scrubber on `Canvas` (Material3 1.4.0 has `MotionScheme`
and `SwipeToDismissBox` but **no** `WavyProgressIndicator` or
`MaterialShapes` — those are 1.5.0-alpha). Swipe artwork to change track,
drag-to-reorder queue, shared element transition, `MotionScheme.expressive()`
app-wide, full-screen lyrics, bundled variable font.

### v4.3 — Personalization

`AffinityScorer` (decay-weighted, pure), co-occurrence, `DailyMixWorker`,
smart shuffle with artist spacing, moods that learn, Wrapped — and **Wrapped
For Two** off `shared_plays`.

### v4.4 — Customization

Settings rebuilt with **search**, and a `SettingsAuditTest` so a dead
setting fails CI. Crossfade/fade, gapless, skip silence, normalization,
independent pitch; a real equalizer UI; home layout editor; navigation
editor; player layout presets; full backup/restore.

### v4.5 — Platform

Glance widgets (including one showing whether your partner is listening),
Android Auto browse tree, Quick Settings tile, folder browsing, tag editor.
**Audio visualizer is `plus`-only and opt-in** — `Visualizer` needs
`RECORD_AUDIO`, which triggers a Play privacy-policy requirement for a
decorative feature.

---

## 4. Picking up on a desktop

```bash
git clone https://github.com/prashant-rajpu/auralis-music.git
cd auralis-music
git checkout claude/grill-me-c8klg5
```

**Prerequisites:** JDK 17, Android SDK with platform 37 and build-tools.
Android Studio bundles both. No `gradle` install needed — use `./gradlew`.

**Optional local config** in `~/.gradle/gradle.properties` (never commit):

```properties
auralis.jamendoClientId=<your Jamendo client id>
```

Without it the Jamendo source is simply absent; everything else works.

**Build and run:**

```bash
./gradlew assemblePlusDebug          # sideload edition, all sources
./gradlew assemblePlayDebug          # Play-safe edition
./gradlew installPlusDebug           # with a device attached
```

**The verification loop — run this before every commit:**

```bash
./gradlew testPlayDebugUnitTest testPlusDebugUnitTest \
          assemblePlayRelease assemblePlusRelease lint
ANDROID_HOME=$ANDROID_HOME ./scripts/verify-play-flavor.sh
```

That last script is the one that matters for Play compliance — it inspects
the built APK, not the source tree.

---

## 5. Repo gotchas

1. **AGP 9 has Kotlin built in.** Do **not** apply
   `org.jetbrains.kotlin.android`; the build fails if you do.
2. **Never hand-edit the migration SQL** in
   `data/local/Migrations.kt`. Room aborts on open if a migrated table
   differs from its exported schema by so much as a column order, which
   bricks every install. Change entities, build (KSP writes
   `app/schemas/…/N.json`), then generate the statements from that schema.
   `MigrationSqlTest` fails if the two drift apart.
3. **No destructive migration fallback**, on purpose. A schema change needs
   a real migration or the app will not open.
4. **Room schemas are committed** (`app/schemas/`). They are inputs to the
   tests, not build noise.
5. **Secrets never enter the repo.** `keystore.properties`, `*.jks`,
   `local.properties` are all gitignored. CI reads `JAMENDO_CLIENT_ID` and
   `KEYSTORE_BASE64` from GitHub repository secrets.
6. **Jamendo client secret:** a client *secret* was pasted into a chat
   earlier in this project. Only the client **ID** is needed by the app.
   If that secret has not already been regenerated at
   devportal.jamendo.com, do it — it was never stored or committed here,
   but it was exposed.

---

## 6. What could not be verified, and why

There is no emulator, no device and no hardware virtualisation in the
environment these changes were built in. So:

- **Everything structural is verified:** compilation, 113 unit tests, lint,
  R8 release builds of both flavors, and APK-level proof that `play`
  contains no scraped-source code.
- **Nothing visual or behavioural is verified.** Whether the drag feels
  right, whether contrast holds on real album art, whether playback
  actually resumes correctly after a force-stop on a real phone — all
  unconfirmed. Install the APK and check.

This is also why the **service rework was deliberately deferred**. The plan
had queue persistence arriving inside a rewrite that deletes a 943-line
playback class. Without a device, that rewrite's entire risk sits exactly
where verification stops. `PlaybackStatePersister` delivers the user-visible
result now; the rewrite can happen later as an isolated change, tested on a
real phone.

**For v4.1 specifically:** the relay and the sync logic can be machine-tested
without any phone — the relay runs locally under `wrangler dev` with
simulated clients, and `ClockSync`/`SyncController` are pure functions over
timestamps. What cannot be simulated is whether two phones *sound* together:
decode latency, audio buffer depth and especially Bluetooth output delay
(150–300 ms, and device-specific) need real hardware on real networks.

The most useful thing a human can contribute there is not a device but a
**drift log from a real session** — measured RTT, clock offset, drift
samples and correction events, exported after twenty minutes of listening
with a partner. That turns the sync thresholds from guesses into numbers.

---

## 7. Suggested next step

**Deploy the relay and run the two-phone test.** Everything in v4.1 is written
and machine-checked; none of it has spoken to a real relay or a second device.
`cd relay && npx wrangler login && npm run deploy`, then put the printed URL
in `auralis.relayUrl` (or Settings → Together). Until that happens, the
Together tab correctly reports that it has nowhere to connect.

After that, v4.2 — the showpiece player: artwork-driven colour with a contrast
guard, the waveform scrubber, swipe-between-tracks and drag-to-reorder.
