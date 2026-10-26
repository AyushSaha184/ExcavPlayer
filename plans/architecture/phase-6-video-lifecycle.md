# Phase 6 — One owner for the video lifecycle

**Review card:** card 3 · **Size:** L · **Risk:** medium · **Behavior-preserving:** yes (choreography concentrates; observable behavior unchanged)

## Context

"A video was added, changed, or removed" has no owner:

- **Deletion choreography** spans 8 modules with ~7 hand-sync points: `FoldersScreen` menu → `ExcavApp` passes `library.folderVideosMap` as an argument (`ExcavApp.kt:216`) → `vm.deleteFolders` prefix-matches against `libraryState.videos` with `normalizeFolderPath` (`UiState.kt:588–602`) → `vm.deleteVideos` classifies by URI scheme + SDK + sourceType (`UiState.kt:524–536`) → MediaStore branch emits `DeleteRequestEvent` via Channel (`546–555`) → `MainActivity` collector sets `pendingDeleteIds` (`185–189`) → system dialog → launcher result (`68–75`) → `viewModel.onDeleteConfirmed` (`:72`) → repo evicts thumbnails + `videoDao.deleteVideos` (`VideoRepositoryImpl.kt:153–168`). The viewmodel's classification must hand-stay-in-sync with the repository's classifier (`VideoRepositoryImpl.kt:124–141`); `MediaSyncManager`'s missing-ID sweep (`79–86`) races the DB delete.
- **Exclusion rule**: 6 copies, unified by phase 1 — this phase consumes it.
- **Refresh ritual**: `purgeCorruptedEntries()` then `refresh(force=true)` copy-pasted 5× (VM init `305–312`, VM refresh `431–452`, MainActivity permission paths `116–133, 307–310, 358`, HomeScreen ON_RESUME `96–112`).
- **Transient intent videos** (`MainActivity.kt:279–295`) play without DB registration; correctness rests on the folder name `"External"` never being inserted — and `VideoDao` excludes `'External Videos'`, not `'External'`.

Deepening: one module owns register/refresh/delete; the system-approval shape becomes part of the delete outcome; dead alternate paths delete for free.

## Steps

### 6.0 Read-first inventory

Re-derive, by grep, the current list of sync points before coding:
`grep -rn "deleteFolders\|deleteVideos\|onDeleteConfirmed\|DeleteRequestEvent\|pendingDeleteIds\|createDeleteRequest\|purgeCorruptedEntries" app/src/main` — paste the hit list into the new test file's header as the checklist the tests must encode.

**Done when:** the checklist exists (it becomes the test matrix).

### 6.1 Define the interface

