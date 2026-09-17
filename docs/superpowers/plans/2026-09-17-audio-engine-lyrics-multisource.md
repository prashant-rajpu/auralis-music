# Advanced Audio Engine, Multi-Source Music Catalog, and Synced Lyrics Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement a Dual-ExoPlayer crossfade audio engine with AudioFX sound profiles, a multi-engine streaming catalog (YouTube Music InnerTube, JioSaavn 320k, Audius), multi-source time-synchronized lyrics with tap-to-seek, and full offline caching in Auralis.

**Architecture:** A Dual-ExoPlayer architecture enables simultaneous crossfade between tracks while Android AudioFX (`Equalizer`, `BassBoost`) handles audio enhancement profiles. YouTube Music InnerTube endpoints complement JioSaavn and Audius for a comprehensive online library. A multi-provider lyrics pipeline (Local DB -> YouTube Music -> LRCLIB -> NetEase -> JioSaavn) delivers synchronized LRC lyrics displayed in a tap-to-seek interactive player view and cached for offline use.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Android Media3 (ExoPlayer), Android AudioFX, Retrofit, OkHttp, Room DB, Kotlin Coroutines & Flow, Dagger Hilt.

**Spec:** `docs/superpowers/specs/2026-09-17-audio-engine-lyrics-multisource-design.md`

## Global Constraints
- Target Android SDK: Android 13+ (API 33/34), minSdk 26.
- Pure Kotlin standard library and Android SDK primitives where possible (DES-ECB via `javax.crypto.Cipher`, LRC parser in pure Kotlin).
- Room database schema version bumped to 2 with `fallbackToDestructiveMigration()`.
- Unidirectional Data Flow (MVI/MVVM) with StateFlow.

---

### Task 1: Domain Models & Database Schema Updates
**Files:**
- Create: `app/src/main/java/com/auralis/app/domain/model/LyricLine.kt`
- Create: `app/src/main/java/com/auralis/app/domain/model/SoundProfile.kt`
- Modify: `app/src/main/java/com/auralis/app/domain/model/Track.kt`
- Modify: `app/src/main/java/com/auralis/app/data/local/TrackEntity.kt`
- Modify: `app/src/main/java/com/auralis/app/data/local/AuralisDatabase.kt`

**Interfaces:**
- Consumes: Existing `Track` and `TrackEntity`
- Produces: `LyricLine`, `SoundProfile`, `SoundPreset`, updated `Track`, updated `TrackEntity` with `syncedLyricsJson`

- [ ] **Step 1: Create LyricLine and SoundProfile domain models**
- [ ] **Step 2: Update Track domain model with lyrics field**
- [ ] **Step 3: Update TrackEntity with syncedLyricsJson and bump Room Database to version 2**
- [ ] **Step 4: Verify syntax and commit changes**

---

### Task 2: Pure Kotlin Synced LRC Parser & Unit Tests
**Files:**
- Create: `app/src/main/java/com/auralis/app/lyrics/LrcParser.kt`
- Create: `app/src/test/java/com/auralis/app/lyrics/LrcParserTest.kt`

**Interfaces:**
- Consumes: Raw LRC formatted String
- Produces: `LrcParser.parse(lrcContent: String): List<LyricLine>` sorted chronologically

- [ ] **Step 1: Write LrcParserTest defining expected behavior for standard and edge-case LRC timestamps**
- [ ] **Step 2: Implement LrcParser with regex timestamp matching `\\[(\\d+):(\\d+(?:\\.\\d+)?)\\]`**
- [ ] **Step 3: Run LrcParserTest and verify passing**
- [ ] **Step 4: Commit changes**

---

### Task 3: Multi-Provider Lyrics Clients & LyricsRepository
**Files:**
- Create: `app/src/main/java/com/auralis/app/network/LrclibApi.kt`
- Create: `app/src/main/java/com/auralis/app/network/NetEaseLyricsApi.kt`
- Create: `app/src/main/java/com/auralis/app/lyrics/LyricsRepository.kt`
- Modify: `app/src/main/java/com/auralis/app/di/NetworkModule.kt`

**Interfaces:**
- Consumes: Track title, artist, duration, and optional local syncedLyricsJson
- Produces: `LyricsRepository.getLyrics(track: Track): List<LyricLine>`

- [ ] **Step 1: Define LrclibApi and NetEaseLyricsApi Retrofit interfaces and DTOs**
- [ ] **Step 2: Provide LrclibApi and NetEaseLyricsApi in NetworkModule**
- [ ] **Step 3: Implement LyricsRepository with fallback hierarchy: Local DB/Cache -> LRCLIB -> NetEase -> JioSaavn**
- [ ] **Step 4: Verify syntax and commit changes**

---

### Task 4: YouTube Music (InnerTube) Engine
**Files:**
- Create: `app/src/main/java/com/auralis/app/network/YouTubeMusicApi.kt`
- Modify: `app/src/main/java/com/auralis/app/network/NetworkMusicRepository.kt`
- Modify: `app/src/main/java/com/auralis/app/domain/repository/MusicRepository.kt`
- Modify: `app/src/main/java/com/auralis/app/di/NetworkModule.kt`

**Interfaces:**
- Consumes: Query string
- Produces: `YouTubeMusicApi.search(query: String): List<Track>` with audio quality badge "Opus High 160k" and direct playable stream URL

