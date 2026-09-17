# Implementation Plan: Superpremium Multi-Source & Offline Music Engine
**Date:** 2026-09-17  
**Estimated Time:** 1-2 hours  
**Source:** [Scope Doc](.agent/SCOPE-music-apis.md)

## Summary
Transform Auralis into a superpremium music player utilizing real-time open-source & reverse-engineered music APIs:
1. **JioSaavn Engine (`api.php`):** Direct 320 kbps unencrypted CD-quality streams with embedded DES-ECB decryption (`38346591`) for global and regional hits.
2. **Audius Decentralized Engine (`discoveryprovider.audius.co`):** Full 320 kbps MP3 streams with zero authentication or rate limits.
3. **Deezer Fallback Engine (`api.deezer.com`):** International chart metadata and backup streams.
4. **Offline & Online Dual Mode:** Dedicated "Trending / Online" vs "Offline / Downloaded" tabs, one-tap local caching via Room + OkHttp, with automatic offline fallback when internet is absent.
5. **Superpremium UI:** Live search bar with debouncing, 320 kbps audio quality badge, download action buttons on each track item, and rich album artwork.

## Tech Stack Decisions
| Component | Decision | Rationale |
| :--- | :--- | :--- |
| **Stream Decryption** | Android Native `javax.crypto.Cipher` (DES/ECB/PKCS5) | Standard Android SDK cipher, zero extra dependencies, lightning-fast decryption of JioSaavn media URLs to 320 kbps MP4. |
| **Network Client** | Retrofit + OkHttp Multi-Base Client | Clean separation of JioSaavn, Audius, and Deezer REST contracts. |
| **Offline Storage** | Room DB (`TrackDao`) + Local App Storage (`context.filesDir/music/`) | Persistent storage for downloaded tracks; full offline playback supported in ExoPlayer without internet. |
| **UI Components** | Jetpack Compose + Material3 | Modern segmented tab controls ("Trending" vs "Offline"), search text field, quality badges, and download triggers. |

---

## Tasks

### 🔵 Phase 1: Models & API Contracts (Foundation)
- [ ] **T1:** Update `Track.kt` domain model to support `source` ("JioSaavn", "Audius", "Deezer", "Offline"), `qualityBadge` ("320 kbps Master", "HQ Audio"), and `isDownloaded: Boolean`. `[CHECKPOINT]`
- [ ] **T2:** Create `JioSaavnApi.kt` and `JioSaavnDecryptor.kt` with null-safe DTOs for `search.getResults` and `playlist.getDetails` + DES decryption to 320 kbps CDN URLs.
- [ ] **T3:** Create `AudiusApi.kt` with DTOs for `/v1/tracks/trending` and `/v1/tracks/search` providing 320 kbps MP3 stream redirects.
- [ ] **T4:** Update `NetworkModule.kt` to inject `JioSaavnApi`, `AudiusApi`, and `OpenSourceMusicApi` (Deezer).

### 🟡 Phase 2: Repository & Offline Caching Engine (Core)
- [ ] **T5:** Update `MusicRepository.kt` interface with `searchTracks(query)` and offline status checks.
- [ ] **T6:** Implement multi-provider fallback and aggregation logic in `NetworkMusicRepository.kt` (JioSaavn -> Audius -> Deezer -> Room Offline fallback). `[RISK][CHECKPOINT]`
- [ ] **T7:** Enhance `OfflineDownloader.kt` to track download progress and ensure downloaded tracks update Room database immediately.

### 🟢 Phase 3: Superpremium UI & Offline Experience (Presentation)
- [ ] **T8:** Update `HomeViewModel.kt` to support:
  - Online/Offline tab switching (`HomeTab.Trending` vs `HomeTab.Downloaded`).
  - Real-time search query execution with debounce.
  - One-tap track downloading directly from track cards.
- [ ] **T9:** Update `HomeScreen.kt` with:
  - Material3 search bar at the top.
  - Tab selector: "🔥 Online / Trending" and "💾 Downloaded / Offline".
  - Track cards with `320 kbps` badge and instant download button.
  - Zero-internet automatic offline mode with friendly empty state.
- [ ] **T10:** Update `FullPlayerScreen.kt` to display audio quality chip ("320 kbps Master Audio") and download status.

---

## Risk Analysis
| Risk | Probability | Impact | Mitigation |
| :--- | :--- | :--- | :--- |
| Network disconnect or offline mode on app launch | Medium | High | `NetworkMusicRepository` catches network exceptions and automatically serves cached tracks from Room `TrackDao`. |
| JioSaavn API response schema null values | Low | Medium | All DTO properties declared as nullable with default fallbacks, preventing Gson parsing crashes. |
| DES Decryption failure on unexpected strings | Low | Low | Wrapped in try/catch returning safe fallback URL or skipping broken track. |

---

## Next Steps
Proceeding with implementation of Phase 1, Phase 2, and Phase 3 systematically, verifying compilation and building the new APK.
