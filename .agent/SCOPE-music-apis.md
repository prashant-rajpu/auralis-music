# Scope: Multi-Source Music Provider Architecture for Auralis
**Date:** 2026-09-17
**Status:** DRAFT / Brainstorming Review

## Problem
Currently, Auralis integrates Deezer's public API, which is reliable for metadata but only provides 30-second audio previews. Users expect full-length track streaming, comprehensive search across global and Indian/regional catalogs, high bitrate audio (up to 320kbps), and resilient failover if one music source undergoes rate limits or API changes.

## Open Source & Reverse-Engineered Music APIs Surveyed

| Platform | Protocol / API Type | Free Full Stream? | Bitrate / Format | Auth Required? | Stability & Feasibility |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **JioSaavn** | `saavn.dev` / Unofficial REST | **Yes (Full Track)** | 320 / 160 kbps AAC/MP4 | None | **Very High** (Direct CDN stream URLs, zero auth, fast, charts + search) |
| **YouTube Music** | Innertube / Piped REST | **Yes (Full Track)** | 128-160 kbps Opus/M4A | None / Proxy instance | **High** (Massive global catalog, requires stream extraction or Piped proxy) |
| **Audius** | Open Decentralized REST | **Yes (Full Track)** | 320 kbps MP3 | None (`app_name` only) | **Very High** (Official open source, completely free, no DRM) |
| **SoundCloud** | V2 Public Client API | **Yes (Full Track)** | 128 kbps MP3/Opus | Public Client ID | **Medium** (Client ID needs extraction or rotation) |
| **Deezer** | Official Public REST | Previews (30s) | 128 kbps MP3 | None | **High** (Great charts/metadata, currently active) |
| **Spotify** | Web API / `spclient` | Metadata Only (Previews deprecated) | Ogg Vorbis (DRM protected) | Client ID + Token | **Low for Free Streams** (Full streams require Spotify Premium + Widevine/Librespot) |
| **Apple Music** | `amp-api` Web Client | Previews Only (30s) | AAC (FairPlay DRM) | Web Bearer Token | **Low for Free Streams** (Full streams protected by FairPlay DRM) |
| **Gaana / Hungama** | Reverse-Engineered REST | Full Track (Variable) | 128-320 kbps AAC | App Signatures / Headers | **Medium** (Frequent endpoint changes & geo-restrictions) |

## Kapsam İçi (In Scope)
1. **Multi-Source Repository Pattern:**
   - Define a unified `MusicSource` interface or composite repository capable of dispatching queries to multiple music backends.
2. **JioSaavn High-Quality Integration:**
   - Implementation of JioSaavn REST API (`https://saavn.dev/api/` or direct Saavn endpoints) providing:
     - Global and regional charts / trending tracks.
     - Full search capability across millions of songs.
     - Direct 320kbps and 160kbps audio URLs playing without 30s cutoffs.
3. **Fallback & Aggregation:**
   - Seamless fallback: if a stream fails or returns empty, the app falls back to alternative sources.
4. **Source Badge in UI:**
   - Display audio quality / source tag (e.g. `320kbps` or `JioSaavn` / `Deezer`) on tracks.

## Kapsam Dışı (Out of Scope for First Slice)
- Librespot C-daemon compilation (requires Spotify Premium account login and heavy native NDK binaries).
- Complex Widevine DRM decryption engines.

## Başarı Kriterleri (Success Criteria)
1. User can search or browse trending tracks and listen to the **complete full-length song** (not just 30 seconds).
2. Clean integration into existing `NetworkMusicRepository`, `MusicRepository`, and ExoPlayer `AuralisMediaSessionService`.
3. Zero crashes on empty or malformed fields (null-safe GSON DTOs).

## Seçilen Yaklaşım (Selected Approach)
- **Primary Source: JioSaavn Engine (`saavn.dev` / JioSaavn REST API)**
  - Why: Provides full-length 320kbps CD-quality audio streams without authentication or API keys, full search, album covers, and top charts.
- **Secondary Source: Deezer Engine**
  - Why: Provides global international pop charts and backup metadata.
- **Optional Hybrid Source: YouTube Music / Piped Streams**
  - For rare tracks not found on standard platforms.
