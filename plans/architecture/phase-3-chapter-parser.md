# Phase 3 — One chapter parser, two adapters

**Review card:** card 2 · **Size:** S · **Risk:** low · **Behavior-preserving:** yes

## Context

`player/chapters/ChapterExtractor.kt` (1673 lines) implements the same container knowledge twice, hand-copied: a buffer-based EBML path (`parseEbmlContainer` ~535, `parseSegmentChildren` ~807, `parseSeekHead` ~850, `parseChaptersMaster` ~881, `parseEditionEntry` ~900, `parseChapterAtom` ~919, plus `EbmlBufferReader` ~1481–1565) and a seekable-based path (`parseEbmlSeekable` ~587 + the `...Seekable` twins ~639–804 + `EbmlSeekableReader` ~1570–1672). Same for MP4: `parseMp4Seekable` (~1002) vs `parseMp4Boxes` (~1329). Commit `6d0175b9` added the seekable copy wholesale. The 745-line test suite covers the buffer path thickly and the seekable path with ~2 tests.

`SeekableSource.kt` already provides the adapter seam: `ByteArraySeekableSource` (in-memory) and `FileChannelSeekableSource` (files). Two adapters justify the seam; the buffer variants are the deleted copy.

## Steps

### 3.1 Equivalence tests first

Add to `ChapterExtractorTest`: for each existing EBML/MP4 fixture, assert `buffer-path parse == ByteArraySeekableSource + seekable-path parse`. Run them before deleting anything — a failure here defines the exact gap to close first.

**Done when:** equivalence tests exist and pass (or the divergences they expose are fixed, each with its own test).

### 3.2 Map and reroute buffer-path callers

`grep -rn "parseEbmlContainer\|parseSegmentChildren\|parseSeekHead\|parseChaptersMaster\|parseEditionEntry\|parseChapterAtom\|parseMp4Boxes\|extractFromMetadata\|extractFromUri" app/src` — every production caller (expect: PlayerManager only, via the public entries) and every test caller. Reroute each through the seekable path: byte arrays wrap in `ByteArraySeekableSource`, URIs/channels use `FileChannelSeekableSource` (already the case for `extractFromSeekable`).

**Done when:** no production code path reaches a buffer-path function; build green.

### 3.3 Delete the buffer twins

Delete: `parseEbmlContainer`, `parseSegmentChildren`, `parseSeekHead`, `parseChaptersMaster`, `parseEditionEntry`, `parseChapterAtom`, `EbmlBufferReader`, `parseMp4Boxes`. Update `ChapterExtractorTest` cases that called buffer functions directly: same fixtures, same assertions, invoked via `ByteArraySeekableSource` + seekable functions. Keep test names so coverage intent stays visible.

**Done when:** `grep -rn "EbmlBufferReader\|parseMp4Boxes\|parseEbmlContainer" app/src` = 0; `ChapterExtractor.kt` ≤ ~1250 lines; full test suite green.

### 3.4 Gate

`./gradlew :app:testDebugUnitTest :app:assembleDebug`. Manual smoke: open a video with embedded chapters (MKV with moov-at-end if available) and one with a sidecar chapter file — chapter dots, skip pill, chapters sheet all unchanged.

**Done when:** gate green; smoke clean; ledger updated (buffer parsers row ticked).
