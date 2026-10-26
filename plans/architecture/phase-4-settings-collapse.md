# Phase 4 — Collapse the settings stack

**Review card:** card 7 · **Size:** M · **Risk:** low · **Behavior-preserving:** yes except one flagged logging change

## Context

A settings flip is hand-threaded through four layers: 25 viewmodel setters (`UiState.kt:650–825`) → 28 `SettingsManager` setters (each body is a log line + a delegate; 29 methods, 0 depth, 1 caller) → 29-method `SettingsRepository` → DataStore. Meanwhile the viewmodel already exposes `player`/`queue`/`updateManager`/`subtitleHelper` as raw vals (`UiState.kt:290–293`), and `PlayerManager.kt:70` + `PlaybackPersistenceManager.kt:23` consume `SettingsRepository` directly — two routes to the same DataStore already coexist. A dead DataStore equalizer path (5 interface methods + impl + keys, zero callers) shadows the live Room-based one. `SettingsRepositoryImpl.edit` swallows write failures silently (~281–288).

`SettingsRepository` is the one seam. Both middle layers fail the deletion test: deleting them loses only duplicate log lines.

## Steps

### 4.0 Verify-zero-readers sweep

- `grep -rn SettingsManager app/src/main` — expect 1 consumer: `ExcavViewModel` (`UiState.kt:289`) + its DI provider.
- `grep -rn "updateEqualizer" app/src/main` — expect zero callers of the DataStore equalizer surface (Room path via `EqualizerPreferenceDao` is the live one).
- `grep -rn "vm\.set|viewModel\.set" app/src/main/java/com/excavplayer/ui` — count the call sites to repoint (~25, across PlayerScreen, SettingsScreen, HomeScreen, ExcavApp).

**Done when:** counts recorded; surprises resolved.

### 4.1 Repoint the viewmodel

In `ui/UiState.kt`: replace the `settingsManager` constructor param with `SettingsRepository`; expose `val settings: SettingsRepository` alongside the existing raw vals. Delete the 25 wrapper methods (650–825).

**Done when:** `grep -rn "SettingsManager" app/src/main` = 0 references from UiState; build green (UI call sites still broken at this point — fixed next).

### 4.2 Repoint the UI call sites

Mechanical: every `vm.setFoo(x)` / `viewModel.setFoo(x)` for settings becomes `vm.settings.setFoo(x)`. Work through the grep list from 4.0. SettingsScreen may read nicer with a local `val settings = vm.settings`, but no restructuring beyond that.

**Done when:** `grep -rn "\.set[A-Z]" app/src/main/java/com/excavplayer/ui` shows no direct-to-vm settings calls; build green; SettingsScreen renders and toggles work (emulator).

### 4.3 Delete `SettingsManager`

Delete `settings/SettingsManager.kt` and its DI provider.

**Done when:** `grep -rn SettingsManager app/src` = 0; build green.

### 4.4 Delete the dead DataStore equalizer surface

From `SettingsRepository` interface (35–38), `SettingsRepositoryImpl` (261–279 + keys 65–68 + defaults if shared), remove the `updateEqualizer*` methods. The `"Balanced"`/`"0,0,0,0,0"` default strings that the live Room path also uses stay in their live homes (`PlayerStateAndCommand.kt:12`, `EqualizerPreferenceEntity.kt:20`) — dedup of those defaults is backlog material, not this phase.

**Done when:** `grep -rn "updateEqualizer" app/src` = 0; build green.

### 4.5 Surface settings write failures

`BEHAVIOR CHANGE (logging only)`: in `SettingsRepositoryImpl.edit`, the swallowed `runCatching` (~281–288) gains an error-level log including the failed key. API shape unchanged.

**Done when:** a failing write logs; build green.

### 4.6 First viewmodel-adjacent test seam

Add `FakeSettingsRepository` (in-memory map) to test sources, matching the `SettingsRepository` interface. Optional but recommended: one smoke test constructing `ExcavViewModel` with fakes and asserting a settings change round-trips. If construction proves heavier than expected (8 params), skip the VM test — phase 6 leans on the fake.

**Done when:** fake exists; optional test attempted (result recorded).

### 4.7 Gate

`./gradlew :app:testDebugUnitTest :app:assembleDebug`. Manual smoke: change several settings, force-kill the app, relaunch — every setting persists; equalizer per-video state still works from the Room path.

**Done when:** gate green; smoke clean; ledger updated (SettingsManager + dead equalizer rows ticked).
