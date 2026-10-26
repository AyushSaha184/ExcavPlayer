# ADR 0001 — PlayerManager exposes one transport interface

**Status:** Accepted (2026-10-26, architecture deepening run, phase 2)

## Context

Playback intent used to reach `PlayerManager` through a `Channel<PlayerCommand>` with 22 command subtypes and a `dispatch()` switch. State flowed back out through `PlayerState`, which carried dead UI-command fields (`isFullscreenRequested`, `isLandscapeRequested`, `areControlsVisible`) that no renderer consumed. A `release()` method existed with zero callers — the player is an immortal process-lifetime singleton, so "release" could never be honest.

The channel made adding a command trivial and invisible: every new capability was a new subtype, a new dispatch branch, and (sometimes) a new dead state field, all in one file. Uniform, but opaque — nothing greppable told you who could send what.

## Decision

`PlayerManager` is consumed through one narrow transport interface, `PlayerController`: play/pause/resume/seek/repeat/shuffle, track selection, equalizer application, and a few session-facts (the MediaSession player handle, PiP marker). UI, `MainActivity`, and `PlaybackService` inject the interface directly. The command channel, `dispatch()`, `release()`, and the dead UI-command state fields are deleted.

## Trade-off

Uniform dispatch was cheap to extend; the narrow surface is cheap to audit. We chose auditability: a new player capability now means an explicit interface method with a visible caller set. The channel's other jobs (backpressure, ordering) never mattered here — commands were fire-and-forget into one consumer.

## Consequences

- Adding a capability = adding a method to `PlayerController` (compiler enforces every consumer's update).
- No command serialization: callers invoke methods directly (suspend where needed).
- Screen visibility/fullscreen state is screen-local Compose state, not player state.
- The player is never released; it lives and dies with the process. This is deliberate — do not "restore" a release path without a product reason.
