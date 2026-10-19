/*
 * Copyright 2023 Google LLC
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
import com.example.metflix.data.entities.toMovie
import com.example.metflix.data.network.MetFlixApiClient
import com.example.metflix.data.network.NetworkConfig
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
class TvDataSource @Inject constructor(
    private val apiClient: MetFlixApiClient,
    private val tmdbApiClient: TmdbApiClient,
    private val diskCache: TmdbDiskCache
) {
    /**
     * Application-process-lived coroutine scope dedicated to non-blocking background stale-while-revalidate
     * cache refreshes. Because [TvDataSource] is a [@Singleton], this scope intentionally matches the
     * application process lifecycle so that in-flight background disk cache updates are not abruptly cancelled
     * when the user navigates between screens. Uses [SupervisorJob] so failures in an individual row
     * revalidation never cancel other ongoing cache updates.
     */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeRefreshes = ConcurrentHashMap<String, Boolean>()

    private fun refreshIfStale(key: String, block: suspend () -> Unit) {
        if (diskCache.isCacheStale(key)) {
            if (activeRefreshes.putIfAbsent(key, true) == null) {
                applicationScope.launch {
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

    private val mostPopularTvShowsReader: CachedDataReader<Movie> = CachedDataReader {
        val cached = diskCache.loadMovies("tv_shows")
        if (cached.isNotEmpty()) {
            refreshIfStale("tv_shows") {
                fetchTvShowsLive()?.let { mostPopularTvShowsReader.update(it) }
            }
            return@CachedDataReader cached
        }
        fetchTvShowsLive() ?: emptyList()
    }

    private val topRatedTvShowsReader: CachedDataReader<Movie> = CachedDataReader {
        val cached = diskCache.loadMovies("tv_shows_top_rated")
        if (cached.isNotEmpty()) {
            refreshIfStale("tv_shows_top_rated") {
                fetchTopRatedTvShowsLive()?.let { topRatedTvShowsReader.update(it) }
            }
            return@CachedDataReader cached
        }
        fetchTopRatedTvShowsLive() ?: emptyList()
    }

    private suspend fun fetchTvShowsLive(): List<Movie>? {
        if (TmdbConfig.USE_TMDB) {
            val combined = fetchTwoPages { page -> tmdbApiClient.getPopularTvShows(page = page) }
            if (combined.isNotEmpty()) {
                diskCache.saveMovies("tv_shows", combined)
                return combined
            }
        }
        if (NetworkConfig.USE_REMOTE_BACKEND) {
            val remote = apiClient.getMovies(NetworkConfig.TV_SHOWS_ENDPOINT).getOrNull()
            if (!remote.isNullOrEmpty()) {
                val mapped = remote.map { it.toMovie() }
                diskCache.saveMovies("tv_shows", mapped)
                return mapped
            }
        }
        return null
    }

    private suspend fun fetchTopRatedTvShowsLive(): List<Movie>? {
        if (TmdbConfig.USE_TMDB) {
            val combined = fetchTwoPages { page -> tmdbApiClient.getTopRatedTvShows(page = page) }
            if (combined.isNotEmpty()) {
                diskCache.saveMovies("tv_shows_top_rated", combined)
                return combined
            }
        }
        return null
    }

    suspend fun getTvShowList(): List<Movie> = mostPopularTvShowsReader.read()

    suspend fun getBingeWatchDramaList(): List<Movie> {
        val topRated = topRatedTvShowsReader.read()
        return if (topRated.isNotEmpty()) topRated else mostPopularTvShowsReader.read()
    }
}
