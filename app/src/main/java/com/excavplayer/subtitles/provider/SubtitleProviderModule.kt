package com.excavplayer.subtitles.provider

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SubtitleProviderModule {
    @Provides
    @Singleton
    @IntoSet
    fun openSubtitlesV3(provider: OpenSubtitlesV3Provider): SubtitleProvider = provider

    @Provides
    @Singleton
    @IntoSet
    fun openSubtitlesRest(provider: OpenSubtitlesRestProvider): SubtitleProvider = provider

    @Provides
    @Singleton
    @IntoSet
    fun openSubtitlesWeb(provider: OpenSubtitlesWebProvider): SubtitleProvider = provider

    @Provides
    @Singleton
    @IntoSet
    fun subDl(provider: SubDlProvider): SubtitleProvider = provider

    @Provides
    @Singleton
    @IntoSet
    fun yify(provider: YifyProvider): SubtitleProvider = provider
}
