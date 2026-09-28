# ExcavPlayer Architecture Guide

This document details the architectural flows, component interactions, and state machines powering the ExcavPlayer backend.

---

## 1. Media Discovery Flow

```mermaid
sequenceDiagram
    autonumber
    actor UserOrSystem as User / WorkManager
    participant SyncManager as MediaSyncManager
    participant MSDataSource as MediaStoreDataSource
    participant Resolver as ContentResolver (Android MediaStore)
    participant VideoDao as VideoDao (Room)

    UserOrSystem->>SyncManager: syncMediaStore()
    SyncManager->>VideoDao: getAllVideoIds()
    SyncManager->>MSDataSource: queryAllVideosPaged(batchSize=250)
    loop Every Page Batch
        MSDataSource->>Resolver: query(URI, projection, QUERY_ARG_OFFSET, LIMIT)
        Resolver-->>MSDataSource: Cursor
        MSDataSource-->>SyncManager: List<VideoEntity>
        SyncManager->>VideoDao: insertVideos(batch)
    end
    SyncManager->>SyncManager: diff(existingIds, discoveredIds)
    alt Missing / Deleted Videos Detected
        SyncManager->>VideoDao: markUnavailable(removedIds)
    end
    SyncManager-->>UserOrSystem: ExcavResult.Success(totalDiscovered)
```

---

## 2. Playback & State Initialization Flow

```mermaid
sequenceDiagram
    autonumber
    actor UI as Presentation Layer / UI
    participant PC as PlayerController (PlayerManager)
    participant PR as PlaybackRepository
    participant SR as SettingsRepository
    participant Exo as AndroidX Media3 ExoPlayer
    participant Noisy as AudioBecomingNoisyReceiver
    participant PPM as PlaybackPersistenceManager

    UI->>PC: play(video)
    PC->>SR: userSettings.first()
    alt autoResume Enabled
        PC->>PR: getPlaybackState(video.id)
        PR-->>PC: savedState (e.g. 45000ms)
    end
    PC->>Exo: setMediaItem(MediaItem.fromUri(video.uri))
    opt Resume Position > 0
        PC->>Exo: seekTo(savedPosition)
    end
    PC->>Exo: prepare()
    PC->>Exo: play()
    PC->>Noisy: register()
    PC->>PPM: onSessionStarted()
    PC->>PPM: startPeriodicSave(3000ms)
    PC-->>UI: StateFlow<PlayerState> emits Playing
```

---

## 3. Playback Persistence Flow

```mermaid
sequenceDiagram
    autonumber
    participant Engine as PlayerManager
    participant PPM as PlaybackPersistenceManager
    participant PR as PlaybackRepository
    participant HR as HistoryRepository
    participant Settings as SettingsRepository

    loop While isPlaying (Every 3000ms)
        PPM->>Settings: userSettings.first()
        PPM->>PPM: compute progressPercentage & completion
        opt autoResume is true
            alt isCompleted (>= threshold 95%)
                PPM->>PR: savePlaybackState(pos = 0L)
            else In Progress
                PPM->>PR: savePlaybackState(pos = currentPosition)
            end
        end
        opt historyEnabled is true && sessionTime >= 4000ms
            PPM->>HR: recordHistory(WatchHistoryEntry)
            PPM->>HR: pruneOldHistory(500)
        end
    end

    Note over Engine,PPM: On Pause / Stop / Release / App Backgrounding
    Engine->>PPM: saveImmediate(state, video)
    PPM->>PR: synchronous coroutine commit to Room
```

---

## 4. Background Playback & MediaSession Flow

```mermaid
sequenceDiagram
    autonumber
    actor System as Lockscreen / Bluetooth / Notification
    participant Service as PlaybackService (MediaSessionService)
    participant Session as MediaSession
    participant Player as ExoPlayer
    participant Noisy as AudioBecomingNoisyReceiver

    System->>Session: MediaCommand (Play / Pause / Seek / Next)
    Session->>Player: executeCommand()
    Player-->>Session: onPlaybackStateChanged()
    Session-->>System: Update Notification & MediaStyle Controls

    alt Headphones Unplugged / Bluetooth Disconnected
        Noisy->>Player: pause()
        Player-->>Session: onIsPlayingChanged(false)
        Session-->>System: Update Notification to Paused
    end

    alt User Clears App from Recents
        Service->>Service: onTaskRemoved()
        alt Player is Playing
            Service-->>Service: Keep Service alive (Foreground Playback continues)
        else Player is Paused / Stopped
            Service->>Service: stopSelf() (Release resources)
        end
    end
```

---

## 5. Storage Access Framework (SAF) Resolution Flow

```mermaid
graph TD
    A[Incoming Media URI] --> B{Scheme & Authority?}
    B -->|content://media...| C[LOCAL_MEDIASTORE]
    B -->|content://...documents...tree...| D[LOCAL_TREE]
    B -->|content://...documents...document...| E[LOCAL_DOCUMENT]
    B -->|http / https / smb / webdav| F[NETWORK_FUTURE]

    D --> G[SafDataSource.registerTreeUri]
    G --> H[Take Persistable URI Permission]
    H --> I[Recursive scanDocumentTree via DocumentsContract]
    I --> J[Batch Insert into Room Database]

    E --> K[SafDataSource.registerDocumentUri]
    K --> L[Take Persistable URI Permission]
    L --> M[Query OpenableColumns DisplayName & Size]
    M --> N[Insert VideoEntity into Room Database]
```

---

## 6. Playback Queue State Machine

```mermaid
stateDiagram-v2
    [*] --> Idle
    Idle --> InQueue: setQueue(items, startIdx)
    InQueue --> Shuffled: setShuffle(true)
    Shuffled --> InQueue: setShuffle(false)

    state InQueue {
        [*] --> CurrentItem
        CurrentItem --> NextItem: next() (RepeatMode OFF / ALL)
        CurrentItem --> PreviousItem: previous()
        CurrentItem --> CurrentItem: next() (RepeatMode ONE)
    }

    state Shuffled {
        [*] --> ShuffledItem
        ShuffledItem --> NextShuffled: next()
        Note right of Shuffled: Underlying original list order is preserved!
    }
```
