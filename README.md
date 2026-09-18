# Auralis Music

An ad-free Android music player with multi-source streaming, offline downloads, synced lyrics,
AudioFX sound profiles, crossfade playback and a listen-together mode.

## Features

- **Playback**: overlapping crossfade between tracks (1–12 s), gapless-style transitions on skip,
  shuffle and repeat, 0.75–2× speed, sleep timer with fade-out, notification / lockscreen /
  Bluetooth controls.
- **Sound profiles**: Android AudioFX equalizer with five presets (Flat, Bass Heavy, Vocal Clarity,
  EDM / Club, Acoustic Warm) plus bass and treble boost, remembered across launches.
- **Sources**: YouTube Music (search, related tracks, artist pages), JioSaavn (320 kbps AAC) and
  Audius (320 kbps MP3), searchable together with per-source badges.
- **Synced lyrics**: LRCLIB, Lyrics.ovh and NetEase with auto-scroll and tap-to-seek; lyrics are
  cached with downloads for offline use.
- **Offline**: one-tap downloads to app storage; the Home screen falls back to your downloads when
  there is no network.
- **Together Mode**: listen in sync with another phone, share the queue, send reactions.
- **Infinite Radio**, SponsorBlock skipping for YouTube tracks, playlist import/export links.

See [`docs/STATUS.md`](docs/STATUS.md) for what is known to be missing and the roadmap.

> YouTube Music and JioSaavn are accessed through unofficial endpoints. They can stop working
> without notice, and builds that include them are distributed outside Google Play.

## Tech stack

Kotlin, Jetpack Compose (Material 3), Media3 ExoPlayer + MediaSession, Hilt, Room, Retrofit /
OkHttp / Gson, Coil, Kotlin coroutines and Flow. Single `app` module.

## Building

Requires JDK 17 and the Android SDK (platform 37). The Gradle wrapper is committed.

```bash
./gradlew testDebugUnitTest assembleDebug     # tests + debug APK
./gradlew assembleRelease                     # R8-minified release APK (unsigned without a keystore)
```

Every push runs lint, unit tests, and both debug and release builds in GitHub Actions; the debug
APK is attached to the run as `auralis-debug-apk`. Tags matching `v*` publish a signed APK to
GitHub Releases — see [`docs/RELEASING.md`](docs/RELEASING.md).
