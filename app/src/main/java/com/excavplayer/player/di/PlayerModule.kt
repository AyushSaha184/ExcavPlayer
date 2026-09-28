package com.excavplayer.player.di

import com.excavplayer.player.core.PlayerController
import com.excavplayer.player.core.PlayerManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PlayerModule {

    @Binds
    @Singleton
    abstract fun bindPlayerController(impl: PlayerManager): PlayerController
}
