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
import com.example.metflix.data.entities.TvEpisode
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

@Singleton
class TmdbApiClient @Inject constructor(
    private val okHttpClient: OkHttpClient
) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = true
    }

    @Volatile
    private var circuitTrippedUntil: Long = 0L

    @Volatile
    private var consecutiveFailures: Int = 0

    fun resetCircuit() {
        consecutiveFailures = 0
        circuitTrippedUntil = 0L
    }

    private fun isCircuitOpen(): Boolean {
        return System.currentTimeMillis() < circuitTrippedUntil
    }

    private fun recordSuccess() {
        consecutiveFailures = 0
        circuitTrippedUntil = 0L
    }

    private fun recordFailure(endpoint: String, error: String) {
        consecutiveFailures++
        Log.w("TmdbApiClient", "TMDB failure count $consecutiveFailures ($endpoint): $error")
        if (consecutiveFailures >= 4) {
            circuitTrippedUntil = System.currentTimeMillis() + 8_000L
            Log.w("TmdbApiClient", "TMDB circuit opened for 8s after 4 consecutive failures.")
        }
    }

    private val isConfigured: Boolean
        get() = TmdbConfig.USE_TMDB && TmdbConfig.API_KEY.isNotBlank() && !isCircuitOpen()

    private fun appendPage(url: String, page: Int): String {
        if (page <= 1) return url
        val sep = if (url.contains("?")) "&" else "?"
        return "$url${sep}page=$page"
    }

    suspend fun getTrendingMovies(page: Int = 1): Result<List<Movie>> {
        if (!isConfigured) return Result.failure(IllegalStateException("TMDB API disabled or circuit breaker active"))
        return fetchJson(appendPage("${TmdbConfig.BASE_URL}/trending/movie/week", page)) { raw ->
            val p = json.decodeFromString<TmdbPageResponse<TmdbMovieItem>>(raw)
            p.results.map { it.toMovie(ThumbnailType.Standard) }
        }
    }

    suspend fun getTrendingMoviesDay(page: Int = 1): Result<List<Movie>> {
        if (!isConfigured) return Result.failure(IllegalStateException("TMDB API disabled or circuit breaker active"))
        return fetchJson(appendPage("${TmdbConfig.BASE_URL}/trending/movie/day", page)) { raw ->
            val p = json.decodeFromString<TmdbPageResponse<TmdbMovieItem>>(raw)
            p.results.map { it.toMovie(ThumbnailType.Standard) }
        }
    }

    suspend fun getUpcomingMovies(page: Int = 1): Result<List<Movie>> {
        if (!isConfigured) return Result.failure(IllegalStateException("TMDB API key not set"))
        return fetchJson(appendPage("${TmdbConfig.BASE_URL}/movie/upcoming", page)) { raw ->
            val p = json.decodeFromString<TmdbPageResponse<TmdbMovieItem>>(raw)
            p.results.map { it.toMovie(ThumbnailType.Standard) }
        }
    }

    suspend fun getTopRevenueMovies(page: Int = 1): Result<List<Movie>> {
        if (!isConfigured) return Result.failure(IllegalStateException("TMDB API key not set"))
        return fetchJson(appendPage("${TmdbConfig.BASE_URL}/discover/movie?sort_by=revenue.desc", page)) { raw ->
            val p = json.decodeFromString<TmdbPageResponse<TmdbMovieItem>>(raw)
            p.results.map { it.toMovie(ThumbnailType.Standard) }
        }
    }

    suspend fun getMostVotedMovies(page: Int = 1): Result<List<Movie>> {
        if (!isConfigured) return Result.failure(IllegalStateException("TMDB API key not set"))
        return fetchJson(appendPage("${TmdbConfig.BASE_URL}/discover/movie?sort_by=vote_count.desc", page)) { raw ->
            val p = json.decodeFromString<TmdbPageResponse<TmdbMovieItem>>(raw)
            p.results.map { it.toMovie(ThumbnailType.Standard) }
        }
    }

    suspend fun getMoviesByWatchProvider(providerId: Int, region: String = "IN", page: Int = 1): Result<List<Movie>> {
        if (!isConfigured) return Result.failure(IllegalStateException("TMDB API key not set"))
        return fetchJson(appendPage("${TmdbConfig.BASE_URL}/discover/movie?with_watch_providers=$providerId&watch_region=$region&sort_by=popularity.desc", page)) { raw ->
            val p = json.decodeFromString<TmdbPageResponse<TmdbMovieItem>>(raw)
            p.results.map { it.toMovie(ThumbnailType.Standard) }
        }
    }

    suspend fun getNowPlayingMovies(page: Int = 1): Result<List<Movie>> {
        if (!isConfigured) return Result.failure(IllegalStateException("TMDB API key not set"))
        return fetchJson(appendPage("${TmdbConfig.BASE_URL}/movie/now_playing", page)) { raw ->
            val p = json.decodeFromString<TmdbPageResponse<TmdbMovieItem>>(raw)
            p.results.map { it.toMovie(ThumbnailType.Long) }
        }
    }

    suspend fun getTopRatedMovies(page: Int = 1): Result<List<Movie>> {
        if (!isConfigured) return Result.failure(IllegalStateException("TMDB API key not set"))
        return fetchJson(appendPage("${TmdbConfig.BASE_URL}/movie/top_rated", page)) { raw ->
            val p = json.decodeFromString<TmdbPageResponse<TmdbMovieItem>>(raw)
            p.results.map { it.toMovie(ThumbnailType.Long) }
        }
    }

    suspend fun getPopularMovies(page: Int = 1): Result<List<Movie>> {
        if (!isConfigured) return Result.failure(IllegalStateException("TMDB API key not set"))
        return fetchJson(appendPage("${TmdbConfig.BASE_URL}/movie/popular", page)) { raw ->
            val p = json.decodeFromString<TmdbPageResponse<TmdbMovieItem>>(raw)
            p.results.map { it.toMovie(ThumbnailType.Standard) }
        }
    }

    suspend fun getPopularTvShows(page: Int = 1): Result<List<Movie>> {
        if (!isConfigured) return Result.failure(IllegalStateException("TMDB API key not set"))
        return fetchJson(appendPage("${TmdbConfig.BASE_URL}/tv/popular", page)) { raw ->
            val p = json.decodeFromString<TmdbPageResponse<TmdbTvItem>>(raw)
            p.results.map { it.toMovie(ThumbnailType.Long) }
        }
    }

    suspend fun getTopRatedTvShows(page: Int = 1): Result<List<Movie>> {
        if (!isConfigured) return Result.failure(IllegalStateException("TMDB API key not set"))
        return fetchJson(appendPage("${TmdbConfig.BASE_URL}/tv/top_rated", page)) { raw ->
            val p = json.decodeFromString<TmdbPageResponse<TmdbTvItem>>(raw)
            p.results.map { it.toMovie(ThumbnailType.Standard) }
        }
    }

    suspend fun getMovieCategories(): Result<List<MovieCategory>> {
        if (!isConfigured) return Result.failure(IllegalStateException("TMDB API key not set"))
        return fetchJson("${TmdbConfig.BASE_URL}/genre/movie/list") { raw ->
            val response = json.decodeFromString<TmdbGenreResponse>(raw)
            response.genres.map { it.toMovieCategory() }
        }
    }

    suspend fun getMoviesByCategory(genreId: String, page: Int = 1): Result<List<Movie>> {
        if (!isConfigured) return Result.failure(IllegalStateException("TMDB API key not set"))
        return fetchJson(appendPage("${TmdbConfig.BASE_URL}/discover/movie?with_genres=$genreId", page)) { raw ->
            val p = json.decodeFromString<TmdbPageResponse<TmdbMovieItem>>(raw)
            p.results.map { it.toMovie(ThumbnailType.Standard) }
        }
    }

    suspend fun getMovieDetails(movieId: String): Result<MovieDetails> {
        if (!isConfigured) return Result.failure(IllegalStateException("TMDB API key not set"))
        return fetchJson("${TmdbConfig.BASE_URL}/movie/$movieId?append_to_response=credits,similar,watch/providers") { raw ->
            val response = json.decodeFromString<TmdbMovieDetailsResponse>(raw)
            response.toMovieDetails()
        }
    }

    suspend fun getTvShowDetails(seriesId: String): Result<MovieDetails> {
        if (!isConfigured) return Result.failure(IllegalStateException("TMDB API key not set"))
        return fetchJson("${TmdbConfig.BASE_URL}/tv/$seriesId?append_to_response=credits,similar,watch/providers,external_ids") { raw ->
            val response = json.decodeFromString<TmdbTvDetailsResponse>(raw)
            response.toMovieDetails()
        }
    }

    suspend fun getTvSeasonDetails(seriesId: String, seasonNumber: Int): Result<List<TvEpisode>> {
        if (!isConfigured) return Result.failure(IllegalStateException("TMDB API key not set"))
        return fetchJson("${TmdbConfig.BASE_URL}/tv/$seriesId/season/$seasonNumber") { raw ->
            val response = json.decodeFromString<TmdbSeasonDetailsResponse>(raw)
            response.toTvEpisodes()
        }
    }

    suspend fun getTrendingAnime(page: Int = 1): Result<List<Movie>> {
        if (!isConfigured) return Result.failure(IllegalStateException("TMDB API key not set"))
        // Discover Anime TV series (Genre 16 = Animation, origin_country = JP)
        val url = appendPage("${TmdbConfig.BASE_URL}/discover/tv?with_genres=16&with_origin_country=JP&sort_by=popularity.desc", page)
        return fetchJson(url) { raw ->
            val p = json.decodeFromString<TmdbPageResponse<TmdbTvItem>>(raw)
            p.results.map { it.toMovie(ThumbnailType.Standard) }
        }
    }

    suspend fun getPopularAnimeMovies(page: Int = 1): Result<List<Movie>> {
        if (!isConfigured) return Result.failure(IllegalStateException("TMDB API key not set"))
        // Discover Anime Movies (Genre 16 = Animation, origin_country = JP)
        val url = appendPage("${TmdbConfig.BASE_URL}/discover/movie?with_genres=16&with_origin_country=JP&sort_by=popularity.desc", page)
        return fetchJson(url) { raw ->
            val p = json.decodeFromString<TmdbPageResponse<TmdbMovieItem>>(raw)
            p.results.map { it.toMovie(ThumbnailType.Standard) }
        }
    }

    suspend fun getAnimatedCartoonsAndKids(page: Int = 1): Result<List<Movie>> {
        if (!isConfigured) return Result.failure(IllegalStateException("TMDB API key not set"))
        // Popular worldwide cartoons & animated shows (Genre 16 = Animation)
        val url = appendPage("${TmdbConfig.BASE_URL}/discover/tv?with_genres=16&sort_by=vote_count.desc", page)
        return fetchJson(url) { raw ->
            val p = json.decodeFromString<TmdbPageResponse<TmdbTvItem>>(raw)
            p.results.map { it.toMovie(ThumbnailType.Standard) }
        }
    }

    suspend fun getTopRatedAnime(page: Int = 1): Result<List<Movie>> {
        if (!isConfigured) return Result.failure(IllegalStateException("TMDB API key not set"))
        val url = appendPage("${TmdbConfig.BASE_URL}/discover/tv?with_genres=16&with_origin_country=JP&sort_by=vote_average.desc&vote_count.gte=200", page)
        return fetchJson(url) { raw ->
            val p = json.decodeFromString<TmdbPageResponse<TmdbTvItem>>(raw)
            p.results.map { it.toMovie(ThumbnailType.Standard) }
        }
    }

    suspend fun searchMovies(query: String, page: Int = 1): Result<List<Movie>> {
        if (!isConfigured) return Result.failure(IllegalStateException("TMDB API key not set"))
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        return fetchJson(appendPage("${TmdbConfig.BASE_URL}/search/movie?query=$encodedQuery", page)) { raw ->
            val p = json.decodeFromString<TmdbPageResponse<TmdbMovieItem>>(raw)
            p.results.map { it.toMovie(ThumbnailType.Standard) }
        }
    }

    private suspend fun <T> fetchJson(urlWithoutKey: String, parser: (String) -> T): Result<T> = withContext(Dispatchers.IO) {
        if (!isConfigured) {
            return@withContext Result.failure(IllegalStateException("TMDB is disabled or circuit breaker active"))
        }
        runCatching {
            val key = TmdbConfig.API_KEY.trim()
            val isJwt = key.startsWith("eyJ")
            val separator = if (urlWithoutKey.contains("?")) "&" else "?"
            val finalUrl = if (isJwt) {
                "$urlWithoutKey${separator}language=en-US"
            } else {
                "$urlWithoutKey${separator}api_key=$key&language=en-US"
            }

            val requestBuilder = Request.Builder()
                .url(finalUrl)
                .header("Accept", "application/json")
                .header("User-Agent", "MetFlix-AndroidTV/1.0")

            if (isJwt) {
                requestBuilder.header("Authorization", "Bearer $key")
            }

            val response = okHttpClient.newCall(requestBuilder.build()).execute()
            response.use { resp ->
                if (resp.isSuccessful) {
                    val bodyString = resp.body?.string().orEmpty()
                    parser(bodyString)
                } else {
                    val errorText = resp.body?.string() ?: "HTTP ${resp.code}"
                    Log.w("TmdbApiClient", "TMDB API Error ${resp.code}: $errorText")
                    throw Exception("TMDB error (${resp.code}): $errorText")
                }
            }
        }.onSuccess {
            recordSuccess()
        }.onFailure { err ->
            recordFailure(urlWithoutKey, err.message.orEmpty())
        }
    }
}
