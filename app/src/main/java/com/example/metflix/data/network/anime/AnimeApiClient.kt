package com.example.metflix.data.network.anime

import android.util.Log
import com.example.metflix.data.network.anime.models.AnimeHomeResponse
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

    suspend fun getHomeData(): Result<AnimeHomeResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "${AnimeBackendConfig.BASE_URL.trimEnd('/')}/${AnimeBackendConfig.HOME_ENDPOINT.trimStart('/')}"
            Log.d(TAG, "Fetching Anime Home data from: $url")

            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("User-Agent", "MetFlix-AndroidTV/1.0")
                .build()

            val response = animeHttpClient.newCall(request).execute()
            response.use { resp ->
                if (!resp.isSuccessful) {
                    val errorBody = resp.body?.string().orEmpty()
                    Log.w(TAG, "Anime Home request failed with HTTP ${resp.code}: $errorBody")
                    throw IOException("HTTP ${resp.code}: $errorBody")
                }
                val bodyString = resp.body?.string().orEmpty()
                json.decodeFromString<AnimeHomeResponse>(bodyString)
            }
        }.onSuccess {
            Log.d(TAG, "Successfully fetched Anime Home data (${it.newestDrops.size} newest drops, ${it.newAnimeArrivals.size} arrivals)")
        }.onFailure { err ->
            Log.e(TAG, "Error fetching Anime Home data", err)
        }
    }
}
