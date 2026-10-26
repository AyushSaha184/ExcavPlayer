# ADR 0002 — Per-video equalizer state persists in Room

**Status:** Accepted (2026-10-26, architecture deepening run, phase 4)

## Context

Equalizer state had two parallel persistence paths:

1. **Room** (`equalizer_preferences` table, `EqualizerPreferenceEntity`, per-video rows) — read and written by the live equalizer path (per-video preset/band application in `PlayerManager`).
2. **DataStore** (`updateEqualizerEnabled`, `updateEqualizerPreset`, `updateEqualizerBandLevels`, `updateBassBoostStrength` on `SettingsRepository`, plus four mirror fields on `UserSettings`) — a dead path: zero callers anywhere in the app. The settings UI's equalizer toggles write to the live Room path through the player controller, never to DataStore.

The dead path wasn't inert, just silent: it shaped the settings stack (`SettingsManager` delegation, `UserSettings` fields, DataStore keys) and made the real equalizer mechanism look like one of two legitimate options.

## Decision

The DataStore equalizer surface is deleted — interface methods, implementation methods, the four `KEY_EQUALIZER_*`/`KEY_BASS_BOOST_STRENGTH` keys, the four dead `UserSettings` fields. Room is the single home for equalizer state. Per-video equalizer settings do not appear in the app settings UI or in `UserSettings`; they are video-scoped facts applied by the player.

## Consequences

- A future reader will find exactly one equalizer persistence path. Do not "helpfully" re-add global DataStore equalizer preferences without a product decision — the equalizer is per-video by design, not a global setting.
- The `"Balanced"` / `"0,0,0,0,0"` default strings live in their two homes (`PlayerStateAndCommand`, `EqualizerPreferenceEntity`); deduplicating them is backlog, not urgent.
- If a global equalizer is ever wanted, it should be modeled as a real feature (settings entry + player application + persistence decision), not as a resurrection of the deleted keys.
