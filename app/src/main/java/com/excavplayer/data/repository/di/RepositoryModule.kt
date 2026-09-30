package com.excavplayer.data.repository.di

import com.excavplayer.data.datastore.SettingsRepositoryImpl
import com.excavplayer.data.repository.FavoritesRepositoryImpl
import com.excavplayer.data.repository.MediaSourceRepositoryImpl
import com.excavplayer.data.repository.PlaybackRepositoryImpl
import com.excavplayer.data.repository.PlaylistRepositoryImpl
import com.excavplayer.data.repository.VideoRepositoryImpl
import com.excavplayer.domain.repository.FavoritesRepository
import com.excavplayer.domain.repository.MediaSourceRepository
import com.excavplayer.domain.repository.PlaybackRepository
import com.excavplayer.domain.repository.PlaylistRepository
import com.excavplayer.domain.repository.SettingsRepository
import com.excavplayer.domain.repository.VideoRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindVideoRepository(impl: VideoRepositoryImpl): VideoRepository

    @Binds
    @Singleton
    abstract fun bindPlaybackRepository(impl: PlaybackRepositoryImpl): PlaybackRepository

    @Binds
    @Singleton
    abstract fun bindFavoritesRepository(impl: FavoritesRepositoryImpl): FavoritesRepository

    @Binds
    @Singleton
    abstract fun bindPlaylistRepository(impl: PlaylistRepositoryImpl): PlaylistRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindMediaSourceRepository(impl: MediaSourceRepositoryImpl): MediaSourceRepository
}
