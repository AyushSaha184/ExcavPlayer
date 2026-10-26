package com.excavplayer.subtitles

import com.excavplayer.core.coroutine.DispatcherProvider
import com.excavplayer.core.logging.AppLogger
import com.excavplayer.subtitles.provider.OpenSubtitlesRestProvider
import com.excavplayer.subtitles.provider.OpenSubtitlesWebProvider
import com.excavplayer.subtitles.provider.SubDlProvider
import com.excavplayer.subtitles.provider.SubtitleProvider
import com.excavplayer.subtitles.provider.YifyProvider
import com.excavplayer.subtitles.provider.parseCinemeta
import com.excavplayer.subtitles.provider.parseImdbSuggest
import com.excavplayer.subtitles.provider.parseOpenSubtitlesHtml
import com.excavplayer.subtitles.provider.parseOpenSubtitlesRest
import com.excavplayer.subtitles.provider.parseStremioSubtitles
import com.excavplayer.subtitles.provider.parseSubDl
import com.excavplayer.subtitles.provider.parseYifyHtml
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException
import java.net.UnknownHostException

private fun fixture(name: String): String =
    SubtitleProvidersTest::class.java.classLoader.getResource("subtitles/$name")!!.readText()

private class RecordingLogger : AppLogger {
    override fun d(tag: String, message: String) {}
    override fun i(tag: String, message: String) {}
    override fun w(tag: String, message: String, throwable: Throwable?) {}
    override fun e(tag: String, message: String, throwable: Throwable?) {}
}

private class TestDispatcherProvider : DispatcherProvider {
    override val main: CoroutineDispatcher = Dispatchers.Unconfined
    override val io: CoroutineDispatcher = Dispatchers.Unconfined
    override val default: CoroutineDispatcher = Dispatchers.Unconfined
    override val unconfined: CoroutineDispatcher = Dispatchers.Unconfined
}

/** Deterministic fake provider for orchestrator tests. */
private class FakeProvider(
    override val source: SubtitleSource,
    private val result: List<SubtitleSearchResult>,
    private val failure: Throwable? = null
) : SubtitleProvider {
    override suspend fun search(query: String, languageCode: String): List<SubtitleSearchResult> {
        failure?.let { throw it }
        return result
    }
}

class SubtitleProvidersTest {

    // ── Provider parses: OpenSubtitles REST ──────────────────────────

    @Test
    fun `openSubtitlesRest parse reads fixtures into results`() {
        val results = parseOpenSubtitlesRest(fixture("opensubtitles_rest.json"), "Movie", "en")
        assertThat(results).hasSize(2)
        assertThat(results[0].id).isEqualTo("os_555001")
        assertThat(results[0].language).isEqualTo("English")
        assertThat(results[0].downloads).isEqualTo(1500)
        assertThat(results[1].languageCode).isEqualTo("en")
    }

    @Test
    fun `openSubtitlesRest parse throws on malformed body instead of swallowing`() {
        assertThrows(SerializationException::class.java) {
            parseOpenSubtitlesRest("{not json", "Movie", "en")
        }
    }

    @Test
    fun `openSubtitlesRest parse drops entries without a file id`() {
        // Third fixture entry has no files -> no file_id -> filtered
        val results = parseOpenSubtitlesRest(fixture("opensubtitles_rest.json"), "Movie", "en")
        assertThat(results.none { it.title.contains("NoFiles") }).isTrue()
    }

    @Test
    fun `openSubtitlesHtml parse reads download links`() {
        val results = parseOpenSubtitlesHtml(fixture("opensubtitles_web.html"), "en")
        assertThat(results).hasSize(2)
        assertThat(results[0].id).isEqualTo("os_web_7712345")
        assertThat(results[0].downloadUrl)
            .isEqualTo("https://www.opensubtitles.org/en/subtitleserve/sub/7712345")
        // Tags stripped from title
        assertThat(results[1].title).isEqualTo("Movie.2024.HDRipXviD")
    }

    @Test
    fun `openSubtitlesHtml parse filters blank titles and yields none on unrelated html`() {
        assertThat(parseOpenSubtitlesHtml("<html><p>nothing here</p></html>", "en")).isEmpty()
        assertThat(parseOpenSubtitlesHtml(fixture("opensubtitles_web.html"), "en")
            .none { it.title.isBlank() }).isTrue()
    }

