# Phase 5 — A real seam for subtitle providers

**Review card:** card 5 · **Size:** M · **Risk:** medium · **Behavior changes:** flagged (error surfacing, language-code fix, SUBSOURCE removal)

## Context

`SubtitleSearchService.kt` (496 lines) holds five provider implementations as private functions behind an if-ladder: `searchOpenSubtitlesV3` (:117), `searchOpenSubtitles` (:240), `searchOpenSubtitlesWeb` (:310, HTML regex), `searchSubDL` (:359), `searchYifySubs` (:413, HTML regex). Each hand-rolls `HttpURLConnection` + parsing. Every provider failure is swallowed into an empty list (:68–75), so dead network is indistinguishable from zero results and the UI's Retry button retries silence (`UiState.kt:910, 913–921`). `SUBSOURCE` sits in the `SubtitleSource` enum with no provider branch. One search traverses 7 modules with 4 error conventions; `UiState.kt:932` even calls `.getOrThrow()` on a kotlin `Result` while the library layer uses `ExcavResult`. The language vocabulary lives in 4 places (`SettingsScreen.kt:417–421` stores display names; `UiState.kt:892–895` lowercases to `"english"`; `SubtitleSearchService` uses it directly as an API param `:244, 363`; `PlayerManager.LANGUAGE_MAP` :84–109 is a separate table).

Five adapters is a real seam — it already exists conceptually; this phase expresses it.

## Steps

### 5.1 Language registry first (behavior-preserving)

Create `subtitles/LanguageRegistry.kt`: one table absorbing `PlayerManager.LANGUAGE_MAP` (84–109), `SubtitleLanguage.SUPPORTED` (27–44), and `normalizeLangCode`. API: display-name → API code, code → display name, and the track-selection aliases. `PlayerManager`'s companion functions (`getLanguageCodes`, `matchesLanguage`) delegate to it so existing callers stay untouched.

**Done when:** `grep -rn "LANGUAGE_MAP\|SubtitleLanguage.SUPPORTED" app/src/main` shows definitions only in the registry (delegating shims allowed at call sites); build green; existing tests green.

### 5.2 Fix the language-code bug

`BEHAVIOR CHANGE (bug fix)`: `UiState.kt:892–896` sends the lowercased display name (`"english"`) where an API code belongs. Convert via `LanguageRegistry.nameToCode` before calling the search; `"all"` and sentinel values pass through explicitly. Regression test: display name in → correct code out, for every supported language.

**Done when:** search requests carry real codes; regression test green; manual search with a non-English language returns plausible results.

### 5.3 Provider seam

Create `subtitles/provider/SubtitleProvider.kt`:

- Interface: `val source: SubtitleSource` + `suspend fun search(query, languageCode): List<SubtitleSearchResult>`.
- Four adapters: `OpenSubtitlesV3Provider`, `OpenSubtitlesWebProvider`, `SubDLProvider`, `YifyProvider` — each owns its HTTP shape + parse. Each provider's parse becomes a pure function `parse(body: String): List<SubtitleSearchResult>` (internal-visible for tests).
- `SubtitleSearchService` becomes the orchestrator: fan-out to selected providers (ALL = every adapter), dedup + sort (current logic at 78–83, preserved), returns a typed outcome instead of a bare list.
- Delete `SUBSOURCE` from the `SubtitleSource` enum (verify-zero-readers first).

**Done when:** provider files exist with pure parse functions; `grep -rn SUBSOURCE app/src` = 0; build green.

### 5.4 Typed outcomes

`subtitles/SubtitleSearchOutcome.kt` — sealed: `Results(list)`, `NoResults`, `NetworkError(cause)`, `ParseError(provider, cause)`. Per-provider exceptions become outcome entries instead of empty lists. `UiState.searchOnlineSubtitles` maps outcomes onto `OnlineSubtitleUiState` with an explicit error state distinct from the empty state.

**Done when:** outcome type exists, orchestrator returns it, viewmodel maps it; build green.

### 5.5 UI error surfacing

`BEHAVIOR CHANGE (UI copy)`: `PlayerScreen` online tab distinguishes "No subtitles found" (empty) from a connection failure message (new string, e.g. "Couldn't reach subtitle sites — check your connection"). Retry stays wired to the same search call — it now works because the error path is real.

**Done when:** airplane-mode search shows the connection message; normal search still shows results; both strings exist in `strings.xml`.

### 5.6 Download path cleanup

`UiState.kt:932` (`getOrThrow`) → handle the `Result<File>` failure explicitly into the subtitle UI state (failure message state, mirroring 5.4). `SubtitleDownloader` keeps its internal `runCatching`. Add the missing test to `PlayerErrorMapperTest` for the `MalformedSubtitleError` branch — completes the mapper's coverage at 8/8.

**Done when:** no `getOrThrow` on the download path; mapper fully covered; build green.

### 5.7 Tests

Fixtures under `app/src/test/resources/subtitles/`: one JSON and one HTML per provider (captured shapes, synthetic). Cases: per-provider parse (success, malformed body, empty body), dedup/sort on `SubtitleSearchService`, registry round-trips, outcome mapping, the 5.2 regression. Style: plain JUnit + Truth, fixtures read as strings.

**Done when:** all subtitle tests green; total new tests ≥ 15.

### 5.8 Gate

`./gradlew :app:testDebugUnitTest :app:assembleDebug`. Device smoke: search online subtitles (results appear), airplane mode (connection error + working Retry), download + apply a subtitle, embedded track switching, subtitle delay.

**Done when:** gate green; device smoke clean; ledger updated (SUBSOURCE row ticked).
