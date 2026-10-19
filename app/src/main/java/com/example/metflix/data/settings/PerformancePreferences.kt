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
    @param:ApplicationContext private val context: Context
) {
    companion object {
        private const val PREFS_NAME = "metflix_performance_prefs"
        private const val KEY_LOW_SPEC_MODE = "key_low_spec_mode"
        private const val KEY_TMDB_ENABLED = "key_tmdb_enabled"
        private const val KEY_HIGH_QUALITY_IMAGES = "key_high_quality_images"
        private const val KEY_REDUCE_ANIMATIONS = "key_reduce_animations"
        private const val KEY_MAX_CHANNELS = "key_max_channels"

        private const val KEY_USER_TMDB_ENABLED = "key_user_tmdb_enabled"
        private const val KEY_USER_HIGH_QUALITY_IMAGES = "key_user_high_quality_images"
        private const val KEY_USER_REDUCE_ANIMATIONS = "key_user_reduce_animations"
        private const val KEY_USER_MAX_CHANNELS = "key_user_max_channels"

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

    private val initialLowSpec = prefs.getBoolean(KEY_LOW_SPEC_MODE, defaultLowSpecMode)
    private val _lowSpecMode = MutableStateFlow(initialLowSpec)
    val lowSpecMode: StateFlow<Boolean> = _lowSpecMode.asStateFlow()

    private val userTmdbPref = prefs.getBoolean(KEY_USER_TMDB_ENABLED, prefs.getBoolean(KEY_TMDB_ENABLED, !defaultLowSpecMode))
    private val userHqPref = prefs.getBoolean(KEY_USER_HIGH_QUALITY_IMAGES, prefs.getBoolean(KEY_HIGH_QUALITY_IMAGES, !defaultLowSpecMode))
    private val userAnimPref = prefs.getBoolean(KEY_USER_REDUCE_ANIMATIONS, prefs.getBoolean(KEY_REDUCE_ANIMATIONS, defaultLowSpecMode))
    private val userMaxChannelsPref = prefs.getInt(KEY_USER_MAX_CHANNELS, prefs.getInt(KEY_MAX_CHANNELS, if (defaultLowSpecMode) 3500 else 5000))

    private val _tmdbEnabled = MutableStateFlow(if (initialLowSpec) false else userTmdbPref)
    val tmdbEnabled: StateFlow<Boolean> = _tmdbEnabled.asStateFlow()

    private val _highQualityImages = MutableStateFlow(if (initialLowSpec) false else userHqPref)
    val highQualityImages: StateFlow<Boolean> = _highQualityImages.asStateFlow()

    private val _reduceAnimations = MutableStateFlow(if (initialLowSpec) true else userAnimPref)
    val reduceAnimations: StateFlow<Boolean> = _reduceAnimations.asStateFlow()

    private val _maxChannels = MutableStateFlow(if (initialLowSpec) 3500 else userMaxChannelsPref)
    val maxChannels: StateFlow<Int> = _maxChannels.asStateFlow()

    init {
        instance = this
        syncConfig()
    }

    fun setLowSpecMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LOW_SPEC_MODE, enabled).apply()
        _lowSpecMode.value = enabled

        if (enabled) {
            _tmdbEnabled.value = false
            _highQualityImages.value = false
            _reduceAnimations.value = true
            _maxChannels.value = 3500
        } else {
            val userTmdb = prefs.getBoolean(KEY_USER_TMDB_ENABLED, true)
            val userHq = prefs.getBoolean(KEY_USER_HIGH_QUALITY_IMAGES, true)
            val userAnim = prefs.getBoolean(KEY_USER_REDUCE_ANIMATIONS, false)
            val userChannels = prefs.getInt(KEY_USER_MAX_CHANNELS, 5000)

            _tmdbEnabled.value = userTmdb
            _highQualityImages.value = userHq
            _reduceAnimations.value = userAnim
            _maxChannels.value = userChannels
        }

        syncConfig()
    }

    fun setTmdbEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_USER_TMDB_ENABLED, enabled).apply()
        _tmdbEnabled.value = enabled
        if (enabled && _lowSpecMode.value) {
            _lowSpecMode.value = false
            prefs.edit().putBoolean(KEY_LOW_SPEC_MODE, false).apply()
        }
        syncConfig()
    }

    fun setHighQualityImages(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_USER_HIGH_QUALITY_IMAGES, enabled).apply()
        _highQualityImages.value = enabled
        if (enabled && _lowSpecMode.value) {
            _lowSpecMode.value = false
            prefs.edit().putBoolean(KEY_LOW_SPEC_MODE, false).apply()
        }
        syncConfig()
    }

    fun setReduceAnimations(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_USER_REDUCE_ANIMATIONS, enabled).apply()
        _reduceAnimations.value = enabled
        syncConfig()
    }

    @OptIn(coil.annotation.ExperimentalCoilApi::class)
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
