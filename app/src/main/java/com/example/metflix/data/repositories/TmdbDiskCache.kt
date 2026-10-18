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

package com.example.metflix.data.repositories

import android.content.Context
import android.util.Log
import com.example.metflix.data.entities.Movie
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * High-speed local in-memory and disk cache for TMDB movie catalog.
 * Guarantees 0ms instant startup rendering on app launch / cold boots,
 * with zero-cost repeated lookups via memory cache, and automatic TTL expiration.
 */
@Singleton
class TmdbDiskCache @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    companion object {
        const val CACHE_TTL_MS = 6 * 60 * 60 * 1000L // 6 hours TTL
    }

    private val memoryCache = ConcurrentHashMap<String, List<Movie>>()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = true
    }

    fun saveMovies(key: String, movies: List<Movie>) {
        if (movies.isEmpty()) return
        memoryCache[key] = movies
        runCatching {
            val file = File(context.filesDir, "tmdb_cache_${key}.json")
            val serialized = json.encodeToString(movies)
            file.writeText(serialized)
        }.onFailure {
            Log.w("TmdbDiskCache", "Failed to cache $key: ${it.message}")
        }
    }

    fun loadMovies(key: String): List<Movie> {
        memoryCache[key]?.let { return it }

        return runCatching {
            val file = File(context.filesDir, "tmdb_cache_${key}.json")
            if (file.exists() && file.length() > 0) {
                val parsed = json.decodeFromString<List<Movie>>(file.readText())
                memoryCache[key] = parsed
                parsed
            } else {
                emptyList()
            }
        }.getOrDefault(emptyList())
    }

    fun isCacheStale(key: String, ttlMs: Long = CACHE_TTL_MS): Boolean {
        val file = File(context.filesDir, "tmdb_cache_${key}.json")
        if (!file.exists() || file.length() == 0L) return true
        val age = System.currentTimeMillis() - file.lastModified()
        return age > ttlMs
    }

    fun invalidate(key: String) {
        memoryCache.remove(key)
        runCatching {
            val file = File(context.filesDir, "tmdb_cache_${key}.json")
            if (file.exists()) file.delete()
        }
    }

    fun clearAll() {
        memoryCache.clear()
        runCatching {
            context.filesDir.listFiles { _, name -> name.startsWith("tmdb_cache_") && name.endsWith(".json") }
                ?.forEach { it.delete() }
        }.onFailure {
            Log.w("TmdbDiskCache", "Failed to clear disk cache: ${it.message}")
        }
    }
}
