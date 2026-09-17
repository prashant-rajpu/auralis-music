# Architecture

## Tech Stack
- **Language**: Kotlin
- **UI**: Jetpack Compose, Material 3
- **Media**: Media3 (ExoPlayer + MediaSessionService)
- **Architecture**: Clean Architecture (MVI/Unidirectional Data Flow)
- **DI**: Dagger Hilt
- **Concurrency**: Coroutines & Flow
- **Network**: Retrofit/OkHttp, Coil (Images)
- **Persistence**: Room DB, DataStore

## Modules
- `app`: DI, Navigation
- `core:ui`: Shared Compose components
- `core:network`: API clients (Subsonic/Jamendo)
- `core:database`: Room DAOs
- `core:playback`: MediaSessionService, Queue Manager
- `feature:home`: Dashboard
- `feature:library`: Local files, downloads
- `feature:player`: Fullscreen player, mini-player
- `feature:jam`: WebSocket synchronization
