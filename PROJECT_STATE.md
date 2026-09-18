# Project State

## Current Phase: Phase 0 - Stabilize & make releasable
**Status**: Completed

**Recent Actions**:
- Upgraded the toolchain: Gradle 9.7 wrapper, AGP 9.4 with built-in Kotlin 2.4, KSP, Compose
  BOM 2026.09 (compileSdk 37 / targetSdk 36), Media3 1.11, Room 2.8, Hilt 2.60, Java 17.
- Release builds now work: R8 keep rules for the Gson DTOs, resource shrinking, signing from
  `keystore.properties` or CI secrets, and a tag-triggered GitHub Release workflow.
- Hardened Together Mode (URL allowlist for inbound tracks, session-code validation, duplicate
  message suppression), restricted MediaSession control to trusted controllers, debug-only HTTP
  logging, per-session audio effects, local files no longer re-resolved over the network.
- Removed dead code, hid the unwired streaming-quality setting, made the haptic setting real,
  and replaced inaccurate docs and quality badges with honest ones (`docs/STATUS.md`).

**Next Phase**: Phase 1 - `play` / `plus` product flavors and the `MusicSource` abstraction.

**Blockers**:
- None.
