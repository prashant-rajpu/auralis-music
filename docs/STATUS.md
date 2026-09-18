# Project status

Honest snapshot of what Auralis does today, what is known to be missing, and where it is going.
Updated with each phase of the roadmap.

## Editions

Auralis builds as two editions from one source tree:

| | `play` (`com.auralis.app`) | `plus` (`com.auralis.app.plus`) |
|---|---|---|
| Catalogs | Audius, Jamendo, music on the device | the same, plus YouTube Music and JioSaavn |
| Lyrics | LRCLIB, Lyrics.ovh | the same, plus NetEase |
| Non-music segment skipping | — | SponsorBlock |
| Artist pages | built from a catalog search | YouTube Music artist pages |
| Distribution | Google Play | GitHub releases / sideload |

The unofficial-endpoint code lives entirely in `src/plus`, and
`scripts/verify-play-flavor.sh` fails the build if any of it reaches a `play` APK.
Jamendo needs a free client id (`auralis.jamendoClientId` or `AURALIS_JAMENDO_CLIENT_ID`);
without one that source is simply disabled.

## Works today

- **Playback**: two ExoPlayer instances with an overlapping volume crossfade (1–12 s), shuffle,
  repeat (off / all / one), 0.75–2× speed, sleep timer with fade-out, media notification,
  lockscreen and Bluetooth controls, Android AudioFX equalizer presets with bass and treble boost.
- **Sources**: Audius (320 kbps MP3), Jamendo, and music already on the device in every
  edition; YouTube Music and JioSaavn (320 kbps AAC) in `plus`. YouTube audio is Opus/AAC at
  roughly 128–160 kbps; where a JioSaavn match for the same song exists it is used instead.
  The Streaming Quality setting now picks the bitrate variant where a catalog offers one.
- **Lyrics**: LRCLIB → Lyrics.ovh → NetEase, synced with tap-to-seek, cached in Room for
  downloaded tracks.
- **Offline**: one-tap download to app storage + Room; Home falls back to downloads with no network.
- **Together Mode**: playback sync, queue sharing, reactions and message presets between phones
  over public ntfy.sh topics.
- **Extras**: Infinite Radio autoplay, SponsorBlock skipping for YouTube tracks, playlist
  import/export links, recently played and top artists.

## Known limitations

- YouTube Music and JioSaavn are accessed through unofficial endpoints; they can break without
  notice and are not acceptable on Google Play. A Play-safe flavor is planned (Phase 1).
- Together Mode has no authentication: anyone with the session code can join and send messages.
  Incoming URLs are allowlisted so a peer cannot make the app open arbitrary content.
- The accent-theme picker in Settings is stored but not applied; there is no dark mode.
- Likes/dislikes in the player are in-memory only; there is no Library, Playlists or Liked Songs
  screen. Playlist import/export lives in Settings.
- Only the English UI exists; strings are hardcoded.
- Single Gradle module; unit tests cover the LRC parser, Together protocol and queue helpers only.

## Roadmap

| Phase | Scope | Status |
|---|---|---|
| 0 | Modern toolchain, signed release builds, security and correctness fixes | Done |
| 1 | `play` / `plus` product flavors, `MusicSource` abstraction, local library | Done |
| 2 | Single ExoPlayer owned by the media service, persistent queue | Planned |
| 3 | Room v3: playlists, likes, history, downloads with progress, backup | Planned |
| 4 | On-device discovery: daily mixes, learned moods, listening stats | Planned |
| 5 | Listen Together 2.0 on an authenticated relay with roles and chat | Planned |
| 6 | Sheet player, dark/dynamic themes, haptics, localization, widgets, Android Auto | Planned |
| 7 | Release automation, lint gates, store listings, compliance | Planned |

## Verifying a build

```bash
./gradlew lint testDebugUnitTest assembleDebug assembleRelease
```

CI runs the same on every push; see `docs/RELEASING.md` for signed releases.
