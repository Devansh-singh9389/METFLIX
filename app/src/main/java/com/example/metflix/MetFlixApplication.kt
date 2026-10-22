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

package com.example.metflix

import android.app.Application
import android.content.ComponentCallbacks2
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.example.metflix.data.repositories.LiveTvRepository
import com.example.metflix.data.repositories.LiveTvRepositoryImpl
import com.example.metflix.data.repositories.MovieRepository
import com.example.metflix.data.repositories.MovieRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent

import com.example.metflix.data.network.SecureHttpClient
import com.example.metflix.data.player.MediaCacheManager
import com.example.metflix.data.settings.PerformancePreferences
import javax.inject.Inject

@HiltAndroidApp(Application::class)
class MetFlixApplication : Hilt_MetFlixApplication(), ImageLoaderFactory {

    @Inject
    lateinit var performancePreferences: PerformancePreferences

    private var imageLoaderRef: ImageLoader? = null

    override fun onCreate() {
        super.onCreate()
        SecureHttpClient.getClient(this)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            imageLoaderRef?.memoryCache?.clear()
            MediaCacheManager.trimCacheToSize(this, 20L * 1024 * 1024)
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        imageLoaderRef?.memoryCache?.clear()
        MediaCacheManager.trimCacheToSize(this, 10L * 1024 * 1024)
    }

    override fun newImageLoader(): ImageLoader {
        val loader = ImageLoader.Builder(this)
            .okHttpClient {
                SecureHttpClient.getClient(this)
            }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.15)
                    .strongReferencesEnabled(true)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(40L * 1024 * 1024)
                    .build()
            }
            .respectCacheHeaders(false)
            .crossfade(false)
            .allowHardware(true)
            .allowRgb565(true)
            .networkCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .build()
        imageLoaderRef = loader
        return loader
    }
}

@InstallIn(SingletonComponent::class)
@Module
abstract class MovieRepositoryModule {

    @Binds
    abstract fun bindMovieRepository(
        movieRepositoryImpl: MovieRepositoryImpl
    ): MovieRepository

    @Binds
    abstract fun bindLiveTvRepository(
        liveTvRepositoryImpl: LiveTvRepositoryImpl
    ): LiveTvRepository
}
