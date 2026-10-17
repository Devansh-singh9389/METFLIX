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
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Singleton
class MovieDataSource @Inject constructor(
    private val apiClient: MetFlixApiClient,
    private val tmdbApiClient: TmdbApiClient,
    private val diskCache: TmdbDiskCache
) {

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
            CoroutineScope(Dispatchers.IO).launch {
                fetchTrendingLive()?.let { trendingMovieDataReader.update(it) }
            }
            return@MovieDataReader cached
        }
        fetchTrendingLive() ?: emptyList()
    }

    private suspend fun fetchTrendingLive(): List<Movie>? {
        if (TmdbConfig.USE_TMDB) {
            val tmdb = tmdbApiClient.getTrendingMovies().getOrNull()
            if (!tmdb.isNullOrEmpty()) {
                diskCache.saveMovies("trending", tmdb)
                return tmdb
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
            CoroutineScope(Dispatchers.IO).launch {
                fetchPopularLive()?.let { popularMovieDataReader.update(it) }
            }
            return@MovieDataReader cached
        }
        fetchPopularLive() ?: emptyList()
    }

    private suspend fun fetchPopularLive(): List<Movie>? {
        if (TmdbConfig.USE_TMDB) {
            val tmdb = tmdbApiClient.getPopularMovies().getOrNull()
            if (!tmdb.isNullOrEmpty()) {
                diskCache.saveMovies("popular", tmdb)
                return tmdb
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
            CoroutineScope(Dispatchers.IO).launch {
                fetchNowPlayingLive()?.let { nowPlayingMovieDataReader.update(it) }
            }
            return@MovieDataReader cached
        }
        fetchNowPlayingLive() ?: emptyList()
    }

    private suspend fun fetchNowPlayingLive(): List<Movie>? {
        if (TmdbConfig.USE_TMDB) {
            val tmdb = tmdbApiClient.getNowPlayingMovies().getOrNull()
            if (!tmdb.isNullOrEmpty()) {
                diskCache.saveMovies("now_playing", tmdb)
                return tmdb
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
            CoroutineScope(Dispatchers.IO).launch {
                fetchTopRatedLive()?.let { topRatedMovieDataReader.update(it) }
            }
            return@MovieDataReader cached
        }
        fetchTopRatedLive() ?: emptyList()
    }

    private suspend fun fetchTopRatedLive(): List<Movie>? {
        if (TmdbConfig.USE_TMDB) {
            val tmdb = tmdbApiClient.getTopRatedMovies().getOrNull()
            if (!tmdb.isNullOrEmpty()) {
                diskCache.saveMovies("top_rated", tmdb)
                return tmdb
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
            CoroutineScope(Dispatchers.IO).launch {
                val tmdb = tmdbApiClient.getTrendingMoviesDay().getOrNull()
                if (!tmdb.isNullOrEmpty()) {
                    diskCache.saveMovies("trending_day", tmdb)
                    trendingDayMovieDataReader.update(tmdb)
                }
            }
            return@MovieDataReader cached
        }
        if (TmdbConfig.USE_TMDB) {
            val tmdb = tmdbApiClient.getTrendingMoviesDay().getOrNull()
            if (!tmdb.isNullOrEmpty()) {
                diskCache.saveMovies("trending_day", tmdb)
                return@MovieDataReader tmdb
            }
        }
        trendingMovieDataReader.read()
    }

    private val upcomingMovieDataReader: MovieDataReader = MovieDataReader {
        val cached = diskCache.loadMovies("upcoming")
        if (cached.isNotEmpty()) {
            CoroutineScope(Dispatchers.IO).launch {
                val tmdb = tmdbApiClient.getUpcomingMovies().getOrNull()
                if (!tmdb.isNullOrEmpty()) {
                    diskCache.saveMovies("upcoming", tmdb)
                    upcomingMovieDataReader.update(tmdb)
                }
            }
            return@MovieDataReader cached
        }
        if (TmdbConfig.USE_TMDB) {
            val tmdb = tmdbApiClient.getUpcomingMovies().getOrNull()
            if (!tmdb.isNullOrEmpty()) {
                diskCache.saveMovies("upcoming", tmdb)
                return@MovieDataReader tmdb
            }
        }
        popularMovieDataReader.read()
    }

    private val topRevenueMovieDataReader: MovieDataReader = MovieDataReader {
        val cached = diskCache.loadMovies("top_revenue")
        if (cached.isNotEmpty()) {
            CoroutineScope(Dispatchers.IO).launch {
                val tmdb = tmdbApiClient.getTopRevenueMovies().getOrNull()
                if (!tmdb.isNullOrEmpty()) {
                    diskCache.saveMovies("top_revenue", tmdb)
                    topRevenueMovieDataReader.update(tmdb)
                }
            }
            return@MovieDataReader cached
        }
        if (TmdbConfig.USE_TMDB) {
            val tmdb = tmdbApiClient.getTopRevenueMovies().getOrNull()
            if (!tmdb.isNullOrEmpty()) {
                diskCache.saveMovies("top_revenue", tmdb)
                return@MovieDataReader tmdb
            }
        }
        topRatedMovieDataReader.read()
    }

    private val mostVotedMovieDataReader: MovieDataReader = MovieDataReader {
        val cached = diskCache.loadMovies("most_voted")
        if (cached.isNotEmpty()) {
            CoroutineScope(Dispatchers.IO).launch {
                val tmdb = tmdbApiClient.getMostVotedMovies().getOrNull()
                if (!tmdb.isNullOrEmpty()) {
                    diskCache.saveMovies("most_voted", tmdb)
                    mostVotedMovieDataReader.update(tmdb)
                }
            }
            return@MovieDataReader cached
        }
        if (TmdbConfig.USE_TMDB) {
            val tmdb = tmdbApiClient.getMostVotedMovies().getOrNull()
            if (!tmdb.isNullOrEmpty()) {
                diskCache.saveMovies("most_voted", tmdb)
                return@MovieDataReader tmdb
            }
        }
        popularMovieDataReader.read()
    }

    private val netflixMovieDataReader: MovieDataReader = MovieDataReader {
        val cached = diskCache.loadMovies("netflix")
        if (cached.isNotEmpty()) {
            CoroutineScope(Dispatchers.IO).launch {
                val tmdb = tmdbApiClient.getMoviesByWatchProvider(providerId = 8).getOrNull()
                if (!tmdb.isNullOrEmpty()) {
                    diskCache.saveMovies("netflix", tmdb)
                    netflixMovieDataReader.update(tmdb)
                }
            }
            return@MovieDataReader cached
        }
        if (TmdbConfig.USE_TMDB) {
            val tmdb = tmdbApiClient.getMoviesByWatchProvider(providerId = 8).getOrNull()
            if (!tmdb.isNullOrEmpty()) {
                diskCache.saveMovies("netflix", tmdb)
                return@MovieDataReader tmdb
            }
        }
        popularMovieDataReader.read()
    }

    private val primeMovieDataReader: MovieDataReader = MovieDataReader {
        val cached = diskCache.loadMovies("prime")
        if (cached.isNotEmpty()) {
            CoroutineScope(Dispatchers.IO).launch {
                val tmdb = tmdbApiClient.getMoviesByWatchProvider(providerId = 119).getOrNull()
                if (!tmdb.isNullOrEmpty()) {
                    diskCache.saveMovies("prime", tmdb)
                    primeMovieDataReader.update(tmdb)
                }
            }
            return@MovieDataReader cached
        }
        if (TmdbConfig.USE_TMDB) {
            val tmdb = tmdbApiClient.getMoviesByWatchProvider(providerId = 119).getOrNull()
            if (!tmdb.isNullOrEmpty()) {
                diskCache.saveMovies("prime", tmdb)
                return@MovieDataReader tmdb
            }
        }
        popularMovieDataReader.read()
    }

    private val disneyMovieDataReader: MovieDataReader = MovieDataReader {
        val cached = diskCache.loadMovies("disney")
        if (cached.isNotEmpty()) {
            CoroutineScope(Dispatchers.IO).launch {
                val tmdb = tmdbApiClient.getMoviesByWatchProvider(providerId = 337).getOrNull()
                    ?: tmdbApiClient.getMoviesByWatchProvider(providerId = 122).getOrNull()
                if (!tmdb.isNullOrEmpty()) {
                    diskCache.saveMovies("disney", tmdb)
                    disneyMovieDataReader.update(tmdb)
                }
            }
            return@MovieDataReader cached
        }
        if (TmdbConfig.USE_TMDB) {
            val tmdb = tmdbApiClient.getMoviesByWatchProvider(providerId = 337).getOrNull()
                ?: tmdbApiClient.getMoviesByWatchProvider(providerId = 122).getOrNull()
            if (!tmdb.isNullOrEmpty()) {
                diskCache.saveMovies("disney", tmdb)
                return@MovieDataReader tmdb
            }
        }
        popularMovieDataReader.read()
    }

    suspend fun getMovieList(thumbnailType: ThumbnailType = ThumbnailType.Standard): List<Movie> =
        when (thumbnailType) {
            ThumbnailType.Standard -> popularMovieDataReader.read()
            ThumbnailType.Long -> movieWithLongThumbnailDataReader.read()
        }

    suspend fun getFeaturedMovieList(): List<Movie> {
        val list = nowPlayingMovieDataReader.read()
        return if (list.isNotEmpty()) list.take(5) else emptyList()
    }

    suspend fun getTrendingMovieList(): List<Movie> {
        val list = trendingMovieDataReader.read()
        return if (list.size >= 10) list.subList(0, 10) else list
    }

    suspend fun getTop10MovieList(): List<Movie> {
        val list = topRatedMovieDataReader.read()
        return if (list.size >= 10) list.take(10) else list
    }

    suspend fun getNowPlayingMovieList(): List<Movie> {
        val list = nowPlayingMovieDataReader.read()
        return if (list.size >= 10) list.take(10) else list
    }

    suspend fun getPopularFilmThisWeek(): List<Movie> {
        val list = popularMovieDataReader.read()
        return if (list.size >= 20) list.subList(10, 20) else list.take(10)
    }

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
        if (TmdbConfig.USE_TMDB) {
            val tmdb = tmdbApiClient.searchMovies(query).getOrNull()
            if (!tmdb.isNullOrEmpty()) return tmdb
        }
        if (NetworkConfig.USE_REMOTE_BACKEND) {
            val remote = apiClient.searchMovies(query).getOrNull()
            if (!remote.isNullOrEmpty()) return remote.map { it.toMovie() }
        }
        val all = popularMovieDataReader.read()
        return all.filter { it.name.contains(query, ignoreCase = true) }
    }
}
