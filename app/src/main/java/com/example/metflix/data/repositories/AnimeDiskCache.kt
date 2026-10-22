package com.example.metflix.data.repositories

import android.content.Context
import android.util.Log
import com.example.metflix.data.network.anime.models.AnimeHomeResponse
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Singleton
class AnimeDiskCache @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "AnimeDiskCache"
        private const val CACHE_FILE_NAME = "anime_home_cache.json"
        const val CACHE_TTL_MS = 2 * 60 * 60 * 1000L // 2 hours
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = true
    }

    private val cacheFile: File
        get() = File(context.filesDir, CACHE_FILE_NAME)

    suspend fun saveHomeResponse(response: AnimeHomeResponse) = withContext(Dispatchers.IO) {
        runCatching {
            val serialized = json.encodeToString(response)
            cacheFile.writeText(serialized)
            Log.d(TAG, "Cached anime home response to disk (${cacheFile.length()} bytes)")
        }.onFailure {
            Log.w(TAG, "Failed to cache anime home response: ${it.message}")
        }
    }

    suspend fun loadHomeResponse(): AnimeHomeResponse? = withContext(Dispatchers.IO) {
        runCatching {
            if (cacheFile.exists() && cacheFile.length() > 0) {
                json.decodeFromString<AnimeHomeResponse>(cacheFile.readText())
            } else {
                null
            }
        }.getOrNull()
    }

    suspend fun isCacheStale(ttlMs: Long = CACHE_TTL_MS): Boolean = withContext(Dispatchers.IO) {
        if (!cacheFile.exists() || cacheFile.length() == 0L) return@withContext true
        val age = System.currentTimeMillis() - cacheFile.lastModified()
        age > ttlMs
    }

    suspend fun clearCache() = withContext(Dispatchers.IO) {
        runCatching {
            if (cacheFile.exists()) {
                cacheFile.delete()
            }
        }
    }
}