- [ ] **Step 1: Create YouTubeMusicApi client utilizing YouTube InnerTube endpoint**
- [ ] **Step 2: Register YouTubeMusicApi in NetworkModule**
- [ ] **Step 3: Update NetworkMusicRepository to support multi-source search and source filtering (All, YouTube, JioSaavn, Audius)**
- [ ] **Step 4: Verify syntax and commit changes**

---

### Task 5: AudioFX Manager & Sound Profiles
**Files:**
- Create: `app/src/main/java/com/auralis/app/playback/AudioEffectManager.kt`
- Create: `app/src/main/java/com/auralis/app/playback/SoundProfilePreferences.kt`

**Interfaces:**
- Consumes: `audioSessionId: Int`, `SoundProfile`
- Produces: Hardware-accelerated equalizer preset curves, bass boost adjustment, and persistent preferences

- [ ] **Step 1: Implement SoundProfilePreferences using SharedPreferences**
- [ ] **Step 2: Implement AudioEffectManager managing Android Equalizer and BassBoost instances**
- [ ] **Step 3: Verify safe initialization and release on audioSession changes**
- [ ] **Step 4: Commit changes**

---

### Task 6: Dual-ExoPlayer Crossfade & Playback Manager Enhancements
**Files:**
- Modify: `app/src/main/java/com/auralis/app/playback/PlaybackManager.kt`
- Modify: `app/src/main/java/com/auralis/app/playback/AuralisMediaSessionService.kt`

**Interfaces:**
- Consumes: Track playback requests, skip next, seek requests
- Produces: Smooth simultaneous cross-mixing between Player A and Player B with volume interpolation, active position ticker flow

- [ ] **Step 1: Update PlaybackManager to manage dual ExoPlayer instances with crossfade volume animation**
- [ ] **Step 2: Expose currentPositionMs Flow and seekTo(positionMs) method**
- [ ] **Step 3: Integrate AudioEffectManager with active audio session ID**
- [ ] **Step 4: Commit changes**

---

### Task 7: Offline Downloader with Synced Lyrics Persistence
**Files:**
- Modify: `app/src/main/java/com/auralis/app/data/local/OfflineDownloader.kt`
- Modify: `app/src/main/java/com/auralis/app/network/NetworkMusicRepository.kt`

**Interfaces:**
- Consumes: Track to download
- Produces: Local audio file + populated `syncedLyricsJson` in Room DB

- [ ] **Step 1: Update downloadTrack to fetch lyrics via LyricsRepository if not already present**
- [ ] **Step 2: Save parsed lyrics JSON into TrackEntity during offline download**
- [ ] **Step 3: Verify offline track retrieval restores cached lyrics**
- [ ] **Step 4: Commit changes**

---

### Task 8: Superpremium Now Playing Screen (Synced Lyrics, Audio FX Sheet, Seekbar)
**Files:**
- Modify: `app/src/main/java/com/auralis/app/presentation/player/PlayerViewModel.kt`
- Modify: `app/src/main/java/com/auralis/app/presentation/player/FullPlayerScreen.kt`
- Create: `app/src/main/java/com/auralis/app/presentation/player/SoundProfilesBottomSheet.kt`
- Create: `app/src/main/java/com/auralis/app/presentation/player/SyncedLyricsView.kt`

**Interfaces:**
- Consumes: `PlayerViewModel` state (currentTrack, isPlaying, positionMs, lyrics, soundProfile)
- Produces: Live draggable seekbar, auto-scrolling synced lyrics with tap-to-seek, sound profiles modal

- [ ] **Step 1: Implement SyncedLyricsView with animated line scrolling and click-to-seek**
- [ ] **Step 2: Implement SoundProfilesBottomSheet with Bass Boost slider, Treble Boost slider, Crossfade duration slider, and 5 preset chips**
- [ ] **Step 3: Update FullPlayerScreen with lyrics toggle button, Audio FX top bar icon, and live interactive slider**
- [ ] **Step 4: Commit changes**

---

### Task 9: Home Screen Multi-Engine Search with Source Filtering
**Files:**
- Modify: `app/src/main/java/com/auralis/app/presentation/home/HomeViewModel.kt`
- Modify: `app/src/main/java/com/auralis/app/presentation/home/HomeScreen.kt`

**Interfaces:**
- Consumes: Search query and selected source filter chip
- Produces: Filtered search results across All, YouTube Music, JioSaavn 320k, Audius with audio quality badges

- [ ] **Step 1: Add sourceFilter Flow and filter chips ("All", "YouTube Music", "JioSaavn 320k", "Audius") to HomeViewModel**
- [ ] **Step 2: Update HomeScreen with horizontal filter chips row below search bar**
- [ ] **Step 3: Verify search and source filtering UX**
- [ ] **Step 4: Commit changes**

---

### Task 10: End-to-End Verification & Documentation
**Files:**
- Modify: `PROJECT_STATE.md`
- Modify: `docs/FEATURE_MATRIX.md`
- Modify: `README.md`

- [ ] **Step 1: Run unit tests and verify all pass**
- [ ] **Step 2: Verify git status and compile readiness**
- [ ] **Step 3: Update documentation to reflect new audio engine, lyrics, and YouTube Music integration**
- [ ] **Step 4: Final commit**
