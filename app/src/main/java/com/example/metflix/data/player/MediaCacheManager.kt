/*
 * Copyright 2026 Google LLC
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

package com.example.metflix.data.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

/**
 * Singleton MediaCacheManager that manages a bounded disk cache for Media3 ExoPlayer.
 * Prevents in-RAM video buffer exhaustion on ultra-low-memory Android TV devices (512 MB RAM)
 * by streaming media chunks (both HLS .ts fragments and progressive MP4s) directly to disk.
 */
@OptIn(UnstableApi::class)
object MediaCacheManager {

    private const val MAX_CACHE_BYTES = 60L * 1024 * 1024 // 60 MB media disk cache limit
    private const val CACHE_DIR_NAME = "anime_media_cache"

    @Volatile
    private var simpleCache: SimpleCache? = null

    @Synchronized
    fun getCache(context: Context): SimpleCache {
        return simpleCache ?: synchronized(this) {
            simpleCache ?: run {
                val cacheDir = File(context.cacheDir, CACHE_DIR_NAME)
                if (!cacheDir.exists()) {
                    cacheDir.mkdirs()
                }
                val evictor = LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES)
                val databaseProvider = StandaloneDatabaseProvider(context.applicationContext)
                SimpleCache(cacheDir, evictor, databaseProvider).also {
                    simpleCache = it
                }
            }
        }
    }

    /**
     * Builds a CacheDataSource.Factory that wraps upstream HTTP data source (with Referer/Origin headers)
     * and streams content to disk cache without retaining huge memory buffers in the JVM heap.
     */
    fun createCacheDataSourceFactory(
        context: Context,
        upstreamHttpFactory: DefaultHttpDataSource.Factory
    ): DataSource.Factory {
        val cache = getCache(context)
        val defaultDataSourceFactory = DefaultDataSource.Factory(context, upstreamHttpFactory)

        return CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(defaultDataSourceFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    /**
     * Trims the media disk cache down to the target size in bytes.
     */
    fun trimCacheToSize(context: Context, targetBytes: Long) {
        val cache = getCache(context)
        var currentSize = cache.cacheSpace
        if (currentSize > targetBytes) {
            val keys = cache.keys.toList()
            for (key in keys) {
                if (currentSize <= targetBytes) break
                val spans = cache.getCachedSpans(key)
                for (span in spans) {
                    currentSize -= span.length
                    cache.removeSpan(span)
                    if (currentSize <= targetBytes) break
                }
            }
        }
    }

    /**
     * Clears all cached video chunks from disk.
     */
    fun clearCache(context: Context) {
        val cache = getCache(context)
        cache.keys.toList().forEach {
            cache.removeResource(it)
        }
    }
}
