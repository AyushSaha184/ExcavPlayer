# ExcavPlayer - Production-Grade Android Video Player Core

ExcavPlayer is a local-first, modular, lifecycle-safe backend and core architecture for a modern Android video player application. It is engineered to be completely decoupled from the UI layer, enabling full Jetpack Compose (or Views) frontends to be attached seamlessly without modifying playback or library management logic.

---

## 1. Architectural Overview

ExcavPlayer follows Clean Layered Architecture:

```
UI / Presentation (Jetpack Compose / Activities / ViewModels)
               ↓
    Domain Layer (Use Cases & Models)
               ↓
    Repository Interfaces
               ↓
    Data Layer (Room, DataStore, MediaStore, SAF)
               ↓
    Player Engine (AndroidX Media3 / ExoPlayer & MediaSession)
```

### Core Principles
- **UI Independence**: The playback engine, queue manager, and database never reference UI widgets, Compose states, or Activities.
- **Controlled Player Lifecycle**: A single, application-scoped `ExoPlayer` instance managed by `PlayerManager` prevents recreating decoders during screen rotations or configuration changes.
- **Reliable Persistence**: Playback positions and watch history are debounced/throttled (persisting every 3 seconds while playing, and immediately on pause, stop, backgrounding, or error) to prevent main-thread or database lockups.
- **Scoped Storage & SAF Compliance**: Content URIs (`content://`) are treated as canonical references. Storage Access Framework (SAF) enables persistent tree and document access across reboots.

---

## 2. Module & Package Structure

```
com.excavplayer
 ├── core/
 │   ├── common/           # Common utilities and extensions
 │   ├── coroutine/        # DispatcherProvider abstraction for testability
 │   ├── di/               # Global Hilt dependency injection modules
 │   ├── logging/          # AppLogger abstraction with release stripping
 │   └── result/           # ExcavResult sealed interface
 │
 ├── domain/
 │   ├── model/            # Pure Kotlin domain entities (Video, PlaybackState, etc.)
 │   ├── repository/       # Repository interfaces
 │   └── usecase/          # Encapsulated business actions (GetVideos, Playback, History, etc.)
 │
 ├── data/
 │   ├── database/         # Room Database, Entities, DAOs, Tuples, Mappers
 │   ├── datastore/        # Jetpack DataStore Preferences for UserSettings
 │   ├── media/            # MediaStore DataSource with Scoped Storage pagination
 │   └── repository/       # Concrete Repository implementations
 │
 ├── player/
 │   ├── core/             # PlayerManager, PlayerController, AudioBecomingNoisy, ErrorMapper
 │   ├── playback/         # PlaybackPersistenceManager, PipHelper
 │   ├── queue/            # PlaybackQueue with non-destructive shuffle and repeat modes
 │   ├── tracks/           # TrackManager for audio, video, and subtitle track selection
 │   └── di/               # Hilt PlayerModule
 │
 ├── media/
 │   ├── discovery/        # MediaSyncManager & MediaStore ContentObserver
 │   ├── source/           # Storage Access Framework (SAF) & MediaSourceResolver
 │   ├── metadata/         # MediaMetadataExtractor (MediaMetadataRetriever)
 │   └── thumbnail/        # Asynchronous hardware-accelerated ThumbnailLoader with LruCache
 │
 ├── library/              # VideoLibrary, HistoryManager, PlaylistManager UI facades
 ├── settings/             # SettingsManager facade
 ├── service/              # MediaSession PlaybackService for lockscreen/notification controls
 ├── background/           # WorkManager MediaSyncWorker for battery-aware sync
 └── MainActivity.kt       # Verification & debug test interface
```

---

## 3. Data Flow & Lifecycle Management

### Player Lifecycle
1. **Activity Rotation / Configuration Change**:
   - `MainActivity.onStop()` detaches `PlayerView.player = null` if not entering Picture-in-Picture.
   - The underlying `ExoPlayer` instance in `PlayerManager` keeps playing without interruption.
   - `MainActivity.onStart()` reattaches `PlayerView.player = playerManager.exoPlayer`.
2. **App Backgrounding**:
   - `PlaybackService` holds a Media3 `MediaSession`.
   - Audio focus and becoming-noisy events pause playback if headphones are unplugged or Bluetooth disconnects.
   - If user exits app while playing, playback continues as a foreground media service with Android notification controls.
   - If user leaves app while paused, `PlaybackService.onTaskRemoved()` stops the foreground service to conserve memory.
3. **Process Death & Recovery**:
   - Playback progress is continuously saved to Room.
   - On app restart, `PlayerManager.play(video)` automatically queries the last position from `PlaybackRepository` and seeks smoothly.

---

## 4. Room Database Schema

The database `excav_player.db` contains 9 normalized tables:
- `videos`: Master table indexed by URI, folder path, display name, and date added.
- `playback_states`: Active playback positions, speeds, track selections (Cascade delete on video removal).
- `watch_history`: Completion stats and total watch duration.
- `favorites`: User-favorited media with foreign-key cascade integrity.
- `playlists`: User-defined playlists.
- `playlist_items`: Ordered playlist entries with composite uniqueness constraint on `(playlist_id, video_id)`.
- `subtitle_preferences`: Preferred subtitle tracks and sync delays per video.
- `media_sources`: Registered SAF trees, documents, and future network sources.
- `folders`: Cached directory aggregations.

---

## 5. Media Discovery & SAF

- **MediaStore**: Queries `MediaStore.Video.Media.EXTERNAL_CONTENT_URI` in batches of 250 using `ContentResolver.QUERY_ARG_OFFSET` / `LIMIT` on Android 8.0+.
- **Deletion Detection**: Compares discovered MediaStore IDs against Room records. Media no longer present on device is marked `UNAVAILABLE` or cleaned up.
- **Storage Access Framework (SAF)**:
  - Takes persistable URI permissions via `takePersistableUriPermission`.
  - Scans document trees recursively using `DocumentsContract`.
  - Re-validates access before playback.

---

## 6. Permissions

Configured dynamically per Android version:
- **Android 13+ (API 33+)**: `android.permission.READ_MEDIA_VIDEO`, `android.permission.POST_NOTIFICATIONS`
- **Android 12 and below**: `android.permission.READ_EXTERNAL_STORAGE`
- **Foreground Playback**: `android.permission.FOREGROUND_SERVICE`, `android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK` (Android 14+, API 34+)

---

## 7. How to Run the Project & Tests

### Building the Project
```bash
./gradlew assembleDebug
```

### Running Unit & Integration Tests
```bash
./gradlew test
```

### Running Android Lint
```bash
./gradlew lintDebug
```

---

## 8. Future Extension Points

1. **Network Sources**: Implement `MediaSourceType.NETWORK_FUTURE` providers (HTTP/HLS/DASH, SMB, WebDAV, FTP) using Media3's `DefaultHttpDataSource` or custom `DataSource.Factory`.
2. **Chromecast & Android Auto**: Connect Media3's `MediaSession` directly to `CastPlayer`.
3. **Jetpack Compose UI**: Consume `VideoLibrary.observeVideos()` and `PlayerController.state` using `collectAsStateWithLifecycle()`.
