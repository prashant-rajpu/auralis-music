# Auralis Music

An ad-free Android music player with multi-source streaming, offline downloads, synced lyrics,
AudioFX sound profiles, crossfade playback and a listen-together mode.

## Features

- **Playback**: overlapping crossfade between tracks (1–12 s), gapless-style transitions on skip,
  shuffle and repeat, 0.75–2× speed, sleep timer with fade-out, notification / lockscreen /
  Bluetooth controls.
- **Sound profiles**: Android AudioFX equalizer with five presets (Flat, Bass Heavy, Vocal Clarity,
  EDM / Club, Acoustic Warm) plus bass and treble boost, remembered across launches.
- **Sources**: Audius, Jamendo and music already on your device, searchable together with
  per-source badges. The sideload edition adds YouTube Music and JioSaavn (320 kbps AAC).
- **Synced lyrics**: LRCLIB, Lyrics.ovh and NetEase with auto-scroll and tap-to-seek; lyrics are
  cached with downloads for offline use.
- **Offline**: one-tap downloads to app storage; the Home screen falls back to your downloads when
  there is no network.
- **Together Mode**: listen in sync with another phone, share the queue, send reactions.
- **Infinite Radio**, SponsorBlock skipping for YouTube tracks, playlist import/export links.

See [`docs/STATUS.md`](docs/STATUS.md) for what is known to be missing and the roadmap.

> Auralis builds as two editions: **play** (Audius, Jamendo and local files) and **plus**, which
> adds YouTube Music and JioSaavn. Those use unofficial endpoints, can stop working without
> notice, and ship only outside Google Play — see [`docs/STATUS.md`](docs/STATUS.md).

## Tech stack

Kotlin, Jetpack Compose (Material 3), Media3 ExoPlayer + MediaSession, Hilt, Room, Retrofit /
OkHttp / Gson, Coil, Kotlin coroutines and Flow. Single `app` module.

## Building

Requires JDK 17 and the Android SDK (platform 37). The Gradle wrapper is committed.

Or open it in a **GitHub Codespace** — `.devcontainer/` installs both for you.
Everything but the emulator works there; see [`docs/CODESPACES.md`](docs/CODESPACES.md).

```bash
./gradlew testPlusDebugUnitTest assemblePlusDebug   # sideload edition: tests + debug APK
./gradlew assemblePlayRelease                       # Play edition, R8-minified
./scripts/verify-play-flavor.sh                     # assert no plus-only code leaked into it
```

Every push runs lint, unit tests and debug plus release builds for **both** flavors in GitHub
Actions, verifies the play APK is free of plus-only code, and attaches both debug APKs to the run. Tags matching `v*` publish a signed APK to
GitHub Releases — see [`docs/RELEASING.md`](docs/RELEASING.md).
