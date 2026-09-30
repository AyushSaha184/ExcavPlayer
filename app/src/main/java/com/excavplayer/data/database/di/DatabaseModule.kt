package com.excavplayer.data.database.di

import android.content.Context
import com.excavplayer.data.database.ExcavDatabase
import com.excavplayer.data.database.dao.FavoriteDao
import com.excavplayer.data.database.dao.MediaSourceDao
import com.excavplayer.data.database.dao.PlaybackDao
import com.excavplayer.data.database.dao.PlaylistDao
import com.excavplayer.data.database.dao.SubtitlePreferenceDao
import com.excavplayer.data.database.dao.VideoDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ExcavDatabase {
        return ExcavDatabase.buildDatabase(context)
    }

    @Provides
    fun provideVideoDao(database: ExcavDatabase): VideoDao = database.videoDao()

    @Provides
    fun providePlaybackDao(database: ExcavDatabase): PlaybackDao = database.playbackDao()

    @Provides
    fun provideFavoriteDao(database: ExcavDatabase): FavoriteDao = database.favoriteDao()

    @Provides
    fun providePlaylistDao(database: ExcavDatabase): PlaylistDao = database.playlistDao()

    @Provides
    fun provideMediaSourceDao(database: ExcavDatabase): MediaSourceDao = database.mediaSourceDao()

    @Provides
    fun provideSubtitlePreferenceDao(database: ExcavDatabase): SubtitlePreferenceDao = database.subtitlePreferenceDao()
}
