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
import com.example.metflix.data.network.anime.models.AnimeCategoryResponse
import com.example.metflix.data.network.anime.models.AnimeEpisodeResponse
import com.example.metflix.data.network.anime.models.AnimeHomeResponse
import com.example.metflix.data.network.anime.models.AnimeMovieResponse
import com.example.metflix.data.network.anime.models.AnimeSeriesResponse
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

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

    private val seriesCache = ConcurrentHashMap<String, AnimeSeriesResponse>()
    private val categoryCache = ConcurrentHashMap<String, AnimeCategoryResponse>()
    private val movieCache = ConcurrentHashMap<String, AnimeMovieResponse>()

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

    suspend fun getHomeData(forceRefresh: Boolean = false): AnimeHomeData = withContext(Dispatchers.IO) {
        if (!forceRefresh) {
            cachedHomeData?.let { data ->
                if (!data.isEmpty) {
                    if (animeDiskCache.isCacheStale()) {
                        triggerBackgroundRefresh()
                    }
                    return@withContext data
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
                    return@withContext mapped
                }
            }
        }

        // Fetch live from Render backend
        fetchMutex.withLock {
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

    suspend fun getSeriesData(slug: String, forceRefresh: Boolean = false): Result<AnimeSeriesResponse> = withContext(Dispatchers.IO) {
        val cleanSlug = slug.trim().removePrefix("/series/").trim('/')
        if (!forceRefresh) {
            seriesCache[cleanSlug]?.let { return@withContext Result.success(it) }
        }
        val result = animeApiClient.getSeriesData(cleanSlug)
        result.getOrNull()?.let { seriesCache[cleanSlug] = it }
        result
    }

    suspend fun getCategoryData(
        categoryType: String,
        slug: String,
        page: Int = 1,
        forceRefresh: Boolean = false
    ): Result<AnimeCategoryResponse> = withContext(Dispatchers.IO) {
        val cleanSlug = slug.trim().removePrefix("/category/").removePrefix("$categoryType/").trim('/')
        val cacheKey = "${categoryType}_${cleanSlug}_p$page"
        if (!forceRefresh) {
            categoryCache[cacheKey]?.let { return@withContext Result.success(it) }
        }
        val result = animeApiClient.getCategoryData(categoryType, cleanSlug, page)
        result.getOrNull()?.let { categoryCache[cacheKey] = it }
        result
    }

    suspend fun getMovieData(slug: String, forceRefresh: Boolean = false): Result<AnimeMovieResponse> = withContext(Dispatchers.IO) {
        val cleanSlug = slug.trim().removePrefix("/movie/").trim('/')
        if (!forceRefresh) {
            movieCache[cleanSlug]?.let { return@withContext Result.success(it) }
        }
        val result = animeApiClient.getMovieData(cleanSlug)
        result.getOrNull()?.let { movieCache[cleanSlug] = it }
        result
    }

    suspend fun getEpisodeData(slug: String): Result<AnimeEpisodeResponse> = withContext(Dispatchers.IO) {
        val cleanSlug = slug.trim().removePrefix("/episode/").trim('/')
        animeApiClient.getEpisodeData(cleanSlug)
    }

    fun findMovieById(id: String): Movie? {
        val clean = id.removePrefix("tv:").trim()
        movieIndex[clean]?.let { return it }
        val slug = clean.substringAfterLast("/")
        return movieIndex[slug] ?: movieIndex[clean.lowercase()]
    }

    suspend fun getTrendingAnimeList(): List<Movie> = withContext(Dispatchers.IO) {
        val home = getHomeData()
        home.newAnimeArrivals.ifEmpty { home.newestDrops }
    }

    suspend fun getAnimeMoviesList(): List<Movie> = withContext(Dispatchers.IO) {
        val home = getHomeData()
        home.latestAnimeMovies.ifEmpty { home.mostWatchedFilms }
    }

    suspend fun getCartoonsList(): List<Movie> = withContext(Dispatchers.IO) {
        val home = getHomeData()
        home.cartoonSeries.ifEmpty { home.cartoonFilms }
    }

    suspend fun getTopRatedAnimeList(): List<Movie> = withContext(Dispatchers.IO) {
        val home = getHomeData()
        home.mostWatchedShows
    }

    suspend fun getNewestDropsList(): List<Movie> = getHomeData().newestDrops
    suspend fun getBigTitlesList(): List<Movie> = getHomeData().bigTitles
    suspend fun getNetworksList(): List<Movie> = getHomeData().networks
    suspend fun getLanguagesList(): List<Movie> = getHomeData().languages

    suspend fun clearCache() = withContext(Dispatchers.IO) {
        cachedHomeData = null
        movieIndex.clear()
        seriesCache.clear()
        categoryCache.clear()
        movieCache.clear()
        animeDiskCache.clearCache()
    }
}