    @Test
    fun `subDl parse reads releases and absolutizes relative urls`() {
        val results = parseSubDl(fixture("subdl.json"), "Movie", "en")
        assertThat(results).hasSize(2)
        assertThat(results[0].downloadUrl).isEqualTo("https://dl.subdl.com/file/555001/movie.srt")
        assertThat(results[1].downloadUrl).isEqualTo("https://dl.subdl.com/file/555002/movie-ja.srt")
        assertThat(results[0].language).isEqualTo("English")
    }

    @Test
    fun `subDl parse throws on malformed body`() {
        assertThrows(SerializationException::class.java) {
            parseSubDl("{not json", "Movie", "en")
        }
    }

    @Test
    fun `yify parse reads download links`() {
        val results = parseYifyHtml(fixture("yify.html"), "en")
        assertThat(results).hasSize(2)
        assertThat(results[0].downloadUrl).isEqualTo("https://yts-subs.com/subtitles/movie-2024-english-yify")
    }

    @Test
    fun `yify parse yields none on unrelated html`() {
        assertThat(parseYifyHtml("<html></html>", "en")).isEmpty()
    }

    // ── OpenSubtitles v3 (Stremio gateway) parses ────────────────────

    @Test
    fun `stremio parse maps languages through the registry`() {
        val results = parseStremioSubtitles(fixture("stremio_subtitles.json"), "all", "Movie", "Movie.2024")
        assertThat(results).hasSize(3)
        assertThat(results[0].languageCode).isEqualTo("en")
        assertThat(results[0].language).isEqualTo("English")
        assertThat(results[1].languageCode).isEqualTo("ja")
        // Missing subtitleFileName falls back to movieReleaseName, then "<query>.srt"
        assertThat(results[2].fileName).isEqualTo("Movie.srt")
    }

    @Test
    fun `stremio parse filters by requested language code`() {
        val results = parseStremioSubtitles(fixture("stremio_subtitles.json"), "ja", "Movie", "Movie.2024")
        assertThat(results.map { it.languageCode }).containsExactly("ja")
    }

    @Test
    fun `imdb suggest and cinemeta extract ids`() {
        val (id, qType) = parseImdbSuggest("""{"d":[{"id":"tt123","q":"feature"}]}""")!!
        assertThat(id).isEqualTo("tt123")
        assertThat(qType).isEqualTo("feature")

        assertThat(parseCinemeta("""{"metas":[{"id":"tt456"}]}""")).isEqualTo("tt456")
        assertThat(parseImdbSuggest(null)).isNull()
        assertThat(parseCinemeta(null)).isNull()
    }

    // ── LanguageRegistry round trips (5.2 regression) ────────────────

    @Test
    fun `every supported language display name maps to its search code and back`() {
        for (language in LanguageRegistry.supported) {
            val code = LanguageRegistry.nameToCode(language.name)
            assertThat(code).isEqualTo(language.code)
            if (language.code != "all") {
                assertThat(LanguageRegistry.displayNameFor(language.code)).isEqualTo(language.name)
            }
        }
        // The 5.2 regression: display names no longer leak into API params
        assertThat(LanguageRegistry.nameToCode("English")).isEqualTo("en")
        assertThat(LanguageRegistry.nameToCode("Japanese")).isEqualTo("ja")
        assertThat(LanguageRegistry.nameToCode("Vietnamese")).isEqualTo("vi")
        assertThat(LanguageRegistry.nameToCode("All")).isEqualTo("all")
    }

    @Test
    fun `registry normalizes iso codes and unknown names`() {
        assertThat(LanguageRegistry.normalizeLangCode("eng")).isEqualTo("en")
        assertThat(LanguageRegistry.normalizeLangCode("zho")).isEqualTo("zh")
        assertThat(LanguageRegistry.normalizeLangCode("fra")).isEqualTo("fr")
        assertThat(LanguageRegistry.displayNameForAny("jpn")).isEqualTo("Japanese")
        assertThat(LanguageRegistry.displayNameForAny("xx")).isEqualTo("XX")
        assertThat(LanguageRegistry.nameToCode("Klingon")).isNull()
    }

    // ── Orchestrator outcomes ────────────────────────────────────────

    private fun service(vararg providers: SubtitleProvider): SubtitleSearchService =
        SubtitleSearchService(
            TestDispatcherProvider(),
            RecordingLogger(),
            providers.toSet(),
            OpenSubtitlesRestProvider()
        )

