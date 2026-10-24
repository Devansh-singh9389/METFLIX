/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.metflix.data.util

import android.content.Context
import android.util.Log
import com.example.metflix.MetFlixApplication
import com.example.metflix.data.entities.MediaId
import com.example.metflix.data.network.resolvers.StreamResolverRepository
import com.example.metflix.presentation.common.DownloadStreamOption
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface DownloadStreamResolverEntryPoint {
    fun streamResolverRepository(): StreamResolverRepository
}

object DownloadStreamResolver {
    private const val TAG = "DownloadStreamResolver"

    /**
     * Resolves the actual available direct stream qualities for a given title/episode.
     * Strictly returns ONLY the qualities that actually exist in the resolved direct stream data.
     * Queries actual scrapers (AllDebridProvider, TorBoxClient, DualAudioScraper, AnimeScraper, AnimeApiClient).
     * Does NOT populate hardcoded standard filler qualities if they are not present.
     */
    suspend fun resolveActualStreams(
        context: Context? = null,
        title: String,
        availableLanguages: List<String> = listOf("Hindi", "English"),
        tmdbId: String? = null,
        imdbId: String? = null,
        releaseYear: String = "",
        isTvShow: Boolean = false,
        season: Int = 1,
        episode: Int = 1,
        isAnime: Boolean = false,
        animeSlug: String? = null
    ): List<DownloadStreamOption> {
        val appContext = context?.applicationContext
            ?: runCatching { MetFlixApplication.instance.applicationContext }.getOrNull()

        if (appContext == null) {
            Log.e(TAG, "Application context unavailable for stream resolution")
            return emptyList()
        }

        val repository = EntryPointAccessors.fromApplication(
            appContext,
            DownloadStreamResolverEntryPoint::class.java
        ).streamResolverRepository()

        val cleanTitle = title.trim()
        val mediaId: MediaId = when {
            isAnime -> MediaId.Anime(
                title = cleanTitle,
                seasonNumber = season,
                episodeNumber = episode
            )
            isTvShow -> MediaId.TvShow(
                tmdbId = tmdbId?.ifBlank { cleanTitle } ?: cleanTitle,
                seasonNumber = season,
                episodeNumber = episode,
                imdbId = imdbId
            )
            else -> MediaId.Movie(
                tmdbId = tmdbId?.ifBlank { cleanTitle } ?: cleanTitle,
                imdbId = imdbId
            )
        }

        return repository.resolveDownloadStreams(
            mediaId = mediaId,
            title = cleanTitle,
            releaseYear = releaseYear,
            preferredLanguages = availableLanguages,
            animeSlug = animeSlug
        )
    }
}
