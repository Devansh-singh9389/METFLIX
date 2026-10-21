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

package com.example.metflix.data.repositories

import android.util.Log
import com.example.metflix.data.entities.AnimeHomeData
import com.example.metflix.data.entities.Movie
import com.example.metflix.data.network.anime.AnimeApiClient
import com.example.metflix.data.network.anime.models.AnimeHomeResponse
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class AnimeDataSource @Inject constructor(
    private val animeApiClient: AnimeApiClient,
    private val animeDiskCache: AnimeDiskCache
) {
    companion object {
        private const val TAG = "AnimeDataSource"
    }

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val fetchMutex = Mutex()
    private val movieIndex = ConcurrentHashMap<String, Movie>()

    @Volatile
    private var cachedHomeData: AnimeHomeData? = null

    @Volatile
    private var isRefreshing: Boolean = false

    private fun mapResponseToHomeData(response: AnimeHomeResponse): AnimeHomeData {
        val homeData = AnimeHomeData(
            bigTitles = response.bigTitlesMain.map { it.toMovie() },
            newestDrops = response.newestDrops.map { it.toMovie() },
            newAnimeArrivals = response.newAnimeArrivals.map { it.toMovie() },
            mostWatchedShows = response.mostWatchedShows.map { it.toMovie() },
            latestAnimeMovies = response.latestAnimeMovies.map { it.toMovie() },
            mostWatchedFilms = response.mostWatchedFilms.map { it.toMovie() },
            cartoonSeries = response.justInCartoonSeries.map { it.toMovie() },
            cartoonFilms = response.freshCartoonFilms.map { it.toMovie() },
            networks = response.networks.map { it.toMovie() },
            languages = response.streamInYourLanguage.map { it.toMovie() }
        )
        indexMovies(homeData)
        return homeData
    }

    private fun indexMovies(homeData: AnimeHomeData) {
        val all = homeData.getAllUniqueMovies()
        for (m in all) {
            movieIndex[m.id] = m
            val slug = m.id.substringAfterLast("/")
            if (slug.isNotEmpty() && slug != m.id) {
                movieIndex[slug] = m
            }
            val cleanName = m.name.lowercase().trim()
            if (cleanName.isNotEmpty()) {
                movieIndex[cleanName] = m
            }
        }
    }

    suspend fun getHomeData(forceRefresh: Boolean = false): AnimeHomeData {
        if (!forceRefresh) {
            cachedHomeData?.let { data ->
                if (!data.isEmpty) {
                    if (animeDiskCache.isCacheStale()) {
                        triggerBackgroundRefresh()
                    }
                    return data
                }
            }

            // Check disk cache
            val diskData = animeDiskCache.loadHomeResponse()
            if (diskData != null) {
                val mapped = mapResponseToHomeData(diskData)
                if (!mapped.isEmpty) {
                    cachedHomeData = mapped
                    if (animeDiskCache.isCacheStale()) {
                        triggerBackgroundRefresh()
                    }
                    return mapped
                }
            }
        }

        // Fetch live from Render backend
        return fetchMutex.withLock {
            if (!forceRefresh) {
                cachedHomeData?.let { if (!it.isEmpty) return@withLock it }
            }

            Log.d(TAG, "Fetching live Anime Home data from Render backend...")
            val result = animeApiClient.getHomeData()
            val response = result.getOrNull()
            if (response != null) {
                animeDiskCache.saveHomeResponse(response)
                val mapped = mapResponseToHomeData(response)
                cachedHomeData = mapped
                mapped
            } else {
                // If live fetch fails, fallback to disk cache if available
                val diskFallback = animeDiskCache.loadHomeResponse()
                if (diskFallback != null) {
                    val mapped = mapResponseToHomeData(diskFallback)
                    cachedHomeData = mapped
                    mapped
                } else {
                    cachedHomeData ?: AnimeHomeData()
                }
            }
        }
    }

    private fun triggerBackgroundRefresh() {
        if (isRefreshing) return
        isRefreshing = true
        repositoryScope.launch {
            try {
                Log.d(TAG, "Triggering background refresh for Anime Home data...")
                val result = animeApiClient.getHomeData()
                result.getOrNull()?.let { freshResponse ->
                    animeDiskCache.saveHomeResponse(freshResponse)
                    cachedHomeData = mapResponseToHomeData(freshResponse)
                    Log.d(TAG, "Background refresh completed successfully")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Background refresh failed: ${e.message}")
            } finally {
                isRefreshing = false
            }
        }
    }

    fun findMovieById(id: String): Movie? {
        val clean = id.removePrefix("tv:").trim()
        movieIndex[clean]?.let { return it }
        val slug = clean.substringAfterLast("/")
        return movieIndex[slug] ?: movieIndex[clean.lowercase()]
    }

    suspend fun getTrendingAnimeList(): List<Movie> {
        val home = getHomeData()
        return home.newAnimeArrivals.ifEmpty { home.newestDrops }
    }

    suspend fun getAnimeMoviesList(): List<Movie> {
        val home = getHomeData()
        return home.latestAnimeMovies.ifEmpty { home.mostWatchedFilms }
    }

    suspend fun getCartoonsList(): List<Movie> {
        val home = getHomeData()
        return home.cartoonSeries.ifEmpty { home.cartoonFilms }
    }

    suspend fun getTopRatedAnimeList(): List<Movie> {
        val home = getHomeData()
        return home.mostWatchedShows
    }

    suspend fun getNewestDropsList(): List<Movie> = getHomeData().newestDrops
    suspend fun getBigTitlesList(): List<Movie> = getHomeData().bigTitles
    suspend fun getNetworksList(): List<Movie> = getHomeData().networks
    suspend fun getLanguagesList(): List<Movie> = getHomeData().languages

    fun clearCache() {
        cachedHomeData = null
        movieIndex.clear()
        animeDiskCache.clearCache()
    }
}
