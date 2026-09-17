# Glassmorphism + Light Baby Pink Music Player Theme Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Transform Auralis Music into a dreamy, romantic, superpremium Glassmorphism + Light Baby Pink Music Player tailored for the user's girlfriend, implementing every visual token, component, and screen from the design specification without overriding any core playback engine capability.

**Architecture:** Create dedicated glassmorphism tokens, backgrounds, and frosted glass modifier extensions; implement the light baby pink color palette and typography in `Color.kt` and `Theme.kt`; overhaul `FullPlayerScreen.kt`, `MiniPlayer.kt`, `HomeScreen.kt`, `SyncedLyricsView.kt`, `SoundProfilesBottomSheet.kt`, and `SpotifyJamBottomSheet.kt` to feature frosted glass cards, soft rose borders, deep dusty rose typography, and floating glass navigation; and update status bar / system navigation bar handling for light glass aesthetics.

**Tech Stack:** Jetpack Compose, Material 3, Media3 ExoPlayer, Coil, Kotlin Coroutines & Flows, OkHttp WebSockets.

**Spec:** `/storage/emulated/0/Download/Glassmorphism_BabyPink_MusicPlayer_Theme.md`

## Global Constraints
- App Background: Linear Gradient `#FFF5F8` → `#FFE4EC` → `#FFF0F5`
- Primary Pink: `#FFB6C1`
- Soft Rose: `#FFC0CB`
- Deep Dusty Rose (Primary Text): `#5C3A4A`
- Muted Rose (Secondary Text / Icons): `#9A7A87`
- Glass Surface: `rgba(255, 255, 255, 0.55)` (`Color(0x8CFFFFFF)`)
- Glass Surface Strong: `rgba(255, 255, 255, 0.70)` (`Color(0xB3FFFFFF)`)
- Glass Border: `rgba(255, 182, 193, 0.45)` (`Color(0x73FFB6C1)`)
- White Highlight: `#FFFFFF`
- Play Button Glow: Soft radial `#FFB6C1` at 40% opacity (`Color(0x66FFB6C1)`)
- Progress Bar: Track `#FFD6E0`, Fill `#FFB6C1`, Thumb White with Pink Border
- Notification Tint: `rgba(255, 240, 245, 0.85)` (`Color(0xD9FFF0F5)`)
- System Status Bar: Light background with dark icons (`isAppearanceLightStatusBars = true`)
- Maintain all existing core engine features: Dual-ExoPlayer crossfade, AudioFX equalizer, Spotify Jam real-time sync, multi-source search (YouTube Music, JioSaavn 320k, Audius), Room offline caching, and synced lyrics.

---

### Task 1: Design System & Color Tokens (`Color.kt` & `Theme.kt`)
**Files:**
- Modify: `app/src/main/java/com/auralis/app/ui/theme/Color.kt`
- Modify: `app/src/main/java/com/auralis/app/ui/theme/Theme.kt`

**Interfaces:**
- Consumes: Compose Color library
- Produces: `BabyPinkBackgroundBrush`, `BabyPinkPrimary`, `BabyPinkSoftRose`, `BabyPinkTextPrimary`, `BabyPinkTextSecondary`, `GlassSurface`, `GlassSurfaceStrong`, `GlassBorder`, `ProgressBarTrackPink`, `PlayButtonGlowPink`

- [ ] **Step 1: Update `Color.kt` with exact Glassmorphism Baby Pink color palette**
- [ ] **Step 2: Update `Theme.kt` to use light baby pink colorScheme and configure light status bar / navigation bar with dark icons**

---

### Task 2: Frosted Glass Modifiers & Components (`GlassComponents.kt`)
**Files:**
- Create: `app/src/main/java/com/auralis/app/ui/theme/GlassComponents.kt`

**Interfaces:**
- Consumes: Compose modifier, Shape, Brush, Color tokens
- Produces: `Modifier.glassCard()`, `Modifier.glassPanel()`, `Modifier.glassPill()` helper extensions

- [ ] **Step 1: Create reusable glassmorphism modifier extensions with frosted opacity, soft rose border, and shadow**

