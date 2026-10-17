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

@Singleton
class MovieDataSource @Inject constructor(
    private val apiClient: MetFlixApiClient,
    private val tmdbApiClient: TmdbApiClient
) {

    private val remoteMovieCache = CachedDataReader {
        if (NetworkConfig.USE_REMOTE_BACKEND) {
            apiClient.getMovies(NetworkConfig.MOVIES_ENDPOINT).getOrNull()?.map { it.toMovie() } ?: emptyList()
        } else {
            emptyList()
        }
    }

    private val trendingMovieDataReader = MovieDataReader {
        if (TmdbConfig.USE_TMDB) {
            val tmdb = tmdbApiClient.getTrendingMovies().getOrNull()
            if (!tmdb.isNullOrEmpty()) return@MovieDataReader tmdb
        }
        if (NetworkConfig.USE_REMOTE_BACKEND) {
            val remote = apiClient.getMovies(NetworkConfig.TRENDING_MOVIES_ENDPOINT).getOrNull()
            if (!remote.isNullOrEmpty()) return@MovieDataReader remote.map { it.toMovie() }
        }
        emptyList()
    }

    private val popularMovieDataReader = MovieDataReader {
        if (TmdbConfig.USE_TMDB) {
            val tmdb = tmdbApiClient.getPopularMovies().getOrNull()
            if (!tmdb.isNullOrEmpty()) return@MovieDataReader tmdb
        }
        if (NetworkConfig.USE_REMOTE_BACKEND) {
            val remote = remoteMovieCache.read()
            if (remote.isNotEmpty()) return@MovieDataReader remote
        }
        emptyList()
    }

    private val nowPlayingMovieDataReader: MovieDataReader = MovieDataReader {
        if (TmdbConfig.USE_TMDB) {
            val tmdb = tmdbApiClient.getNowPlayingMovies().getOrNull()
            if (!tmdb.isNullOrEmpty()) return@MovieDataReader tmdb
        }
        if (NetworkConfig.USE_REMOTE_BACKEND) {
            val remote = apiClient.getMovies(NetworkConfig.NOW_PLAYING_ENDPOINT).getOrNull()
            if (!remote.isNullOrEmpty()) return@MovieDataReader remote.map { it.toMovie() }
        }
        emptyList()
    }

    private val topRatedMovieDataReader = MovieDataReader {
        if (TmdbConfig.USE_TMDB) {
            val tmdb = tmdbApiClient.getTopRatedMovies().getOrNull()
            if (!tmdb.isNullOrEmpty()) return@MovieDataReader tmdb
        }
        if (NetworkConfig.USE_REMOTE_BACKEND) {
            val remote = apiClient.getMovies(NetworkConfig.TOP10_MOVIES_ENDPOINT).getOrNull()
            if (!remote.isNullOrEmpty()) return@MovieDataReader remote.map { it.toMovie(ThumbnailType.Long) }
        }
        emptyList()
    }

    private val movieWithLongThumbnailDataReader: MovieDataReader = CachedDataReader {
        val nowPlaying = nowPlayingMovieDataReader.read()
        if (nowPlaying.isNotEmpty()) {
            return@CachedDataReader nowPlaying
        }
        popularMovieDataReader.read().map {
            it.copy(thumbnailType = ThumbnailType.Long)
        }
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
