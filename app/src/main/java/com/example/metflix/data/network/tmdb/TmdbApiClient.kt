/*
 * Copyright 2025 Google LLC
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

package com.example.metflix.data.network.tmdb

import android.util.Log
import com.example.metflix.data.entities.Movie
import com.example.metflix.data.entities.MovieCategory
import com.example.metflix.data.entities.MovieDetails
import com.example.metflix.data.entities.ThumbnailType
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

@Singleton
class TmdbApiClient @Inject constructor() {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = true
    }

    private val isConfigured: Boolean
        get() = TmdbConfig.USE_TMDB && TmdbConfig.API_KEY.isNotBlank()

    suspend fun getTrendingMovies(): Result<List<Movie>> {
        if (!isConfigured) return Result.failure(IllegalStateException("TMDB API key not set"))
        return fetchJson("${TmdbConfig.BASE_URL}/trending/movie/week") { raw ->
            val page = json.decodeFromString<TmdbPageResponse<TmdbMovieItem>>(raw)
            page.results.map { it.toMovie(ThumbnailType.Standard) }
        }
    }

    suspend fun getNowPlayingMovies(): Result<List<Movie>> {
        if (!isConfigured) return Result.failure(IllegalStateException("TMDB API key not set"))
        return fetchJson("${TmdbConfig.BASE_URL}/movie/now_playing") { raw ->
            val page = json.decodeFromString<TmdbPageResponse<TmdbMovieItem>>(raw)
            page.results.map { it.toMovie(ThumbnailType.Long) }
        }
    }

    suspend fun getTopRatedMovies(): Result<List<Movie>> {
        if (!isConfigured) return Result.failure(IllegalStateException("TMDB API key not set"))
        return fetchJson("${TmdbConfig.BASE_URL}/movie/top_rated") { raw ->
            val page = json.decodeFromString<TmdbPageResponse<TmdbMovieItem>>(raw)
            page.results.map { it.toMovie(ThumbnailType.Long) }
        }
    }

    suspend fun getPopularMovies(): Result<List<Movie>> {
        if (!isConfigured) return Result.failure(IllegalStateException("TMDB API key not set"))
        return fetchJson("${TmdbConfig.BASE_URL}/movie/popular") { raw ->
            val page = json.decodeFromString<TmdbPageResponse<TmdbMovieItem>>(raw)
            page.results.map { it.toMovie(ThumbnailType.Standard) }
        }
    }

    suspend fun getPopularTvShows(): Result<List<Movie>> {
        if (!isConfigured) return Result.failure(IllegalStateException("TMDB API key not set"))
        return fetchJson("${TmdbConfig.BASE_URL}/tv/popular") { raw ->
            val page = json.decodeFromString<TmdbPageResponse<TmdbTvItem>>(raw)
            page.results.map { it.toMovie(ThumbnailType.Long) }
        }
    }

    suspend fun getTopRatedTvShows(): Result<List<Movie>> {
        if (!isConfigured) return Result.failure(IllegalStateException("TMDB API key not set"))
        return fetchJson("${TmdbConfig.BASE_URL}/tv/top_rated") { raw ->
            val page = json.decodeFromString<TmdbPageResponse<TmdbTvItem>>(raw)
            page.results.map { it.toMovie(ThumbnailType.Standard) }
        }
    }

    suspend fun getMovieCategories(): Result<List<MovieCategory>> {
        if (!isConfigured) return Result.failure(IllegalStateException("TMDB API key not set"))
        return fetchJson("${TmdbConfig.BASE_URL}/genre/movie/list") { raw ->
            val response = json.decodeFromString<TmdbGenreResponse>(raw)
            response.genres.map { it.toMovieCategory() }
        }
    }

    suspend fun getMoviesByCategory(genreId: String): Result<List<Movie>> {
        if (!isConfigured) return Result.failure(IllegalStateException("TMDB API key not set"))
        return fetchJson("${TmdbConfig.BASE_URL}/discover/movie?with_genres=$genreId") { raw ->
            val page = json.decodeFromString<TmdbPageResponse<TmdbMovieItem>>(raw)
            page.results.map { it.toMovie(ThumbnailType.Standard) }
        }
    }

    suspend fun getMovieDetails(movieId: String): Result<MovieDetails> {
        if (!isConfigured) return Result.failure(IllegalStateException("TMDB API key not set"))
        return fetchJson("${TmdbConfig.BASE_URL}/movie/$movieId?append_to_response=credits,similar") { raw ->
            val response = json.decodeFromString<TmdbMovieDetailsResponse>(raw)
            response.toMovieDetails()
        }
    }

    suspend fun searchMovies(query: String): Result<List<Movie>> {
        if (!isConfigured) return Result.failure(IllegalStateException("TMDB API key not set"))
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        return fetchJson("${TmdbConfig.BASE_URL}/search/movie?query=$encodedQuery") { raw ->
            val page = json.decodeFromString<TmdbPageResponse<TmdbMovieItem>>(raw)
            page.results.map { it.toMovie(ThumbnailType.Standard) }
        }
    }

    private suspend fun <T> fetchJson(urlWithoutKey: String, parser: (String) -> T): Result<T> = withContext(Dispatchers.IO) {
        runCatching {
            val key = TmdbConfig.API_KEY.trim()
            val isJwt = key.startsWith("eyJ")
            val separator = if (urlWithoutKey.contains("?")) "&" else "?"
            val finalUrl = if (isJwt) {
                "$urlWithoutKey${separator}language=en-US"
            } else {
                "$urlWithoutKey${separator}api_key=$key&language=en-US"
            }

            val url = URL(finalUrl)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "MetFlix-AndroidTV/1.0")
                if (isJwt) {
                    setRequestProperty("Authorization", "Bearer $key")
                }
            }

            try {
                val statusCode = connection.responseCode
                if (statusCode in 200..299) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
                    val response = reader.use { it.readText() }
                    parser(response)
                } else {
                    val errorText = connection.errorStream?.let { stream ->
                        BufferedReader(InputStreamReader(stream)).use { it.readText() }
                    } ?: "HTTP $statusCode"
                    Log.w("TmdbApiClient", "TMDB API Error $statusCode: $errorText")
                    throw Exception("TMDB error ($statusCode): $errorText")
                }
            } finally {
                connection.disconnect()
            }
        }
    }
}
