# Phase 7 — One playback clock

**Review card:** card 6 · **Size:** M · **Risk:** high (autoplay, PiP, persistence timing all interact) · **Behavior changes:** one flagged (dead mirrors removed)

## Context

Playback time is stored five ways: ExoPlayer's internal position, a 500 ms ticker flow (`PlayerManager.kt:331–353`), `_state.playback.currentPositionMs/durationMs` (synced at `:1256–1257` and via the `getLivePlaybackState` fallback chain `:534–547`), the DB's `resumePositionMs` (via `PlaybackPersistenceManager:84–86`), and the screen's scrub local (`PlayerScreen.kt:474, 1033, 1392`). The screen mixes synchronous reads and flow collection (`:2997`) from different channels. Track selection is stored six ways, one of them the composite string `"id|lang|label"` (`:1022–1028`, decoded by `parseSavedAudioTrack` :155–162). The resume-threshold policy is copy-pasted 3× (`PlayerManager.kt:375–386`, `PlaybackPersistenceManager.kt:68–74`, `UserSettings.kt:35–43`) with a divergent 4th definition — `PlaybackState.isCompleted` hardcodes 0.95 (`PlaybackState.kt:30`) — and `PlaybackCompletionTest` covers the copy that never runs. Two writers hit the playback table (`PlaybackPersistenceManager:59–88` and `PlayerManager.selectAudioTrack:1042–1055`, which also fabricates a bare `PlaybackState(currentPositionMs = 0)`). Repeat/shuffle are mirrored onto ExoPlayer (`:997–1010`) even though the queue is the real engine — except REPEAT_ONE, where ExoPlayer's internal loop is the actual mechanism.

One clock: position is a single StateFlow owned inside PlayerManager; one writer persists; one threshold policy.

## Steps

### 7.1 Threshold policy first (smallest, unblocks tests)

Create `player/policies/PlaybackThresholdPolicy.kt`: resume threshold → seek-step derivation (absorbing `UserSettings.kt:35–43`), `completedFraction` (the 0.95 constant, named), `isCompleted(positionMs, durationMs)`. Single source. Rewire: `PlayerManager` (375–386), `PlaybackPersistenceManager` (68–74), `PlaybackState.isCompleted` (uses the policy), `UserSettings` derivation (delegates). Update `PlaybackCompletionTest` to construct the policy — the tested copy becomes the running copy. Decide explicitly in the module docstring that completion fraction and resume threshold are two separate concepts (they currently collide).

**Done when:** policy module exists, all four consumers delegate, `PlaybackCompletionTest` green against the production policy; build green.

### 7.2 Position unification inside PlayerManager

- The existing `playbackPosition: StateFlow<PlaybackPosition>` becomes the only live position store. Delete the dual-write into `_state.playback.currentPositionMs/durationMs` (`:1256–1257`); `PlayerState.playback` keeps status/track/equalizer facts only.
- Simplify `getLivePlaybackState` (`:534–547`): read live values from the player, everything else from the one flow; extract the fallback selection as a pure function and test it.
- The 500 ms ticker remains the producer — it writes the one flow only.

**Done when:** `grep -rn "currentPositionMs" app/src/main/java/com/excavplayer/player` shows writes only at the flow producer and the persistence path; build green.

### 7.3 Single writer to the playback table

`PlaybackPersistenceManager` gains `saveTrackSelection(videoId, audioTrackId, subtitleTrackId)`; `PlayerManager.selectAudioTrack` (`:1042–1055`) routes through it; the fabricated bare `PlaybackState(currentPositionMs = 0)` construction dies. Verify: `grep -rn "savePlaybackState\|playbackDao\." app/src/main` — writers must be the persistence manager and the repo impl only.

**Done when:** one writer path; track persistence covered by a test on `PlaybackPersistenceManager` (fake repo/DAO); build green.

### 7.4 Scrub math extraction

Extract `player/policies/ScrubMath.kt`: accumulated scrub delta + duration + current position → target position (the logic in `PlayerScreen.kt:1029–1048, 474–546`). The screen's scrub local state stays screen-local during the gesture, but the commit-to-seek calculation is one pure, tested function — including the duration-source mismatch the audit found (screen reads `player.playbackPosition.value.durationMs` at `:1392` while the timeline composable collects the same flow again at `:2997`). Unify the screen to read position from **one** collected flow snapshot.

**Done when:** `ScrubMathTest` green (accumulation, clamping, duration edge cases); screen reads one flow; build green.

### 7.5 Remove the dead repeat mirrors

`BEHAVIOR CHANGE (dead code only)`: delete the `exoPlayer.repeatMode` / `shuffleModeEnabled` mirrors (`PlayerManager.kt:997–1010`) **except `REPEAT_ONE`** — ExoPlayer's internal loop is the real repeat-one mechanism for a single media item (STATE_ENDED never fires, so the queue path can't advance anyway). Document why in a comment. `PlaybackQueueTest` additions: REPEAT_ALL advances via the queue; REPEAT_ONE documented as ExoPlayer-side (manual test).

**Done when:** mirrors gone except REPEAT_ONE; queue tests green.

### 7.6 Gate

`./gradlew :app:testDebugUnitTest :app:assembleDebug`. Device checklist (the risk lives here, test thoroughly): scrub → release lands at the right position; double-tap seek accumulates N× the configured step; REPEAT_ONE loops the same video; REPEAT_ALL advances to the next; shuffle on; background audio keeps playing and resumes; PiP enter/exit keeps position; audio track selection persists across kill + relaunch; completion marks a video watched (continue-watching row leaves at the threshold).

**Done when:** gate green; full device checklist pass; ledger's clock row ticked.