Create `domain/lifecycle/VideoLifecycle.kt` (plain Kotlin interface, no Android types except the `IntentSender`-shaped approval — model the approval as a small data holder so tests can fake it; the impl wraps Android's `IntentSender`):

- `suspend fun refresh(force: Boolean): ExcavResult<Unit>` — absorbs the purge+refresh ritual (one default implementation instead of 5 copies).
- `suspend fun deleteVideos(videos: List<Video>): DeleteOutcome`
- `suspend fun deleteFolders(folders: List<Folder>): DeleteOutcome` — computes descendants via `FolderTreeManager.getAllDescendantVideos` (this kills the viewmodel prefix-match **and** the `folderVideosMap` argument).
- `suspend fun finalizeApprovedDeletion(ids: List<String>): ExcavResult<Unit>` — post-approval DB cleanup + thumbnail eviction.
- `DeleteOutcome` sealed: approval required (with sender + ids), deleted (ids), partial (ids + failures). Plain data.

**Done when:** interface + outcome types compile; no Android imports leak into the domain file except the wrapped sender type (documented).

### 6.2 Implement it

Create `data/lifecycle/VideoLifecycleImpl.kt`. Injects: `VideoRepository`, `PlaybackRepository`, `MediaSyncManager`, `LibraryDerivation`, logger. Rules:

- The repository's delete classifier (`VideoRepositoryImpl.kt:124–141`) is promoted into the lifecycle impl — **the single classifier**; the viewmodel's copy (`UiState.kt:524–536`) deletes.
- MediaStore-scheme videos: build the system delete request and return it in the outcome (`createDeleteRequest` call moves here from the viewmodel). SAF/document-trees and file-path videos: delete immediately (contentResolver/DocumentsContract/File + thumbnail eviction + DB rows, absorbing `VideoRepositoryImpl:116–151, 153–168`).
- `finalizeApprovedDeletion` performs DB cleanup after the system dialog is approved.
- `refresh` delegates to `MediaSyncManager` (the audit found the funnel real — keep it), with the purge ritual inside.
- Transient intent policy: shared-in videos play without registration — the intent handler in MainActivity calls the lifecycle module for the decision, and `'External'` is added to the exclusion union in `LibraryDerivation` (phase 1 module gains the constant; `VideoDao` gains the matching SQL mirror comment per phase 1's convention).

**Done when:** impl compiles, DI wiring added (`RepositoryModule` or a new `LifecycleModule`), build green.

### 6.3 Rewire the choreography

- `UiState.deleteVideos` / `deleteFolders` / `onDeleteConfirmed` (~515–610) become thin delegates to the lifecycle module. The `DeleteRequestEvent` Channel and the `folderVideosMap` argument die: `MainActivity`'s collector (`185–189`) instead receives the outcome (via a small state flow the viewmodel exposes, e.g. `pendingApproval: StateFlow<DeleteOutcome.Approval?>`), launches the sender, and on approval calls `vm.onDeleteConfirmed` → `lifecycle.finalizeApprovedDeletion`. Instance-state save/restore of `pendingDeleteIds` (`:168, 397–402`) keeps working against the new shape.
- The 7 refresh callers all funnel through `vm.refreshLibrary` → `lifecycle.refresh` — the ritual copies die.

**Done when:** `grep -rn "DeleteRequestEvent\|folderVideosMap" app/src/main` = 0; `grep -rn "createDeleteRequest" app/src/main` = lifecycle impl only; build green.

### 6.4 Delete the dead alternates

Now that the owner exists: `SafDataSource` dead paths (`registerDocumentUri`, `ensureVideoSaved`, `scanAndIndexFile`) — verify with `grep -rn "registerDocumentUri\|ensureVideoSaved\|scanAndIndexFile" app/src`; if the whole class is then unreferenced, delete the file. Re-check `MediaSyncWorker` for anything orphaned.

**Done when:** ledger's SafDataSource row resolved (delete or justified keep, recorded); build green.

### 6.5 Tests

`VideoLifecycleTest` (fake repos/DAOs + Mockk; Robolectric where `ContentResolver`/`DocumentsContract` are touched). Encode the 6.0 checklist:

- MediaStore-scheme video delete → outcome approval required; `finalizeApprovedDeletion` removes DB rows; FK cascade leaves playback/dismissed rows gone.
- SAF/file video delete → immediate `Deleted` outcome; thumbnail eviction invoked.
- Folder delete → descendants computed from `FolderTreeManager` (no stale map possible); mixed-scheme folder deletes each video via the right branch.
- Transient intent video → no DB write.
- `refresh(force=true)` runs the purge ritual.
- Exclusion: refresh applies `LibraryDerivation` predicate.

**Done when:** ≥ 10 tests green, including every checklist row.

### 6.6 Gate

`./gradlew :app:testDebugUnitTest :app:assembleDebug`. Device checklist: delete a single MediaStore video (system dialog appears, confirm, library updates, resume row gone); delete a SAF folder (direct); share a video from a files app into ExcavPlayer (plays, doesn't appear in library); airplane-mode refresh doesn't crash; continue-watching dismissal sticks.

**Done when:** gate green; device checklist pass; ledger updated (delete-orchestration rows ticked).
