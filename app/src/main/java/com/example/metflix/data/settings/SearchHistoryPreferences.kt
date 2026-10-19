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
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchHistoryPreferences @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    companion object {
        private const val PREFS_NAME = "metflix_search_history"
        private const val KEY_HISTORY = "recent_queries"
        private const val MAX_HISTORY_SIZE = 25
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _history = MutableStateFlow(loadHistory())
    val history: StateFlow<List<String>> = _history.asStateFlow()

    private fun loadHistory(): List<String> {
        val jsonStr = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(jsonStr)
            val list = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                val item = arr.optString(i)
                if (item.isNotBlank()) list.add(item)
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveHistory(list: List<String>) {
        val arr = JSONArray(list)
        prefs.edit().putString(KEY_HISTORY, arr.toString()).apply()
        _history.value = list
    }

    fun addQuery(rawQuery: String) {
        val trimmed = rawQuery.trim()
        if (trimmed.length < 2) return

        val current = _history.value.toMutableList()
        // Remove duplicate case-insensitively
        current.removeAll { it.equals(trimmed, ignoreCase = true) }
        // Prepend to top
        current.add(0, trimmed)
        // Trim to max capacity
        val bounded = current.take(MAX_HISTORY_SIZE)
        saveHistory(bounded)
    }

    fun removeQuery(query: String) {
        val current = _history.value.toMutableList()
        current.removeAll { it.equals(query, ignoreCase = true) }
        saveHistory(current)
    }

    fun clearHistory() {
        prefs.edit().remove(KEY_HISTORY).apply()
        _history.value = emptyList()
    }
}
