package com.example.metflix.data.network.anime

import android.net.Uri
import android.util.Log
import com.example.metflix.data.network.anime.models.AnimeCategoryResponse
import com.example.metflix.data.network.anime.models.AnimeEpisodeResponse
import com.example.metflix.data.network.anime.models.AnimeHomeResponse
import com.example.metflix.data.network.anime.models.AnimeMovieResponse
import com.example.metflix.data.network.anime.models.AnimeSeriesResponse
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

@Singleton
class AnimeApiClient @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "AnimeApiClient"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = true
    }

    private val animeHttpClient: OkHttpClient by lazy {
        okHttpClient.newBuilder()
            .connectTimeout(AnimeBackendConfig.TIMEOUT_CONNECT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(AnimeBackendConfig.TIMEOUT_READ_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    private suspend inline fun <reified T> fetch(endpoint: String): Result<T> = withContext(Dispatchers.IO) {
        val path = endpoint.trimStart('/')
        val bases = listOf(
            AnimeBackendConfig.BASE_URL.trimEnd('/'),
            AnimeBackendConfig.FALLBACK_BASE_URL.trimEnd('/')
        ).distinct()

        var lastException: Throwable? = null
        for (base in bases) {
            val fullUrl = "$base/$path"
            val attempt = runCatching {
                Log.d(TAG, "Anime API request: $fullUrl")
                val request = Request.Builder()
                    .url(fullUrl)
                    .header("Accept", "application/json")
                    .header("User-Agent", "MetFlix-AndroidTV/1.0")
                    .build()

                val response = animeHttpClient.newCall(request).execute()
                response.use { resp ->
                    if (!resp.isSuccessful) {
                        val errorBody = resp.body?.string().orEmpty()
                        Log.w(TAG, "Anime API error ${resp.code} for $fullUrl: $errorBody")
                        throw IOException("HTTP ${resp.code}: $errorBody")
                    }
                    val bodyString = resp.body?.string().orEmpty()
                    json.decodeFromString<T>(bodyString)
                }
            }
            if (attempt.isSuccess) {
                return@withContext attempt
            } else {
                lastException = attempt.exceptionOrNull()
                Log.w(TAG, "Anime API attempt failed for $fullUrl, trying next endpoint: ${lastException?.message}")
            }
        }
        Log.e(TAG, "Anime API failure for $endpoint on all base URLs", lastException)
        Result.failure(lastException ?: IOException("Network failure for $endpoint"))
    }

    suspend fun getHomeData(): Result<AnimeHomeResponse> =
        fetch(AnimeBackendConfig.HOME_ENDPOINT)

    suspend fun getSeriesData(slug: String): Result<AnimeSeriesResponse> {
        val cleanSlug = Uri.encode(slug.trim().removePrefix("/series/").trim('/'))
        return fetch("api/series/$cleanSlug")
    }

    suspend fun getCategoryData(categoryType: String, slug: String, page: Int = 1): Result<AnimeCategoryResponse> {
        val cleanType = Uri.encode(categoryType.trim())
        val cleanSlug = Uri.encode(slug.trim().removePrefix("/category/").removePrefix("$categoryType/").trim('/'))
        val query = if (page > 1) "?page=$page" else ""
        return fetch("api/category/$cleanType/$cleanSlug$query")
    }

    suspend fun getMovieData(slug: String): Result<AnimeMovieResponse> {
        val cleanSlug = Uri.encode(slug.trim().removePrefix("/movie/").trim('/'))
        return fetch("api/movie/$cleanSlug")
    }

    suspend fun getEpisodeData(slug: String): Result<AnimeEpisodeResponse> {
        val cleanSlug = Uri.encode(slug.trim().removePrefix("/episode/").trim('/'))
        return fetch("api/episode/$cleanSlug")
    }
}