    @Test
    fun `results win even when other providers fail`() = runTest {
        val svc = service(
            FakeProvider(SubtitleSource.OPENSUBTITLES, listOf(subtitle("hit"))),
            FakeProvider(SubtitleSource.SUBDL, emptyList(), failure = UnknownHostException("subdl"))
        )
        val outcome = svc.searchSubtitles("Movie", "en")
        assertThat(outcome).isInstanceOf(SubtitleSearchOutcome.Results::class.java)
        assertThat((outcome as SubtitleSearchOutcome.Results).items).hasSize(1)
    }

    @Test
    fun `all providers unreachable maps to NetworkError`() = runTest {
        val svc = service(
            FakeProvider(SubtitleSource.OPENSUBTITLES, emptyList(), failure = UnknownHostException("no dns")),
            FakeProvider(SubtitleSource.YIFY, emptyList(), failure = IOException("timeout"))
        )
        val outcome = svc.searchSubtitles("Movie", "en")
        assertThat(outcome).isInstanceOf(SubtitleSearchOutcome.NetworkError::class.java)
    }

    @Test
    fun `all providers failing non-network maps to ParseError`() = runTest {
        val svc = service(
            FakeProvider(SubtitleSource.OPENSUBTITLES, emptyList(), failure = SerializationException("junk")),
            FakeProvider(SubtitleSource.SUBDL, emptyList(), failure = IllegalStateException("bad state"))
        )
        val outcome = svc.searchSubtitles("Movie", "en")
        assertThat(outcome).isInstanceOf(SubtitleSearchOutcome.ParseError::class.java)
        assertThat((outcome as SubtitleSearchOutcome.ParseError).messages).hasSize(2)
    }

    @Test
    fun `all providers answering empty maps to NoResults`() = runTest {
        val svc = service(
            FakeProvider(SubtitleSource.OPENSUBTITLES, emptyList()),
            FakeProvider(SubtitleSource.YIFY, emptyList())
        )
        assertThat(svc.searchSubtitles("Movie", "en")).isEqualTo(SubtitleSearchOutcome.NoResults)
    }

    @Test
    fun `blank query short-circuits to NoResults`() = runTest {
        val svc = service(FakeProvider(SubtitleSource.ALL, listOf(subtitle("ignored"))))
        assertThat(svc.searchSubtitles("   ", "en")).isEqualTo(SubtitleSearchOutcome.NoResults)
    }

    @Test
    fun `dedup removes identical entries across providers`() = runTest {
        val dupe = subtitle("same")
        val svc = service(
            FakeProvider(SubtitleSource.OPENSUBTITLES, listOf(dupe, dupe)),
            FakeProvider(SubtitleSource.SUBDL, listOf(dupe.copy(source = "SubDL")))
        )
        val outcome = svc.searchSubtitles("Movie", "en") as SubtitleSearchOutcome.Results
        assertThat(outcome.items).hasSize(2)
    }

    @Test
    fun `sort prefers requested language then download count`() = runTest {
        val enLow = subtitle("en-low", languageCode = "en", downloads = 10)
        val enHigh = subtitle("en-high", languageCode = "en", downloads = 500)
        val ja = subtitle("ja", languageCode = "ja", downloads = 9000)
        val svc = service(FakeProvider(SubtitleSource.ALL, listOf(enLow, ja, enHigh)))
        val outcome = svc.searchSubtitles("Movie", "en") as SubtitleSearchOutcome.Results
        assertThat(outcome.items.map { it.title }).containsExactly("en-high", "en-low", "ja").inOrder()
    }

    @Test
    fun `source filter selects only the requested provider`() = runTest {
        var subDlCalled = false
        val subDl = object : SubtitleProvider {
            override val source = SubtitleSource.SUBDL
            override suspend fun search(query: String, languageCode: String): List<SubtitleSearchResult> {
                subDlCalled = true
                return listOf(subtitle("from-subdl"))
            }
        }
        val svc = service(
            FakeProvider(SubtitleSource.YIFY, listOf(subtitle("from-yify"))),
            subDl
        )
        val outcome = svc.searchSubtitles("Movie", "en", SubtitleSource.YIFY) as SubtitleSearchOutcome.Results
        assertThat(outcome.items.single().title).isEqualTo("from-yify")
        assertThat(subDlCalled).isFalse()
    }

    private fun subtitle(
        title: String,
        languageCode: String = "en",
        downloads: Int? = null
    ) = SubtitleSearchResult(
        id = "id_$title",
        source = "Test",
        title = title,
        fileName = "$title.srt",
        language = "Test",
        languageCode = languageCode,
        downloadUrl = "https://example.com/$title",
        downloads = downloads
    )
}
