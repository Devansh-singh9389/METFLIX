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
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class AppLanguage(
    val code: String,
    val displayName: String
)

@Singleton
class LanguagePreferences @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    companion object {
        private const val PREFS_NAME = "metflix_language_prefs"
        private const val KEY_LANGUAGE_CODE = "selected_language_code"

        val AVAILABLE_LANGUAGES = listOf(
            AppLanguage(code = "en-US", displayName = "English (US)"),
            AppLanguage(code = "en-GB", displayName = "English (UK)"),
            AppLanguage(code = "hi", displayName = "हिन्दी (Hindi)"),
            AppLanguage(code = "fr", displayName = "Français"),
            AppLanguage(code = "es", displayName = "Español")
        )
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _currentLanguage = MutableStateFlow(
        prefs.getString(KEY_LANGUAGE_CODE, "en-US") ?: "en-US"
    )
    val currentLanguage: StateFlow<String> = _currentLanguage.asStateFlow()

    init {
        // Apply saved locale to app configuration on launch
        val saved = _currentLanguage.value
        applyLocale(saved)
    }

    fun setLanguage(languageCode: String) {
        prefs.edit().putString(KEY_LANGUAGE_CODE, languageCode).apply()
        _currentLanguage.value = languageCode
        applyLocale(languageCode)
    }

    private fun applyLocale(languageCode: String) {
        try {
            val appLocale = LocaleListCompat.forLanguageTags(languageCode)
            AppCompatDelegate.setApplicationLocales(appLocale)
        } catch (_: Exception) { }
    }
}
