# Phase 1 — Deepen library derivation into a pure, tested module

**Review card:** card 4 · **Size:** M · **Risk:** low · **Behavior-preserving:** yes (pure code motion + unification)

## Context

The app's most heuristic-heavy logic is private inside `ui/UiState.kt` (992 lines, ~71% viewmodel) with zero tests: `cleanSeriesTitle` (five regex strategies live in `extractSeriesSeason`), `computeVideoGroups`, `computeFolderVideosMap`, and `normalizeFolderPath`. The last is near-duplicated by `FolderTreeManager.normalizePath` — and only the FolderTreeManager copy is tested. The exclusion rule (trash folders / "SAF Files" / private sources) is hand-copied in SQL, Kotlin, and UI; the newest commit (`93f902f8`) touched 4 files to express it once.

Deepening: one pure module owns derivation. The viewmodel's interface shrinks; the tested copy becomes the running copy.

## Steps

### 1.0 Enumerate the exclusion copies first

Before writing code, read every site that expresses the exclusion rule and write the union of conditions into the module docstring:

1. `data/database/dao/VideoDao.kt:13–37, 40–65, 122–146, 196–217` (SQL: `folder_name != 'SAF Files'`, `folder_path != 'SAF Files'`, `folder_path NOT LIKE '%SAF Files%'`, plus trash/private conditions from commit `93f902f8`)
2. `ui/folders/logic/FolderTreeManager.kt:90–117` (Kotlin: equals + contains on name and path)
3. `media/discovery/StorageFileScanner.kt:32–40, 142–144`
4. `data/media/MediaStoreDataSource.kt:205, 229–231`
5. `ui/navigation/ExcavApp.kt:106–109` (UI copy)
6. `ui/UiState.kt:87–101` (path normalization feeding the comparison)

Note any copy whose conditions differ (e.g. `'External Videos'` excluded in DAO but `"External"` only handled in MainActivity's transient path). Divergences go into the union test matrix, not into silence.

**Done when:** a written condition table exists in the new module's docstring, covering every copy.

### 1.1 Create the module

Create `app/src/main/java/com/excavplayer/domain/derivation/LibraryDerivation.kt` — a pure object, zero Android imports. Move in:

- `normalizeFolderPath` — canonical version starts from `FolderTreeManager.normalizePath` (tested: `FolderTreeManagerTest:36–51, 155–160`), then reconcile differences with the `UiState.kt:87–101` copy (`/sdcard` → `/storage/emulated/0`, "Internal Storage" special case). Both behaviors preserved; each gets a test.
- `isExcludedFromLibrary(folderName: String, folderPath: String): Boolean` — the union predicate from 1.0. Public.
- `cleanSeriesTitle` (UiState 118–152), `extractSeriesSeason` (154–224), `computeVideoGroups` (226–260), `computeFolderVideosMap` (262–273) — moved as-is, made public.
- A single memoization entry point: move the identity-cache currently inside the viewmodel combine block (`UiState.kt:339–377`, `cachedVideosRef`) into the module so `computeVideoGroups` memoizes last-input → result in one testable place.

`UiState.kt` keeps `LibraryUiState` / `OnlineSubtitleUiState` (UI state shapes are viewmodel territory; derivation is not).

**Done when:** module exists, compiles, no Android imports.

### 1.2 Tests before rewiring

Create `app/src/test/java/com/excavplayer/derivation/LibraryDerivationTest.kt`. Follow the `FolderTreeManagerTest` style (plain JUnit + Truth). Required cases:

- Series extraction: one test per regex strategy in `extractSeriesSeason`, plus no-match and multi-strategy-conflict cases.
- `cleanSeriesTitle`: noise tags, generic folder names, empty input.
- Exclusion matrix: trash variants, "SAF Files" (name match, path match, substring), private/external, and a normal video → included. Every row of the 1.0 table.
- Normalization: `/sdcard` form, `/storage/emulated/0` form, "Internal Storage" form, trailing-slash, root.
- Grouping: empty list, single video, mixed series, unsorted input.
- Memoization: same input twice → same output, cache keyed on list identity.

**Done when:** ≥ 20 tests, all green. These tests exist *before* rewiring so later steps fail loudly if they break behavior.

### 1.3 Rewire the viewmodel

In `ui/UiState.kt`: delete the moved private functions and `normalizeFolderPath`; the `combine` block (~345–400) calls `LibraryDerivation`. The viewmodel loses the identity-cache mutation — it collects the memoized call's result.

**Done when:** `grep -rn "cleanSeriesTitle\|extractSeriesSeason\|computeVideoGroups" app/src/main` shows definitions only in `LibraryDerivation.kt`; UiState.kt shrinks by ~200 lines; build green.

### 1.4 Rewire the other consumers

- `FolderTreeManager`: `normalizePath` delegates to `LibraryDerivation.normalizeFolderPath`; the filter at 90–117 calls `isExcludedFromLibrary`. `FolderTreeManagerTest` stays green; add 1–2 equivalence tests proving the delegation.
- `StorageFileScanner` (32–40, 142–144) and `MediaStoreDataSource` (205, 229–231): swap inline checks for the predicate.
- `ExcavApp.kt:106–109`: UI copy → `LibraryDerivation.isExcludedFromLibrary`.
- `LibraryComponents.kt:72` imports `normalizeFolderPath` → repoint to the module.

**Done when:** `grep -rn "SAF Files" app/src/main/java --include=*.kt` shows the string only in `LibraryDerivation.kt` and the `VideoDao` SQL mirrors; build green.

### 1.5 The SQL mirrors

Room SQL cannot call Kotlin, so the 4 SQL copies **stay** — but they stop being unguarded duplicates:

1. Add a comment above each SQL exclusion block: `-- mirrors LibraryDerivation.isExcludedFromLibrary — update both together`.
2. Extend `RoomDatabaseTest` with the same exclusion matrix as 1.2 (insert a trash-folder row, a "SAF Files" row, a normal row; assert the DAO queries exclude exactly the first two). This closes the audit's finding that the SQL copies had zero coverage.

**Done when:** all 4 SQL sites carry the mirror comment; `RoomDatabaseTest` covers the exclusion predicate; all tests green.

### 1.6 Gate

`./gradlew :app:testDebugUnitTest :app:assembleDebug`. Manual smoke (emulator): library groups render, folder tree navigation unchanged, no trash/SAF folders leak in.

**Done when:** gate green; smoke clean; README ledger updated.
