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
import com.example.metflix.data.entities.ThumbnailType
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
class MovieDataSource @Inject constructor(
    private val apiClient: MetFlixApiClient,
    private val tmdbApiClient: TmdbApiClient,
    private val diskCache: TmdbDiskCache
) {
    /**
     * Application-process-lived coroutine scope dedicated to non-blocking background stale-while-revalidate
     * cache refreshes. Because [MovieDataSource] is a [@Singleton], this scope intentionally matches the
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

    private val remoteMovieCache = CachedDataReader {
        if (NetworkConfig.USE_REMOTE_BACKEND) {
            apiClient.getMovies(NetworkConfig.MOVIES_ENDPOINT).getOrNull()?.map { it.toMovie() } ?: emptyList()
        } else {
            emptyList()
        }
    }

    private val trendingMovieDataReader: MovieDataReader = MovieDataReader {
        val cached = diskCache.loadMovies("trending")
        if (cached.isNotEmpty()) {
            refreshIfStale("trending") {
                fetchTrendingLive()?.let { trendingMovieDataReader.update(it) }
            }
            return@MovieDataReader cached
        }
        fetchTrendingLive() ?: emptyList()
    }

    suspend fun fetchTrendingLive(): List<Movie>? {
        if (TmdbConfig.USE_TMDB) {
            val combined = fetchTwoPages { page -> tmdbApiClient.getTrendingMovies(page = page) }
            if (combined.isNotEmpty()) {
                diskCache.saveMovies("trending", combined)
                return combined
            }
        }
        if (NetworkConfig.USE_REMOTE_BACKEND) {
            val remote = apiClient.getMovies(NetworkConfig.TRENDING_MOVIES_ENDPOINT).getOrNull()
            if (!remote.isNullOrEmpty()) {
                val mapped = remote.map { it.toMovie() }
                diskCache.saveMovies("trending", mapped)
                return mapped
            }
        }
        return null
    }

    private val popularMovieDataReader: MovieDataReader = MovieDataReader {
        val cached = diskCache.loadMovies("popular")
        if (cached.isNotEmpty()) {
            refreshIfStale("popular") {
                fetchPopularLive()?.let { popularMovieDataReader.update(it) }
            }
            return@MovieDataReader cached
        }
        fetchPopularLive() ?: emptyList()
    }

    suspend fun fetchPopularLive(): List<Movie>? {
        if (TmdbConfig.USE_TMDB) {
            val combined = fetchTwoPages { page -> tmdbApiClient.getPopularMovies(page = page) }
            if (combined.isNotEmpty()) {
                diskCache.saveMovies("popular", combined)
                return combined
            }
        }
        if (NetworkConfig.USE_REMOTE_BACKEND) {
            val remote = remoteMovieCache.read()
            if (remote.isNotEmpty()) {
                diskCache.saveMovies("popular", remote)
                return remote
            }
        }
        return null
    }

    private val nowPlayingMovieDataReader: MovieDataReader = MovieDataReader {
        val cached = diskCache.loadMovies("now_playing")
        if (cached.isNotEmpty()) {
            refreshIfStale("now_playing") {
                fetchNowPlayingLive()?.let { nowPlayingMovieDataReader.update(it) }
            }
            return@MovieDataReader cached
        }
        fetchNowPlayingLive() ?: emptyList()
    }

    suspend fun fetchNowPlayingLive(): List<Movie>? {
        if (TmdbConfig.USE_TMDB) {
            val combined = fetchTwoPages { page -> tmdbApiClient.getNowPlayingMovies(page = page) }
            if (combined.isNotEmpty()) {
                diskCache.saveMovies("now_playing", combined)
                return combined
            }
        }
        if (NetworkConfig.USE_REMOTE_BACKEND) {
            val remote = apiClient.getMovies(NetworkConfig.NOW_PLAYING_ENDPOINT).getOrNull()
            if (!remote.isNullOrEmpty()) {
                val mapped = remote.map { it.toMovie() }
                diskCache.saveMovies("now_playing", mapped)
                return mapped
            }
        }
        return null
    }

    private val topRatedMovieDataReader: MovieDataReader = MovieDataReader {
        val cached = diskCache.loadMovies("top_rated")
        if (cached.isNotEmpty()) {
            refreshIfStale("top_rated") {
                fetchTopRatedLive()?.let { topRatedMovieDataReader.update(it) }
            }
            return@MovieDataReader cached
        }
        fetchTopRatedLive() ?: emptyList()
    }

    suspend fun fetchTopRatedLive(): List<Movie>? {
        if (TmdbConfig.USE_TMDB) {
            val combined = fetchTwoPages { page -> tmdbApiClient.getTopRatedMovies(page = page) }
            if (combined.isNotEmpty()) {
                diskCache.saveMovies("top_rated", combined)
                return combined
            }
        }
        if (NetworkConfig.USE_REMOTE_BACKEND) {
            val remote = apiClient.getMovies(NetworkConfig.TOP10_MOVIES_ENDPOINT).getOrNull()
            if (!remote.isNullOrEmpty()) {
                val mapped = remote.map { it.toMovie(ThumbnailType.Long) }
                diskCache.saveMovies("top_rated", mapped)
                return mapped
            }
        }
        return null
    }

    private val movieWithLongThumbnailDataReader: MovieDataReader = CachedDataReader {
        val nowPlaying = nowPlayingMovieDataReader.read()
        if (nowPlaying.isNotEmpty()) {
            return@CachedDataReader nowPlaying
        }
        popularMovieDataReader.read()
    }

    private val trendingDayMovieDataReader: MovieDataReader = MovieDataReader {
        val cached = diskCache.loadMovies("trending_day")
        if (cached.isNotEmpty()) {
            refreshIfStale("trending_day") {
                fetchTrendingDayLive()?.let { trendingDayMovieDataReader.update(it) }
            }
            return@MovieDataReader cached
        }
        fetchTrendingDayLive() ?: trendingMovieDataReader.read()
    }

    suspend fun fetchTrendingDayLive(): List<Movie>? {
        if (TmdbConfig.USE_TMDB) {
            val combined = fetchTwoPages { page -> tmdbApiClient.getTrendingMoviesDay(page = page) }
            if (combined.isNotEmpty()) {
                diskCache.saveMovies("trending_day", combined)
                return combined
            }
        }
        return null
    }

    private val upcomingMovieDataReader: MovieDataReader = MovieDataReader {
        val cached = diskCache.loadMovies("upcoming")
        if (cached.isNotEmpty()) {
            refreshIfStale("upcoming") {
                fetchUpcomingLive()?.let { upcomingMovieDataReader.update(it) }
            }
            return@MovieDataReader cached
        }
        fetchUpcomingLive() ?: popularMovieDataReader.read()
    }

    suspend fun fetchUpcomingLive(): List<Movie>? {
        if (TmdbConfig.USE_TMDB) {
            val combined = fetchTwoPages { page -> tmdbApiClient.getUpcomingMovies(page = page) }
            if (combined.isNotEmpty()) {
                diskCache.saveMovies("upcoming", combined)
                return combined
            }
        }
        return null
    }

    private val topRevenueMovieDataReader: MovieDataReader = MovieDataReader {
        val cached = diskCache.loadMovies("top_revenue")
        if (cached.isNotEmpty()) {
            refreshIfStale("top_revenue") {
                fetchTopRevenueLive()?.let { topRevenueMovieDataReader.update(it) }
            }
            return@MovieDataReader cached
        }
        fetchTopRevenueLive() ?: topRatedMovieDataReader.read()
    }

    suspend fun fetchTopRevenueLive(): List<Movie>? {
        if (TmdbConfig.USE_TMDB) {
            val combined = fetchTwoPages { page -> tmdbApiClient.getTopRevenueMovies(page = page) }
            if (combined.isNotEmpty()) {
                diskCache.saveMovies("top_revenue", combined)
                return combined
            }
        }
        return null
    }

    private val mostVotedMovieDataReader: MovieDataReader = MovieDataReader {
        val cached = diskCache.loadMovies("most_voted")
        if (cached.isNotEmpty()) {
            refreshIfStale("most_voted") {
                fetchMostVotedLive()?.let { mostVotedMovieDataReader.update(it) }
            }
            return@MovieDataReader cached
        }
        fetchMostVotedLive() ?: popularMovieDataReader.read()
    }

    suspend fun fetchMostVotedLive(): List<Movie>? {
        if (TmdbConfig.USE_TMDB) {
            val combined = fetchTwoPages { page -> tmdbApiClient.getMostVotedMovies(page = page) }
            if (combined.isNotEmpty()) {
                diskCache.saveMovies("most_voted", combined)
                return combined
            }
        }
        return null
    }

    private val netflixMovieDataReader: MovieDataReader = MovieDataReader {
        val cached = diskCache.loadMovies("netflix")
        if (cached.isNotEmpty()) {
            refreshIfStale("netflix") {
                fetchNetflixLive()?.let { netflixMovieDataReader.update(it) }
            }
            return@MovieDataReader cached
        }
        fetchNetflixLive() ?: popularMovieDataReader.read()
    }

    suspend fun fetchNetflixLive(): List<Movie>? {
        if (TmdbConfig.USE_TMDB) {
            val combined = fetchTwoPages { page -> tmdbApiClient.getMoviesByWatchProvider(providerId = 8, page = page) }
            if (combined.isNotEmpty()) {
                diskCache.saveMovies("netflix", combined)
                return combined
            }
        }
        return null
    }

    private val primeMovieDataReader: MovieDataReader = MovieDataReader {
        val cached = diskCache.loadMovies("prime")
        if (cached.isNotEmpty()) {
            refreshIfStale("prime") {
                fetchPrimeLive()?.let { primeMovieDataReader.update(it) }
            }
            return@MovieDataReader cached
        }
        fetchPrimeLive() ?: popularMovieDataReader.read()
    }

    suspend fun fetchPrimeLive(): List<Movie>? {
        if (TmdbConfig.USE_TMDB) {
            val combined = fetchTwoPages { page -> tmdbApiClient.getMoviesByWatchProvider(providerId = 119, page = page) }
            if (combined.isNotEmpty()) {
                diskCache.saveMovies("prime", combined)
                return combined
            }
        }
        return null
    }

    private val disneyMovieDataReader: MovieDataReader = MovieDataReader {
        val cached = diskCache.loadMovies("disney")
        if (cached.isNotEmpty()) {
            refreshIfStale("disney") {
                fetchDisneyLive()?.let { disneyMovieDataReader.update(it) }
            }
            return@MovieDataReader cached
        }
        fetchDisneyLive() ?: popularMovieDataReader.read()
    }

    suspend fun fetchDisneyLive(): List<Movie>? {
        if (TmdbConfig.USE_TMDB) {
            val combined = coroutineScope {
                val p1 = async {
                    tmdbApiClient.getMoviesByWatchProvider(providerId = 337, page = 1).getOrNull()
                        ?: tmdbApiClient.getMoviesByWatchProvider(providerId = 122, page = 1).getOrNull().orEmpty()
                }
                val p2 = async {
                    tmdbApiClient.getMoviesByWatchProvider(providerId = 337, page = 2).getOrNull()
                        ?: tmdbApiClient.getMoviesByWatchProvider(providerId = 122, page = 2).getOrNull().orEmpty()
                }
                (p1.await() + p2.await()).distinctBy { it.id }
            }
            if (combined.isNotEmpty()) {
                diskCache.saveMovies("disney", combined)
                return combined
            }
        }
        return null
    }

    suspend fun clearAllReaders() {
        trendingMovieDataReader.clear()
        popularMovieDataReader.clear()
        nowPlayingMovieDataReader.clear()
        topRatedMovieDataReader.clear()
        trendingDayMovieDataReader.clear()
        upcomingMovieDataReader.clear()
        topRevenueMovieDataReader.clear()
        mostVotedMovieDataReader.clear()
        netflixMovieDataReader.clear()
        primeMovieDataReader.clear()
        disneyMovieDataReader.clear()
    }

    suspend fun getMovieList(thumbnailType: ThumbnailType = ThumbnailType.Standard): List<Movie> =
        when (thumbnailType) {
            ThumbnailType.Standard -> popularMovieDataReader.read()
            ThumbnailType.Long -> movieWithLongThumbnailDataReader.read()
        }

    suspend fun getFeaturedMovieList(): List<Movie> {
        val list = nowPlayingMovieDataReader.read()
        return if (list.isNotEmpty()) list.take(8) else emptyList()
    }

    suspend fun getTrendingMovieList(): List<Movie> = trendingMovieDataReader.read()

    suspend fun getTop10MovieList(): List<Movie> {
        val list = topRatedMovieDataReader.read()
        return if (list.size >= 10) list.take(10) else list
    }

    suspend fun getNowPlayingMovieList(): List<Movie> = nowPlayingMovieDataReader.read()

    suspend fun getPopularFilmThisWeek(): List<Movie> = popularMovieDataReader.read()

    suspend fun getTrendingDayMovieList(): List<Movie> = trendingDayMovieDataReader.read()
    suspend fun getUpcomingMovieList(): List<Movie> = upcomingMovieDataReader.read()
    suspend fun getTopRevenueMovieList(): List<Movie> = topRevenueMovieDataReader.read()
    suspend fun getMostVotedMovieList(): List<Movie> = mostVotedMovieDataReader.read()
    suspend fun getNetflixMovieList(): List<Movie> = netflixMovieDataReader.read()
    suspend fun getPrimeMovieList(): List<Movie> = primeMovieDataReader.read()
    suspend fun getDisneyMovieList(): List<Movie> = disneyMovieDataReader.read()

    suspend fun getFavoriteMovieList(): List<Movie> {
        val list = popularMovieDataReader.read()
        return if (list.size >= 28) list.subList(0, 28) else list
    }

    suspend fun searchMovies(query: String): List<Movie> {
        // Search must be deterministic and must not turn a local search into a live
        // TMDB request. The catalog cache is the source of truth for this screen.
        val all = diskCache.loadCachedCatalogMovies()
        return all.filter { it.name.contains(query, ignoreCase = true) }
    }
}
