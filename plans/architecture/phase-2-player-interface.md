# Phase 2 — Collapse the player's dual interface

**Review card:** card 1 · **Size:** M · **Risk:** low · **Behavior-preserving:** yes except flagged UI-state deletions

## Context

`PlayerManager` (1544 lines) presents two parallel interfaces for the same operations: 20 direct methods on `PlayerController` plus 22 `PlayerCommand` subtypes whose `dispatch` maps 1:1 back to the same methods (`PlayerManager.kt:1197–1240`). Only 6 dispatch call sites exist, using 4 subtypes (`Play`, `SetScreenLocked`, `SetInPictureInPicture`, `AddExternalSubtitle`). Three `PlayerState` fields (`isFullscreenRequested`, `isLandscapeRequested`, `areControlsVisible`) are written and read by no one. `val exoPlayer` is public on the interface; `PlayerScreen.kt:606` reaches in (`vm.player.exoPlayer`), `PlaybackService.kt:45` builds a MediaSession on it. `release()` has zero callers. `PlayerController` is bound in DI (`PlayerModule.kt:15–17`) but no constructor anywhere accepts it — every consumer takes the concrete class.

Deepening: one seam, one narrow transport interface; Media3 knowledge stops leaking; UI-only toggles return to the screen.

## Steps

### 2.0 Verify-zero-readers sweep

Run and record:
- `grep -rn "isFullscreenRequested\|isLandscapeRequested\|areControlsVisible" app/src` — expect only writes in the dispatch block.
- `grep -rn "isScreenLocked\|isInPictureInPicture" app/src` — decide per field (step 2.2).
- `grep -rn "\.dispatch(" app/src` — expect the 6 audit sites.
- `grep -rn "\.release()\|PlayerCommand.Release" app/src` — expect zero callers.

**Done when:** findings recorded; surprises resolved before deletion.

### 2.1 Delete the dead UI state

- Delete `PlayerCommand.SetFullscreen`, `SetControlsVisible` (`domain/model/PlayerStateAndCommand.kt:55–56`) and their dispatch cases.
- Delete `PlayerState.isFullscreenRequested`, `isLandscapeRequested`, `areControlsVisible` (:25–27).

**Done when:** greps for those symbols return 0; build green.

### 2.2 Delete the command channel

For each remaining dispatch site, convert to a direct call:

- `UiState.kt:483, 490` and `PlayerScreen.kt:864` (`Play`) → `player.play(video, startMs)`.
- `MainActivity.kt:152` (`AddExternalSubtitle`) → `playerManager.addExternalSubtitle(uri, label, mime)`.
- `SetScreenLocked` / `SetInPictureInPicture` (PlayerScreen 909/963/979, MainActivity 372): check the 2.0 grep on `PlayerState.isScreenLocked` / `isInPictureInPicture`.
  - **Zero readers** → delete the command, the field, and the dispatch case; the screen keeps its own local state (it already does at `PlayerScreen.kt:167`).
  - **Live readers** → add a direct method (`setScreenLocked(Boolean)` / `markInPictureInPicture(Boolean)`) on PlayerManager + controller, convert the site.
- Then delete the `PlayerCommand` sealed interface, `dispatch()` from `PlayerController` and `PlayerManager`, and the mapping block (`PlayerManager.kt:1197–1240`).

**Done when:** `grep -rn "PlayerCommand" app/src` = 0; all 6 sites converted; build green.

### 2.3 Decide the interface's fate — **decision point, default: keep and narrow**

Options:
- **(a) Default — keep `PlayerController`, narrow it.** Remove `val exoPlayer: Player`; add `fun sessionPlayer(): Player` (service-only member for MediaSession construction). PlayerScreen's direct `exoPlayer` reads (`PlayerScreen.kt:606` assignment, `:633` frame-rate read) are replaced with existing `PlayerState` data or one read-only member; **no UI code mutates the raw player**. Inject `PlayerController` at every consumer: `UiState.kt` exposes `val player: PlayerController`; `PlayerScreen` composable params, `MainActivity`, `PlaybackService` all take the interface. `PlayerModule` binding stays and finally earns its existence.
- **(b) Alternative — delete `PlayerController`** and let the concrete `PlayerManager` be the interface. Cheapest, but leaves no seam for a fake player, which phases 6–7 want.

If the user's answer differs from the default, follow it; the rest of this plan assumes (a).

Also decide `release()`/`Release`: zero callers, singleton player is immortal by design. Default: **delete both** and note in `PlayerManager`'s docstring that the singleton lives for the process lifetime (process death cleans up).

**Done when:** user confirms (or default accepted); `grep -rn "val exoPlayer" app/src/main` = only the private property in `PlayerManager.kt`; `grep -rn "PlayerManager" app/src/main/java/com/excavplayer/ui` = 0 (UI sees the interface).

### 2.4 Extract two pure policies (the testability payoff)

Pull the genuinely policy-shaped private logic out of `PlayerManager` into pure modules, so tests can reach what was buried:

1. `player/policies/AutoAdvancePolicy.kt` — from the EOF-as-completion / auto-advance heuristics (`PlayerManager.kt:1462–1531`): inputs = position, duration, ended-flag, queue state, repeat mode → decision (mark completed / advance / hold). Pure.
2. `player/policies/TrackSelectionPolicy.kt` — from track auto-selection (`PlayerManager.kt:1356–1442`): inputs = available tracks, saved preference string, system language → chosen track. Pure. (`LANGUAGE_MAP` moves to the subtitle registry in phase 5; for now pass the map in as a parameter.)

`PlayerManager` calls the policies. New tests `AutoAdvancePolicyTest`, `TrackSelectionPolicyTest` covering the branches the audit listed (EOF heuristics, preference decoding via the existing `parseSavedAudioTrack` logic).

**Done when:** policies are pure, tested, and called from PlayerManager; `PlayerMuteAndAudioTrackTest` still green; build green.

### 2.5 Gate

`./gradlew :app:testDebugUnitTest :app:assembleDebug`. Manual smoke: play/pause, scrub, double-tap seek, mute, audio + subtitle track switch, speed, PiP entry/exit, background audio, screen lock. Nothing may change observably.

**Done when:** gate green; smoke clean; ledger updated (PlayerCommand, dead fields, release rows ticked).
