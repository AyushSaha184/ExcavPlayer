# Phase 0 — Foundations: glossary seed + free deletions

**Review card:** free-deletions table · **Size:** S · **Risk:** low · **Behavior-preserving:** yes

## Context

The repo has no `GLOSSARY.md` and no ADRs. It also carries ~250 lines of code with zero production callers — including one module whose test passes while testing code the app never runs. This phase seeds the domain language and removes everything that is dead on its own, independent of the later refactors.

Every deletion below was verified during the audit at commit `93f902f8`. Rule: **verify-zero-readers first** — run the grep, and if a caller appears that the audit missed, stop and reassess.

## Steps

### 0.1 Seed `GLOSSARY.md`

Create `GLOSSARY.md` at the repo root, in the domain-modeling format (opinionated, implementation-free, one–two sentences per term, `_Avoid_` lines). Seed terms, taken from the code's own names:

- **Video** — a playable media file the app knows about, local or shared in from outside. _Avoid_: item, media file (when you mean this exact concept)
- **Folder** — a directory of Videos as shown in the library, including special folders that never contain Videos. _Avoid_: directory, path
- **MediaSource** — where Videos come from (local MediaStore, SAF document tree, future network). _Avoid_: provider (that word belongs to subtitles)
- **Playback state** — the persisted facts about watching one Video: resume position, completion, track choices. _Avoid_: playback record
- **Resume position** — where a Video should reopen after a previous watch. _Avoid_: last position, progress
- **Chapter** — a marked segment of a Video with a type: regular, intro, recap, preview, outro. _Avoid_: bookmark, cue
- **Series grouping** — the library's clustering of Videos that share a cleaned series title and season. _Avoid_: collection, playlist (playlists were removed)
- **Exclusion rule** — the rule deciding which folders/Videos never appear in the library: trash folders, SAF Files, private/external sources. _Avoid_: filter, hidden
- **Subtitle provider** — an online source of downloadable subtitles (OpenSubtitles, SubDL, Yify). _Avoid_: source (overloaded with MediaSource), site
- **Equalizer preset** — a named audio effect configuration applied per video. _Avoid_: profile

**Done when:** `GLOSSARY.md` exists with these terms and reads as pure domain language.

### 0.2 Delete `FolderStateManager` and its test

- Delete `app/src/main/java/com/excavplayer/ui/folders/logic/FolderStateManager.kt` and `app/src/test/java/com/excavplayer/folders/FolderStateManagerTest.kt`.
- Verify first: `grep -rn FolderStateManager app/src/main` — expect only its own file. Its navigation/selection state lives (correctly) in `ExcavViewModel` (`selectedFolder`, `UiState.kt:316`) and screen-local state.

**Done when:** both files gone, build green.

### 0.3 Delete the domain use cases

- Files: `domain/usecase/GetVideosUseCase.kt`, `domain/usecase/PlaybackUseCases.kt` (contains `GetPlaybackStateUseCase`, `GetContinueWatchingUseCase`, `SavePlaybackStateUseCase`) — locate by symbol if names differ.
- Verify: `grep -rn "GetVideosUseCase\|GetPlaybackStateUseCase\|GetContinueWatchingUseCase\|SavePlaybackStateUseCase" app/src/main app/src/test` — expect only their own files. Remove any DI provider/binding that references them.

**Done when:** files gone, `grep -rn "UseCase" app/src/main` returns 0, build green.

### 0.4 Delete `MediaStoreObserver`

- `app/src/main/java/com/excavplayer/media/...` — locate by symbol. `observeChanges()` has zero callers.
- Verify: `grep -rn MediaStoreObserver app/src/main` — if the only reference is its own file + registration, delete the whole file and its injections.

**Done when:** file gone, build green.

### 0.5 Delete `MediaSourceRepository` + Impl + binding

- `domain/repository/MediaSourceRepository.kt`, `data/repository/MediaSourceRepositoryImpl.kt`, its binding in `data/repository/di/RepositoryModule.kt`.
- Verify: `grep -rn "MediaSourceRepository" app/src/main` — expect definitions and DI only. The domain enums `MediaSourceType` / `MediaAvailability` are live and **stay**.

**Done when:** repo + impl + binding gone, build green.

### 0.6 Delete dead `SubtitleHelper` preference facade

- `SubtitleHelper.savePreference` and `getPreference` (`subtitles/SubtitleHelper.kt`, ~140–158) — zero callers. PlayerManager talks to `SubtitlePreferenceDao` directly (addressed properly in phase 6).

**Done when:** methods gone, build green.

### 0.7 Delete `MediaSyncWorker.enqueueOneTimeSync`

- Never called (`ExcavPlayerApp` only schedules periodic sync). Verify: `grep -rn enqueueOneTimeSync app/src`.

**Done when:** method gone, build green.

### 0.8 Remove the unused `SafDataSource` injection in `MainActivity`

- `MainActivity.kt:55` injects `SafDataSource` and never uses it. Remove the constructor param and any property.
- **Do not delete the `SafDataSource` file itself** — phase 6 decides its fate after the lifecycle module exists.

**Done when:** param gone, build green.

### 0.9 Drop the `folders` table

`FolderEntity` / `folders` is registered in `ExcavDatabase.kt` but has no DAO and is never written.

1. Remove `FolderEntity::class` from the `@Database(entities = [...])` list and its import.
2. Delete `data/database/entity/SourceAndFolderEntities.kt`'s `FolderEntity` (keep `MediaSourceEntity` in that file — it is live).
3. Bump `ExcavDatabase` `version = 6` → `7` and add `MIGRATION_6_7` executing `DROP TABLE IF EXISTS folders`, appended to `addMigrations(...)`.
4. Check `RoomDatabaseTest` and the generated schema JSON (`app/schemas/`) for assertions on the folders table; update the schema export as KSP regenerates it.

**Done when:** `grep -rn FolderEntity app/src/main` = 0, DB version 7 with migration in place, build green, Room tests green.

### 0.10 Gate

`./gradlew :app:testDebugUnitTest :app:assembleDebug` from repo root. Tick every row of this phase in the README ledger.

**Done when:** gate green, ledger updated.
