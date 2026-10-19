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

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SubtitlePreferences @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    companion object {
        private const val PREFS_NAME = "metflix_subtitle_prefs"
        private const val KEY_SUBTITLES_ENABLED = "subtitles_enabled"
        private const val KEY_PREFERRED_LANGUAGE = "preferred_subtitle_language"

        val AVAILABLE_SUBTITLE_LANGUAGES = listOf("English", "Hindi", "Spanish", "French", "German")
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _isSubtitlesEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_SUBTITLES_ENABLED, true)
    )
    val isSubtitlesEnabled: StateFlow<Boolean> = _isSubtitlesEnabled.asStateFlow()

    private val _preferredLanguage = MutableStateFlow(
        prefs.getString(KEY_PREFERRED_LANGUAGE, "English") ?: "English"
    )
    val preferredLanguage: StateFlow<String> = _preferredLanguage.asStateFlow()

    fun setSubtitlesEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SUBTITLES_ENABLED, enabled).apply()
        _isSubtitlesEnabled.value = enabled
    }

    fun setPreferredLanguage(language: String) {
        prefs.edit().putString(KEY_PREFERRED_LANGUAGE, language).apply()
        _preferredLanguage.value = language
    }
}