---

### Task 3: Now Playing Screen Overhaul (`FullPlayerScreen.kt`)
**Files:**
- Modify: `app/src/main/java/com/auralis/app/presentation/player/FullPlayerScreen.kt`
- Modify: `app/src/main/java/com/auralis/app/presentation/player/SyncedLyricsView.kt`
- Modify: `app/src/main/java/com/auralis/app/presentation/player/SoundProfilesBottomSheet.kt`

**Interfaces:**
- Consumes: `PlayerViewModel`, `GlassComponents`, `Color.kt`
- Produces: Dreamy Glassmorphism Now Playing screen with soft animated pink background, frosted glass player controls, large circular glowing play button, and rose synced lyrics

- [ ] **Step 1: Replace dark player styling with soft pink gradient background, frosted glass panel, and deep dusty rose typography**
- [ ] **Step 2: Style seek bar with track `#FFD6E0`, active fill `#FFB6C1`, and white thumb with pink border**
- [ ] **Step 3: Update `SyncedLyricsView.kt` with deep dusty rose active lines and muted rose inactive lines**
- [ ] **Step 4: Update `SoundProfilesBottomSheet.kt` with frosted glass card, soft rose sliders, and baby pink chips**

---

### Task 4: Persistent Mini Player Overhaul (`MiniPlayer.kt`)
**Files:**
- Modify: `app/src/main/java/com/auralis/app/presentation/player/MiniPlayer.kt`

**Interfaces:**
- Consumes: `PlayerViewModel`, `GlassComponents`, `Color.kt`
- Produces: Floating frosted glass bar (20dp corner radius, 1.2dp soft rose border, background `Color(0xB3FFFFFF)`), soft pink progress line, and deep dusty rose controls

- [ ] **Step 1: Update `MiniPlayer.kt` container to floating glass pill with soft rose border**
- [ ] **Step 2: Update typography and progress bar to soft baby pink and deep dusty rose**

---

### Task 5: Home Screen & Navigation Overhaul (`HomeScreen.kt` & `AuralisNavGraph.kt`)
**Files:**
- Modify: `app/src/main/java/com/auralis/app/presentation/home/HomeScreen.kt`
- Modify: `app/src/main/java/com/auralis/app/presentation/navigation/AuralisNavGraph.kt`
- Modify: `app/src/main/java/com/auralis/app/presentation/jam/SpotifyJamBottomSheet.kt`

**Interfaces:**
- Consumes: `HomeViewModel`, `GlassComponents`, `Color.kt`
- Produces: Dreamy baby pink home feed with frosted glass search bar, glass category pills, frosted track cards, floating glass bottom navigation pill, and rose Spotify Jam sheet

- [ ] **Step 1: Update `HomeScreen.kt` background to baby pink gradient `#FFF5F8` → `#FFE4EC` → `#FFF0F5`**
- [ ] **Step 2: Style search bar as frosted glass pill with soft rose border and deep dusty rose text**
- [ ] **Step 3: Style Quick Picks and shelves with frosted glass cards (`Color(0x8CFFFFFF)`) and soft rose borders**
- [ ] **Step 4: Update `AuralisNavGraph.kt` bottom navigation bar to floating frosted glass pill with baby pink active indicator**
- [ ] **Step 5: Style `SpotifyJamBottomSheet.kt` with frosted glass container and soft rose/pink accents**

---

### Task 6: Build Verification, Git Commit, and CI APK Generation
**Files:**
- Repository root

**Interfaces:**
- Consumes: Git CLI, GitHub Actions API
- Produces: Passing Android CI build, generated `auralis-debug-apk` artifact, updated local APK

- [ ] **Step 1: Verify all Kotlin/Compose files compile cleanly**
- [ ] **Step 2: Commit changes to git and push to GitHub `origin main`**
- [ ] **Step 3: Monitor GitHub Actions workflow run until compilation succeeds**
- [ ] **Step 4: Download updated APK to `/storage/emulated/0/Download/auralis-debug.apk` and `/sdcard/Download/auralis-debug.apk`**
