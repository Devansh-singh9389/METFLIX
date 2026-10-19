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

import com.example.metflix.data.entities.Movie
import com.example.metflix.data.network.tmdb.TmdbApiClient
import com.example.metflix.data.network.tmdb.TmdbConfig
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@Singleton
class AnimeDataSource @Inject constructor(
    private val tmdbApiClient: TmdbApiClient,
    private val diskCache: TmdbDiskCache
) {
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeRefreshes = ConcurrentHashMap<String, Boolean>()

    private fun refreshIfStale(key: String, block: suspend () -> Unit) {
        if (diskCache.isCacheStale(key)) {
            if (activeRefreshes.putIfAbsent(key, true) == null) {
                repositoryScope.launch {
                    try {
                        block()
                    } finally {
                        activeRefreshes.remove(key)
                    }
                }
            }
        }
    }

    private suspend fun fetchTwoPages(
        fetch: suspend (page: Int) -> Result<List<Movie>>
    ): List<Movie> = coroutineScope {
        val p1 = async { fetch(1).getOrNull().orEmpty() }
        val p2 = async { fetch(2).getOrNull().orEmpty() }
        (p1.await() + p2.await()).distinctBy { it.id }
    }

    private val trendingAnimeReader: CachedDataReader<Movie> = CachedDataReader {
        val cached = diskCache.loadMovies("anime_trending")
        if (cached.isNotEmpty()) {
            refreshIfStale("anime_trending") {
                fetchTrendingAnimeLive()?.let { trendingAnimeReader.update(it) }
            }
            return@CachedDataReader cached
        }
        fetchTrendingAnimeLive() ?: emptyList()
    }

    private val animeMoviesReader: CachedDataReader<Movie> = CachedDataReader {
        val cached = diskCache.loadMovies("anime_movies")
        if (cached.isNotEmpty()) {
            refreshIfStale("anime_movies") {
                fetchAnimeMoviesLive()?.let { animeMoviesReader.update(it) }
            }
            return@CachedDataReader cached
        }
        fetchAnimeMoviesLive() ?: emptyList()
    }

    private val cartoonsReader: CachedDataReader<Movie> = CachedDataReader {
        val cached = diskCache.loadMovies("anime_cartoons")
        if (cached.isNotEmpty()) {
            refreshIfStale("anime_cartoons") {
                fetchCartoonsLive()?.let { cartoonsReader.update(it) }
            }
            return@CachedDataReader cached
        }
        fetchCartoonsLive() ?: emptyList()
    }

    private val topRatedAnimeReader: CachedDataReader<Movie> = CachedDataReader {
        val cached = diskCache.loadMovies("anime_top_rated")
        if (cached.isNotEmpty()) {
            refreshIfStale("anime_top_rated") {
                fetchTopRatedAnimeLive()?.let { topRatedAnimeReader.update(it) }
            }
            return@CachedDataReader cached
        }
        fetchTopRatedAnimeLive() ?: emptyList()
    }

    private suspend fun fetchTrendingAnimeLive(): List<Movie>? {
        if (!TmdbConfig.USE_TMDB) return null
        val combined = fetchTwoPages { page -> tmdbApiClient.getTrendingAnime(page = page) }
        if (combined.isNotEmpty()) {
            diskCache.saveMovies("anime_trending", combined)
            return combined
        }
        return null
    }

    private suspend fun fetchAnimeMoviesLive(): List<Movie>? {
        if (!TmdbConfig.USE_TMDB) return null
        val combined = fetchTwoPages { page -> tmdbApiClient.getPopularAnimeMovies(page = page) }
        if (combined.isNotEmpty()) {
            diskCache.saveMovies("anime_movies", combined)
            return combined
        }
        return null
    }

    private suspend fun fetchCartoonsLive(): List<Movie>? {
        if (!TmdbConfig.USE_TMDB) return null
        val combined = fetchTwoPages { page -> tmdbApiClient.getAnimatedCartoonsAndKids(page = page) }
        if (combined.isNotEmpty()) {
            diskCache.saveMovies("anime_cartoons", combined)
            return combined
        }
        return null
    }

    private suspend fun fetchTopRatedAnimeLive(): List<Movie>? {
        if (!TmdbConfig.USE_TMDB) return null
        val combined = fetchTwoPages { page -> tmdbApiClient.getTopRatedAnime(page = page) }
        if (combined.isNotEmpty()) {
            diskCache.saveMovies("anime_top_rated", combined)
            return combined
        }
        return null
    }

    suspend fun getTrendingAnimeList(): List<Movie> = trendingAnimeReader.read()
    suspend fun getAnimeMoviesList(): List<Movie> = animeMoviesReader.read()
    suspend fun getCartoonsList(): List<Movie> = cartoonsReader.read()
    suspend fun getTopRatedAnimeList(): List<Movie> = topRatedAnimeReader.read()
}
