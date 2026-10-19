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

package com.example.metflix.data.settings

import android.app.ActivityManager
import android.content.Context
import android.content.SharedPreferences
import coil.Coil
import com.example.metflix.data.network.tmdb.TmdbConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PerformancePreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val PREFS_NAME = "metflix_performance_prefs"
        private const val KEY_LOW_SPEC_MODE = "key_low_spec_mode"
        private const val KEY_TMDB_ENABLED = "key_tmdb_enabled"
        private const val KEY_HIGH_QUALITY_IMAGES = "key_high_quality_images"
        private const val KEY_REDUCE_ANIMATIONS = "key_reduce_animations"
        private const val KEY_MAX_CHANNELS = "key_max_channels"

        @Volatile
        var instance: PerformancePreferences? = null
            private set

        val isHighQualityImagesActive: Boolean
            get() = instance?.highQualityImages?.value ?: false

        val isLowSpecModeActive: Boolean
            get() = instance?.lowSpecMode?.value ?: true
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Detect device hardware capability (low RAM device <= 1GB RAM)
    val isDeviceLowRam: Boolean by lazy {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        am?.isLowRamDevice ?: true
    }

    // Default to Low-Spec Mode if device is low RAM (like 512MB RAM TV sticks)
    private val defaultLowSpecMode: Boolean get() = isDeviceLowRam

    private val _lowSpecMode = MutableStateFlow(
        prefs.getBoolean(KEY_LOW_SPEC_MODE, defaultLowSpecMode)
    )
    val lowSpecMode: StateFlow<Boolean> = _lowSpecMode.asStateFlow()

    private val _tmdbEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_TMDB_ENABLED, !defaultLowSpecMode)
    )
    val tmdbEnabled: StateFlow<Boolean> = _tmdbEnabled.asStateFlow()

    private val _highQualityImages = MutableStateFlow(
        prefs.getBoolean(KEY_HIGH_QUALITY_IMAGES, !defaultLowSpecMode)
    )
    val highQualityImages: StateFlow<Boolean> = _highQualityImages.asStateFlow()

    private val _reduceAnimations = MutableStateFlow(
        prefs.getBoolean(KEY_REDUCE_ANIMATIONS, defaultLowSpecMode)
    )
    val reduceAnimations: StateFlow<Boolean> = _reduceAnimations.asStateFlow()

    private val _maxChannels = MutableStateFlow(
        prefs.getInt(KEY_MAX_CHANNELS, if (defaultLowSpecMode) 3500 else 5000)
    )
    val maxChannels: StateFlow<Int> = _maxChannels.asStateFlow()

    init {
        instance = this
        syncConfig()
    }

    fun setLowSpecMode(enabled: Boolean) {
        prefs.edit()
            .putBoolean(KEY_LOW_SPEC_MODE, enabled)
            .putBoolean(KEY_TMDB_ENABLED, !enabled)
            .putBoolean(KEY_HIGH_QUALITY_IMAGES, !enabled)
            .putBoolean(KEY_REDUCE_ANIMATIONS, enabled)
            .putInt(KEY_MAX_CHANNELS, if (enabled) 3500 else 5000)
            .apply()

        _lowSpecMode.value = enabled
        _tmdbEnabled.value = !enabled
        _highQualityImages.value = !enabled
        _reduceAnimations.value = enabled
        _maxChannels.value = if (enabled) 3500 else 5000

        syncConfig()
    }

    fun setTmdbEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_TMDB_ENABLED, enabled).apply()
        _tmdbEnabled.value = enabled
        // If enabling TMDB while in lowSpecMode, disengage pure lowSpec mode flag if both graphics & network enabled
        if (enabled && _highQualityImages.value) {
            _lowSpecMode.value = false
            prefs.edit().putBoolean(KEY_LOW_SPEC_MODE, false).apply()
        }
        syncConfig()
    }

    fun setHighQualityImages(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HIGH_QUALITY_IMAGES, enabled).apply()
        _highQualityImages.value = enabled
        if (enabled && _tmdbEnabled.value) {
            _lowSpecMode.value = false
            prefs.edit().putBoolean(KEY_LOW_SPEC_MODE, false).apply()
        }
        syncConfig()
    }

    fun setReduceAnimations(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_REDUCE_ANIMATIONS, enabled).apply()
        _reduceAnimations.value = enabled
        syncConfig()
    }

    fun clearCaches() {
        try {
            val imageLoader = Coil.imageLoader(context)
            imageLoader.memoryCache?.clear()
            imageLoader.diskCache?.clear()
        } catch (_: Exception) { }
    }

    private fun syncConfig() {
        TmdbConfig.USE_TMDB = _tmdbEnabled.value
        val isHighQuality = _highQualityImages.value && !_lowSpecMode.value
        if (isHighQuality) {
            TmdbConfig.IMAGE_BASE_URL_POSTER = "https://image.tmdb.org/t/p/w500"
            TmdbConfig.IMAGE_BASE_URL_BACKDROP = "https://image.tmdb.org/t/p/w780"
        } else {
            // Low-spec / TV-optimized: w342 for posters and w300 for backdrops saves ~65% GPU memory and speeds up decode
            TmdbConfig.IMAGE_BASE_URL_POSTER = "https://image.tmdb.org/t/p/w342"
            TmdbConfig.IMAGE_BASE_URL_BACKDROP = "https://image.tmdb.org/t/p/w300"
        }
    }
}
