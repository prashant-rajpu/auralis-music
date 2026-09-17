# Spec: Advanced Audio Engine, Multi-Source Music Catalog, and Synced Lyrics System

**Date:** 2026-09-17  
**Status:** Approved  
**Project:** Auralis Music Player  

---

## 1. Overview
This specification defines the architectural enhancements to transform Auralis into a comprehensive, high-fidelity music streaming and offline player with:
1. **Dual-ExoPlayer Crossfade Engine**: Seamless simultaneous audio cross-mixing across track transitions (1s to 12s, default 5s).
2. **AudioFX Sound Profiles**: Native hardware-accelerated Bass Boost, Treble Boost, and 5 genre presets (Bass Heavy, Vocal Clarity, EDM / Club, Acoustic / Warm, Flat / Studio Reference).
3. **Multi-Engine Online Catalog (YouTube Music + JioSaavn 320kbps + Audius)**: A massive online library powered by YouTube Music (InnerTube direct stream extraction), pristine 320 kbps JioSaavn streams with DES-ECB decryption, and Audius.
4. **Multi-Source Synced Lyrics Engine**: Real-time time-synchronized lyrics with auto-fallback across YouTube Music, LRCLIB, NetEase, and JioSaavn.
5. **Full Offline Lyrics & Audio Caching**: Downloaded tracks automatically cache time-synchronized lyrics into Room DB for offline playback.
6. **Polished Now Playing UI**: Live seekbar, auto-scrolling synced lyrics with tap-to-seek, and Audio FX settings bottom sheet.

---

## 2. Core Architectures & Data Flows

### 2.1 Dual-ExoPlayer Crossfade Architecture
```
                         [PlaybackManager]
                                │
          ┌─────────────────────┴─────────────────────┐
          ▼                                           ▼
   [Player A: ExoPlayer]                       [Player B: ExoPlayer]
 (Current Active Stream)                    (Incoming Crossfade Stream)
   Volume: 1.0 -> 0.0                          Volume: 0.0 -> 1.0
          │                                           │
          └─────────────────────┬─────────────────────┘
                                ▼
                       [AudioDevice / Sink]
```
- **Dual Instance Management**: Two instances of `ExoPlayer` are pooled in `PlaybackManager`. At any time, one is `primary` and the other is `standby`.
- **Crossfade Transition**:
  - When a track is scheduled to finish (or when manual skip is triggered), the incoming track is loaded on the `standby` player with volume set to `0.0f`.
  - Both players play concurrently while a coroutine interpolates volumes: outgoing player fades from `1.0f` to `0.0f`, incoming player fades from `0.0f` to `1.0f` over the configured duration (default: 5,000 ms).
  - Once crossfade is complete, the outgoing player stops and the incoming player becomes `primary`.

### 2.2 AudioFX Sound Profiles
- Attaches to the active player's `audioSessionId`.
- **BassBoost**: `android.media.audiofx.BassBoost` with strength range 0 to 1,000.
- **Equalizer / Treble**: `android.media.audiofx.Equalizer` applying predefined band gains for the 5 curated profiles:
  - *Bass Heavy*: Sub-bass / bass bands boosted (+6 dB), flat mids, flat highs.
  - *Vocal Clarity*: Mid frequencies boosted (+4 dB at 1kHz-3kHz), bass slightly rolled off (-2 dB).
  - *EDM / Club*: V-shaped EQ (+5 dB bass, +4 dB highs, -1 dB mids).
  - *Acoustic / Warm*: Warm lower mids (+3 dB), smooth treble (+2 dB).
  - *Flat / Studio Reference*: All bands set to 0 dB.
- State is persisted via `SoundProfilePreferences` to retain settings across app restarts.

### 2.3 Multi-Source Catalog (YouTube Music + JioSaavn + Audius)
- **YouTube Music InnerTube API**:
  - Endpoint: `https://music.youtube.com/youtubei/v1/search` and `https://music.youtube.com/youtubei/v1/player`
  - Context: `WEB_REMIX` client
  - Extracts direct adaptive audio streams (Opus 160 kbps, AAC 128-256 kbps), videoId, title, artist, thumbnails, duration.
- **JioSaavn Engine**:
  - Query: `https://www.jiosaavn.com/api.php`
  - DES-ECB decryption key: `38346591`
  - Yields direct unencrypted 320 kbps MP4/AAC streams.
- **Audius Engine**:
  - Decentralized discovery API (`https://discoveryprovider.audius.co/v1/tracks/trending`, `/search`).
- **UI Aggregator**:
  - Unified search results with source filter chips: `All`, `YouTube Music`, `JioSaavn 320k`, `Audius`.
  - Badges clearly indicate audio quality: `320 kbps Master`, `Opus High 160k`, `Audius 320k`.

### 2.4 Synced Lyrics Engine & Offline Cache
- **Model**:
  ```kotlin
  data class LyricLine(
      val timestampMs: Long,
      val text: String
  )
  ```
- **Fallback Hierarchy**:
  1. Local Room DB: Check `TrackEntity.syncedLyricsJson`. If present, return cached lyrics immediately.
  2. YouTube Music Lyrics endpoint: Fetch official synced/plain lyrics if track is from YouTube Music.
  3. LRCLIB API: `https://lrclib.net/api/get?artist_name={artist}&track_name={title}&duration={duration}`
  4. NetEase / JioSaavn: Secondary fallback for international and Indian regional songs.
- **LRC Parser**: Robust parser handling `[mm:ss.xx]` and `[mm:ss.xxx]` timestamps, sorting lines sequentially.
- **Offline Download Flow**:
  - When `MusicRepository.downloadTrack(track)` is called, the repository fetches lyrics if not already present, serializes to JSON, and persists to Room `TrackEntity.syncedLyricsJson`.

### 2.5 UI / UX Layout
- **Now Playing Screen (`FullPlayerScreen.kt`)**:
  - Live progress slider with active coroutine polling ExoPlayer current position.
  - Lyrics toggle button: switches between Album Art view and smooth scrolling Synced Lyrics list.
  - Tap-to-seek: Tapping any lyric line seeks the player to `timestampMs`.
  - Top-bar Audio FX icon: opens the Sound Profiles bottom sheet with Bass Boost, Treble Boost, Crossfade Duration slider (1s–12s), and preset chips.
- **Home Screen (`HomeScreen.kt`)**:
  - Top search bar with source filter chips (`All`, `YouTube Music`, `JioSaavn 320k`, `Audius`).
  - Track cards with bitrate badges and one-tap download.

---

## 3. Database Schema Updates
In `TrackEntity.kt`:
```kotlin
@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val albumArtUrl: String?,
    val localFilePath: String,
    val durationMs: Long,
    val source: String = "Offline",
    val qualityBadge: String = "320 kbps Master",
    val syncedLyricsJson: String? = null
)
```
Room database version bumped to 2 with fallback-to-destructive migration or migration strategy.

---

## 4. Verification & Testing
- Unit tests for `LrcParser`: Verify parsing standard timestamps, out-of-order timestamps, and multi-tag lines.
- Unit tests for `JioSaavnDecryptor` and `YouTubeMusicParser`.
- Verification of Dual-ExoPlayer crossfade volume ramping math.
- Verification of Room entity storage and offline lyrics retrieval.
