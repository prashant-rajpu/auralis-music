# Auralis Music

A superpremium, ad-free music experience featuring a Dual-ExoPlayer crossfade audio engine, extensive multi-source catalog (YouTube Music, JioSaavn 320 kbps Master audio, Audius), time-synchronized lyrics with tap-to-seek, Android AudioFX sound profiles, and 100% offline playback.

---

## 🎵 Features

### 🎧 Advanced Dual-ExoPlayer Audio Engine
- **Simultaneous Cross-Mixing**: Two pooled `ExoPlayer` instances perform true overlapping crossfade (1s to 12s, default 5s) on automatic track transitions and manual skips.
- **Android AudioFX Sound Profiles**: Hardware-accelerated Bass Boost slider, Treble Boost slider, and 5 curated sound presets (*Flat / Studio Reference*, *Bass Heavy*, *Vocal Clarity*, *EDM / Club*, *Acoustic Warm*).
- **Persistent DSP State**: Remembers your EQ and crossfade configuration across app launches.

### 🌐 Massive Multi-Source Online Catalog
- **YouTube Music Engine**: Deep catalog coverage via YouTube InnerTube with direct high-bitrate Opus and AAC audio extraction.
- **JioSaavn 320 kbps Engine**: Pristine CD-quality 320 kbps unencrypted audio streams via native DES-ECB decryption (`38346591`).
- **Audius Decentralized Streaming**: Unrestricted 320 kbps MP3 streams.
- **Multi-Source Filtering**: Instantly filter search results across `All`, `YouTube Music`, `JioSaavn 320k`, and `Audius` with audio quality badges.

### 📜 Synchronized Lyrics & Tap-to-Seek
- **Multi-Provider Auto-Fallback**: Resolves time-synced LRC lyrics across YouTube Music, LRCLIB, NetEase Cloud Music, and JioSaavn.
- **Interactive Player Display**: Auto-scrolls the active lyric line to center and lets you tap any line to instantly jump playback to that timestamp.
- **100% Offline Lyrics Support**: Automatically caches synchronized LRC lyrics to Room DB when tracks are downloaded.

### 💾 Robust Offline Mode
- Single-tap download caching to app storage and Room database (`TrackDao`).
- Automatic zero-internet fallback that seamlessly serves your offline library.

### 👥 Live Jam Sessions
- Synchronized group listening via WebSockets (`JamWebSocketClient`).

---

## 🛠️ Architecture & Tech Stack

- **UI**: Jetpack Compose, Material 3, dynamic theme palette
- **Media Engine**: Android Media3 (ExoPlayer), Android AudioFX (`Equalizer`, `BassBoost`)
- **Architecture**: Clean Architecture / MVVM with StateFlow & Coroutines
- **Networking**: Retrofit 2, OkHttp 3, Gson
- **Persistence**: Room Database (v2) with automated schema migration
- **Dependency Injection**: Dagger Hilt
- **Image Loading**: Coil Compose

---

## 📦 Build Instructions

APKs are automatically generated via GitHub Actions CI:
```bash
gradle build assembleDebug
```
Check the **Actions** tab on GitHub to download the latest `auralis-debug-apk` artifact.
