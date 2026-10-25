package com.example.metflix.data.repositories

import android.content.Context
import android.util.Log
import com.example.metflix.data.network.anime.models.AnimeHomeResponse
import com.example.metflix.data.network.anime.models.AnimeMovieResponse
import com.example.metflix.data.network.anime.models.AnimeSeriesResponse
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
        private const val DETAILS_CACHE_DIR = "anime_details_cache"
        const val CACHE_TTL_MS = 2 * 60 * 60 * 1000L // 2 hours
        const val DETAILS_TTL_MS = 4 * 60 * 60 * 1000L // 4 hours
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = true
    }

    private val cacheFile: File
        get() = File(context.filesDir, CACHE_FILE_NAME)

    private val detailsDir: File
        get() = File(context.filesDir, DETAILS_CACHE_DIR).apply { if (!exists()) mkdirs() }

    private fun sanitizeSlug(slug: String): String =
        slug.replace(Regex("""[^a-zA-Z0-9_-]"""), "_").take(80)

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

    suspend fun saveSeriesResponse(slug: String, response: AnimeSeriesResponse) = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(detailsDir, "series_${sanitizeSlug(slug)}.json")
            file.writeText(json.encodeToString(response))
            Log.d(TAG, "Cached anime series to disk: ${file.name}")
        }.onFailure {
            Log.w(TAG, "Failed to cache anime series: ${it.message}")
        }
    }

    suspend fun loadSeriesResponse(slug: String, ttlMs: Long = DETAILS_TTL_MS): AnimeSeriesResponse? = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(detailsDir, "series_${sanitizeSlug(slug)}.json")
            if (file.exists() && file.length() > 0) {
                val age = System.currentTimeMillis() - file.lastModified()
                if (age <= ttlMs) {
                    json.decodeFromString<AnimeSeriesResponse>(file.readText())
                } else null
            } else null
        }.getOrNull()
    }

    suspend fun saveMovieResponse(slug: String, response: AnimeMovieResponse) = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(detailsDir, "movie_${sanitizeSlug(slug)}.json")
            file.writeText(json.encodeToString(response))
            Log.d(TAG, "Cached anime movie to disk: ${file.name}")
        }.onFailure {
            Log.w(TAG, "Failed to cache anime movie: ${it.message}")
        }
    }

    suspend fun loadMovieResponse(slug: String, ttlMs: Long = DETAILS_TTL_MS): AnimeMovieResponse? = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(detailsDir, "movie_${sanitizeSlug(slug)}.json")
            if (file.exists() && file.length() > 0) {
                val age = System.currentTimeMillis() - file.lastModified()
                if (age <= ttlMs) {
                    json.decodeFromString<AnimeMovieResponse>(file.readText())
                } else null
            } else null
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
            if (detailsDir.exists()) {
                detailsDir.deleteRecursively()
            }
        }
    }
}
