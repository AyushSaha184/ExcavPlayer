# ExcavPlayer

A local-first Android video player: it discovers the videos already on the device, plays them with a deep playback feature set (tracks, chapters, subtitles, equalizer, PiP), and remembers where you left off.

## Language

### Library

**Video**:
A playable media file the app knows about — discovered on local storage, or shared in from another app.
_Avoid_: item, media file (when you mean this exact concept)

**Folder**:
A directory of Videos as shown in the library, including special folders that never contain Videos.
_Avoid_: directory, path

**MediaSource**:
Where Videos come from: local MediaStore, a SAF document tree, or (future) a network location.
_Avoid_: provider (that word belongs to subtitles)

**Series grouping**:
The library's clustering of Videos that share a cleaned series title and season.
_Avoid_: collection, playlist (playlists were removed)

**Exclusion rule**:
The rule deciding which folders/Videos never appear in the library: trash folders, SAF Files, private/external sources.
_Avoid_: filter, hidden

**Library derivation**:
The pure computation that turns discovered media into the Library's shape: series grouping, folder stats, continue-watching rows.
_Avoid_: post-processing, normalization

### Playback

**Playback state**:
The persisted facts about watching one Video: resume position, completion, track choices.
_Avoid_: playback record

**Resume position**:
Where a Video should reopen after a previous watch.
_Avoid_: last position, progress

**Chapter**:
A marked segment of a Video with a type: regular, intro, recap, preview, outro.
_Avoid_: bookmark, cue

**Watched**:
A Video whose watched fraction passed the completion threshold; it leaves continue-watching.
_Avoid_: finished, played

**Video lifecycle**:
The one owner of refresh, delete, and post-approval cleanup for Videos — the module every add/change/remove flow routes through.
_Avoid_: sync manager (overloaded with media sync), delete flow

**Playback clock**:
The single live source of playback position; every reader takes one snapshot from it rather than keeping a parallel copy.
_Avoid_: position store (redundant now), timer

**Threshold policy**:
The policy separating two time concepts: completion (fixed fraction marks a Video watched) and resume (the user threshold driving the near-end reset and the seek-step size).
_Avoid_: threshold (alone — ambiguous between the two)

**Scrub math**:
The pure mapping from a scrub gesture to a committed position: drag fraction to target, origin selection, release commit.
_Avoid_: seek calculation, gesture math

### Subtitles & audio

**Subtitle provider**:
An online source of downloadable subtitles (OpenSubtitles, SubDL, Yify).
_Avoid_: source (overloaded with MediaSource), site

**Equalizer preset**:
A named audio-effect configuration applied per video.
_Avoid_: profile

**Audio track**:
One of the audio streams embedded in a Video; selection can be automatic or remembered.
_Avoid_: sound, language (language is a property of a track, not the track itself)
