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
import com.example.metflix.data.util.AssetsReader
import com.example.metflix.data.util.StringConstants
import javax.inject.Inject

class MovieDataSource @Inject constructor(
    assetsReader: AssetsReader,
    private val apiClient: MetFlixApiClient,
    private val tmdbApiClient: TmdbApiClient
) {

    private val remoteMovieCache = CachedDataReader {
        apiClient.getMovies(NetworkConfig.MOVIES_ENDPOINT).getOrNull()?.map { it.toMovie() } ?: emptyList()
    }

    private val top250MovieDataReader = CachedDataReader {
        readMovieData(assetsReader, StringConstants.Assets.Top250Movies)
    }

    private val mostPopularMovieDataReader = MovieDataReader {
        if (TmdbConfig.USE_TMDB) {
            val tmdb = tmdbApiClient.getTrendingMovies().getOrNull()
            if (!tmdb.isNullOrEmpty()) return@MovieDataReader tmdb
        }
        if (NetworkConfig.USE_REMOTE_BACKEND) {
            val remote = apiClient.getMovies(NetworkConfig.TRENDING_MOVIES_ENDPOINT).getOrNull()
            if (!remote.isNullOrEmpty()) return@MovieDataReader remote.map { it.toMovie() }
        }
        readMovieData(assetsReader, StringConstants.Assets.MostPopularMovies).map {
            it.toMovie()
        }
    }

    private val movieDataReader = MovieDataReader {
        if (TmdbConfig.USE_TMDB) {
            val tmdb = tmdbApiClient.getPopularMovies().getOrNull()
            if (!tmdb.isNullOrEmpty()) return@MovieDataReader tmdb
        }
        if (NetworkConfig.USE_REMOTE_BACKEND) {
            val remote = remoteMovieCache.read()
            if (remote.isNotEmpty()) return@MovieDataReader remote
        }
        top250MovieDataReader.read().map {
            it.toMovie()
        }
    }

    private var movieWithLongThumbnailDataReader: MovieDataReader = CachedDataReader {
        if (TmdbConfig.USE_TMDB) {
            val tmdb = tmdbApiClient.getNowPlayingMovies().getOrNull()
            if (!tmdb.isNullOrEmpty()) return@CachedDataReader tmdb
        }
        if (NetworkConfig.USE_REMOTE_BACKEND) {
            val remote = remoteMovieCache.read()
            if (remote.isNotEmpty()) return@CachedDataReader remote.map { it.copy() }
        }
        top250MovieDataReader.read().map {
            it.toMovie(ThumbnailType.Long)
        }
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
        readMovieData(assetsReader, StringConstants.Assets.InTheaters).subList(0, 10).map {
            it.toMovie()
        }
    }

    suspend fun getMovieList(thumbnailType: ThumbnailType = ThumbnailType.Standard) =
        when (thumbnailType) {
            ThumbnailType.Standard -> movieDataReader.read()
            ThumbnailType.Long -> movieWithLongThumbnailDataReader.read()
        }

    suspend fun getFeaturedMovieList(): List<Movie> {
        if (TmdbConfig.USE_TMDB) {
            val tmdb = tmdbApiClient.getNowPlayingMovies().getOrNull()
            if (!tmdb.isNullOrEmpty()) return tmdb.take(5)
        }
        if (NetworkConfig.USE_REMOTE_BACKEND) {
            val remote = apiClient.getMovies(NetworkConfig.FEATURED_MOVIES_ENDPOINT).getOrNull()
            if (!remote.isNullOrEmpty()) return remote.map { it.toMovie(ThumbnailType.Long) }
        }
        val list = movieWithLongThumbnailDataReader.read()
        return list.filterIndexed { index, _ -> listOf(1, 3, 5, 7, 9).contains(index) }
    }

    suspend fun getTrendingMovieList(): List<Movie> {
        if (TmdbConfig.USE_TMDB) {
            val tmdb = tmdbApiClient.getTrendingMovies().getOrNull()
            if (!tmdb.isNullOrEmpty()) return tmdb.take(10)
        }
        val list = mostPopularMovieDataReader.read()
        return if (list.size >= 10) list.subList(0, 10) else list
    }

    suspend fun getTop10MovieList(): List<Movie> {
        if (TmdbConfig.USE_TMDB) {
            val tmdb = tmdbApiClient.getTopRatedMovies().getOrNull()
            if (!tmdb.isNullOrEmpty()) return tmdb.take(10)
        }
        if (NetworkConfig.USE_REMOTE_BACKEND) {
            val remote = apiClient.getMovies(NetworkConfig.TOP10_MOVIES_ENDPOINT).getOrNull()
            if (!remote.isNullOrEmpty()) return remote.map { it.toMovie(ThumbnailType.Long) }
        }
        val list = movieWithLongThumbnailDataReader.read()
        return if (list.size >= 30) list.subList(20, 30) else list.take(10)
    }

    suspend fun getNowPlayingMovieList(): List<Movie> {
        if (TmdbConfig.USE_TMDB) {
            val tmdb = tmdbApiClient.getNowPlayingMovies().getOrNull()
            if (!tmdb.isNullOrEmpty()) return tmdb.take(10)
        }
        return nowPlayingMovieDataReader.read()
    }

    suspend fun getPopularFilmThisWeek(): List<Movie> {
        if (TmdbConfig.USE_TMDB) {
            val tmdb = tmdbApiClient.getPopularMovies().getOrNull()
            if (!tmdb.isNullOrEmpty()) return tmdb.take(10)
        }
        val list = mostPopularMovieDataReader.read()
        return if (list.size >= 20) list.subList(11, 20) else list.take(10)
    }

    suspend fun getFavoriteMovieList(): List<Movie> {
        val list = movieDataReader.read()
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
        val all = movieDataReader.read()
        return all.filter { it.name.contains(query, ignoreCase = true) }
    }
}
