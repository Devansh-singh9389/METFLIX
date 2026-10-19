package com.example.metflix.data.network.providers

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ProvidersModule {

    @Provides
    @Singleton
    fun provideStreamProviders(
        dualAudioProvider: DualAudioProvider,
        animeWorldProvider: AnimeWorldProvider,
        allDebridProvider: AllDebridProvider
    ): Set<@JvmSuppressWildcards StreamProvider> {
        return setOf(dualAudioProvider, animeWorldProvider, allDebridProvider)
    }
}
