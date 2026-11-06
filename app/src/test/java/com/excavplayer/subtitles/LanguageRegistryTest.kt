package com.excavplayer.subtitles

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LanguageRegistryTest {

    @Test
    fun `every supported language display name maps to its code and back`() {
        for (language in LanguageRegistry.supported) {
            val code = LanguageRegistry.nameToCode(language.name)
            assertThat(code).isEqualTo(language.code)
            if (language.code != "all") {
                assertThat(LanguageRegistry.displayNameFor(language.code)).isEqualTo(language.name)
            }
        }
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
}

