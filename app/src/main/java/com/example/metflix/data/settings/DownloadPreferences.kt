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

package com.example.metflix.data.settings

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadPreferences @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    companion object {
        private const val PREFS_NAME = "metflix_download_prefs"
        private const val KEY_DOWNLOAD_LOCATION = "download_location"
        private const val KEY_CUSTOM_LOCATIONS_JSON = "custom_locations_json"
        private const val KEY_PREFERRED_QUALITY = "preferred_quality"
        private const val KEY_PREFERRED_LANGUAGES = "preferred_languages"
        private const val KEY_ALWAYS_ASK = "always_ask_download_dialog"

        val AVAILABLE_QUALITIES = listOf("1080p Full HD", "720p HD", "480p SD", "360p Low", "Always Ask")
        val AVAILABLE_LANGUAGES = listOf("Hindi", "English", "Tamil", "Telugu", "Dual Audio", "All Tracks")
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _downloadLocation = MutableStateFlow(
        prefs.getString(KEY_DOWNLOAD_LOCATION, Environment.DIRECTORY_DOWNLOADS) ?: Environment.DIRECTORY_DOWNLOADS
    )
    val downloadLocation: StateFlow<String> = _downloadLocation.asStateFlow()

    private val _customLocations = MutableStateFlow<List<Pair<String, String>>>(loadCustomLocations())
    val customLocations: StateFlow<List<Pair<String, String>>> = _customLocations.asStateFlow()

    private fun loadCustomLocations(): List<Pair<String, String>> {
        val jsonStr = prefs.getString(KEY_CUSTOM_LOCATIONS_JSON, null) ?: return emptyList()
        return try {
            val arr = JSONArray(jsonStr)
            val list = mutableListOf<Pair<String, String>>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(Pair(obj.getString("name"), obj.getString("uri")))
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun addCustomLocation(name: String, uriString: String) {
        val current = _customLocations.value.toMutableList()
        current.removeAll { it.second == uriString }
        current.add(Pair(name, uriString))
        val arr = JSONArray()
        current.forEach { p ->
            arr.put(JSONObject().apply {
                put("name", p.first)
                put("uri", p.second)
            })
        }
        prefs.edit().putString(KEY_CUSTOM_LOCATIONS_JSON, arr.toString()).apply()
        _customLocations.value = current
        setDownloadLocation(uriString)
    }

    private val _preferredQuality = MutableStateFlow(
        prefs.getString(KEY_PREFERRED_QUALITY, "1080p Full HD") ?: "1080p Full HD"
    )
    val preferredQuality: StateFlow<String> = _preferredQuality.asStateFlow()

    private val _preferredLanguages = MutableStateFlow(
        prefs.getStringSet(KEY_PREFERRED_LANGUAGES, setOf("Hindi", "English")) ?: setOf("Hindi", "English")
    )
    val preferredLanguages: StateFlow<Set<String>> = _preferredLanguages.asStateFlow()

    private val _alwaysAsk = MutableStateFlow(
        prefs.getBoolean(KEY_ALWAYS_ASK, true)
    )
    val alwaysAsk: StateFlow<Boolean> = _alwaysAsk.asStateFlow()

    fun setDownloadLocation(location: String) {
        prefs.edit().putString(KEY_DOWNLOAD_LOCATION, location).apply()
        _downloadLocation.value = location
    }

    fun setPreferredQuality(quality: String) {
        prefs.edit().putString(KEY_PREFERRED_QUALITY, quality).apply()
        _preferredQuality.value = quality
    }

    fun setPreferredLanguages(languages: Set<String>) {
        prefs.edit().putStringSet(KEY_PREFERRED_LANGUAGES, languages).apply()
        _preferredLanguages.value = languages
    }

    fun toggleLanguage(language: String) {
        val current = _preferredLanguages.value.toMutableSet()
        if (current.contains(language)) {
            if (current.size > 1) current.remove(language)
        } else {
            current.add(language)
        }
        setPreferredLanguages(current)
    }

    fun setAlwaysAsk(alwaysAsk: Boolean) {
        prefs.edit().putBoolean(KEY_ALWAYS_ASK, alwaysAsk).apply()
        _alwaysAsk.value = alwaysAsk
    }
}
