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

package com.example.metflix.presentation.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.metflix.data.network.tmdb.TmdbApiClient
import com.example.metflix.data.settings.PerformancePreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PerformanceViewModel @Inject constructor(
    private val preferences: PerformancePreferences,
    private val tmdbApiClient: TmdbApiClient
) : ViewModel() {

    val isDeviceLowRam: Boolean = preferences.isDeviceLowRam

    val lowSpecMode: StateFlow<Boolean> = preferences.lowSpecMode
    val tmdbEnabled: StateFlow<Boolean> = preferences.tmdbEnabled
    val highQualityImages: StateFlow<Boolean> = preferences.highQualityImages
    val reduceAnimations: StateFlow<Boolean> = preferences.reduceAnimations

    fun toggleLowSpecMode(enabled: Boolean) {
        preferences.setLowSpecMode(enabled)
        if (!enabled) {
            tmdbApiClient.resetCircuit()
        }
    }

    fun toggleTmdb(enabled: Boolean) {
        preferences.setTmdbEnabled(enabled)
        if (enabled) {
            tmdbApiClient.resetCircuit()
        }
    }

    fun toggleHighQualityImages(enabled: Boolean) {
        preferences.setHighQualityImages(enabled)
    }

    fun toggleReduceAnimations(enabled: Boolean) {
        preferences.setReduceAnimations(enabled)
    }

    fun clearCache() {
        viewModelScope.launch {
            preferences.clearCaches()
            tmdbApiClient.resetCircuit()
        }
    }
}
