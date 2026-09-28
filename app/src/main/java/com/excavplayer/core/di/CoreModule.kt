package com.excavplayer.core.di

import com.excavplayer.core.coroutine.DefaultDispatcherProvider
import com.excavplayer.core.coroutine.DispatcherProvider
import com.excavplayer.core.logging.AndroidAppLogger
import com.excavplayer.core.logging.AppLogger
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CoreModule {

    @Provides
    @Singleton
    fun provideDispatcherProvider(): DispatcherProvider = DefaultDispatcherProvider()

    @Provides
    @Singleton
    fun provideAppLogger(): AppLogger = AndroidAppLogger()
}
