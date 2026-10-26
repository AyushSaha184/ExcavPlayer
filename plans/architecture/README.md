# ExcavPlayer — architecture deepening plan

Companion to the architecture review of 2026-10-26 (`/tmp/opencode/architecture-review-2026-10-26-ExcavPlayer.html`, built from commit `93f902f8`). This plan implements all 7 review candidates plus the free deletions, as a sequence of behavior-preserving phases wherever possible.

**Repo root for every path below:** `/home/ayush/vscode/Projects/ExcavPlayer`

## Execution order

| Phase | Plan file | Review card | Size | Risk | Needs device |
|---|---|---|---|---|---|
| 0 | [phase-0-foundations.md](phase-0-foundations.md) | free deletions + GLOSSARY seed | S | low | no |
| 1 | [phase-1-library-derivation.md](phase-1-library-derivation.md) | card 4 — library derivation | M | low | no |
| 2 | [phase-2-player-interface.md](phase-2-player-interface.md) | card 1 — player interface collapse | M | low | smoke only |
| 3 | [phase-3-chapter-parser.md](phase-3-chapter-parser.md) | card 2 — one parser, two adapters | S | low | smoke only |
| 4 | [phase-4-settings-collapse.md](phase-4-settings-collapse.md) | card 7 — settings stack | M | low | smoke only |
| 5 | [phase-5-subtitle-providers.md](phase-5-subtitle-providers.md) | card 5 — subtitle provider seam | M | medium | yes |
| 6 | [phase-6-video-lifecycle.md](phase-6-video-lifecycle.md) | card 3 — video lifecycle owner | L | medium | yes |
| 7 | [phase-7-playback-clock.md](phase-7-playback-clock.md) | card 6 — one playback clock | M | high | yes |
| 8 | close-out (see bottom of this file) | glossary finalize, ADR offers | S | low | smoke only |

Why this order (differs from the report's badge order, same intent as its top recommendation):

- **Phase 1 first**: highest depth per unit of risk; it single-sources the path normalizer and the exclusion rule that phase 6's lifecycle owner will consume.
- **Phase 2 before 5**: subtitle language tables move out of `PlayerManager`; cleaner after the player interface is collapsed.
- **Phases 4 and 5 before 6**: both heavily edit `ui/UiState.kt`; batch the viewmodel surgery so the big lifecycle phase sees a smaller file.
- **Phase 7 last**: riskiest (autoplay, PiP, persistence timing all interact); everything else is settled by then.

## Global rules

1. **One phase at a time.** Finish a phase, commit it, then start the next. Commit message: `refactor: phase N — <name> (architecture deepening)`.
2. **Gate after every step**: `./gradlew :app:testDebugUnitTest` must pass; run `./gradlew :app:assembleDebug` at least once per phase before committing.
3. **Locate by symbol, not line.** All line numbers are from commit `93f902f8` and will drift as earlier phases land. Grep for the symbol names given in each step.
4. **Behavior-preserving by default.** Steps that change observable behavior are explicitly flagged `BEHAVIOR CHANGE`. UI copy changes are flagged too.
5. **Tests follow modules.** Deleting a module deletes its test file; a module that gains logic gains tests in the same phase.
6. **No drive-by features.** Anything interesting found mid-phase goes into the backlog section at the bottom of this file, not into the commit.
7. **Verify-zero-readers first.** Every deletion step starts with a grep proving the symbol has no live callers. If a step's grep finds a caller the audit missed, stop and reassess rather than force the deletion.

## Deletion ledger

Tick these off as they die. The `grep` column is the proof command (run from repo root; expect 0 hits in `app/src/main` after the phase, except where noted).

| Dead thing | Dies in | Proof after death |
|---|---|---|
| `FolderStateManager` + its test | 0 | `grep -rn FolderStateManager app/src` |
| Domain use cases (`GetVideosUseCase`, `GetPlaybackStateUseCase`, `GetContinueWatchingUseCase`, `SavePlaybackStateUseCase`) | 0 | `grep -rn "UseCase" app/src/main` |
| `MediaStoreObserver` | 0 | `grep -rn MediaStoreObserver app/src` |
| `MediaSourceRepository` + Impl + DI binding | 0 | `grep -rn "MediaSourceRepository" app/src/main` |
| `SubtitleHelper.savePreference` / `getPreference` | 0 | `grep -rn "savePreference\|getPreference" app/src/main` |
| `MediaSyncWorker.enqueueOneTimeSync` | 0 | `grep -rn enqueueOneTimeSync app/src` |
| `folders` table (`FolderEntity`) | 0 | entity list in `ExcavDatabase.kt` |
| `PlayerCommand` (22 subtypes) + `dispatch` | 2 | `grep -rn PlayerCommand app/src` |
| Dead `PlayerState` fields (`isFullscreenRequested`, `isLandscapeRequested`, `areControlsVisible`) + `SetFullscreen`/`SetControlsVisible` | 2 | same grep |
| `release()` + `Release` command (zero callers) | 2 | `grep -rn "\.release()" app/src/main` |
| Buffer-path EBML/MP4 parsers + `EbmlBufferReader` | 3 | `grep -rn "EbmlBufferReader\|parseMp4Boxes\|parseEbmlContainer" app/src` |
| `SettingsManager` (29 methods, 0 depth) | 4 | `grep -rn SettingsManager app/src` |
| Dead DataStore equalizer surface (5 interface methods + impl + keys) | 4 | `grep -rn "updateEqualizer" app/src/main` |
| `SUBSOURCE` enum value | 5 | `grep -rn SUBSOURCE app/src` |
| Viewmodel delete orchestration + `DeleteRequestEvent` channel + `folderVideosMap` arg passing | 6 | `grep -rn "DeleteRequestEvent\|folderVideosMap" app/src/main` |
| Position/track state duplication (5 and 6 stores) | 7 | per-phase greps |

## Phase 8 — close-out

1. **Glossary finalize.** Add the terms coined during phases 1–7 to `GLOSSARY.md` (library derivation, video lifecycle, playback clock, subtitle provider, exclusion rule, threshold policy, scrub math). Keep definitions implementation-free.
2. **ADR offers** (ask the user; create under `docs/adr/` only if accepted):
   - `0001-one-player-interface.md` — PlayerManager exposes one transport interface; the command channel and dead UI state are gone. Surprising without context (a command channel existed), real trade-off (uniform dispatch vs narrow surface).
   - `0002-equalizer-persists-in-room.md` — per-video equalizer state lives in Room (`equalizer_preferences`); the parallel DataStore keys were deleted. A future reader will find only one path and may "helpfully" re-add the other.
3. **Full verification**: `./gradlew :app:testDebugUnitTest :app:assembleDebug`, then the device smoke checklist from phases 5–7.
4. `graphify update .` to refresh the knowledge graph.
5. Triage the backlog section below.

## Backlog (temptations logged during execution)

_(empty — add entries as `phase N: <temptation>` when a step drags toward out-of-scope work)_
