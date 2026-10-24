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

package com.example.metflix.data.downloads

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadedCatalogRepository @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    companion object {
        private const val PREFS_NAME = "metflix_downloaded_catalog"
        private const val KEY_ITEMS = "json_downloaded_items"

        @Volatile
        var instance: DownloadedCatalogRepository? = null
            private set
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _downloadedItems = MutableStateFlow<List<DownloadedItem>>(loadItems())
    val downloadedItems: StateFlow<List<DownloadedItem>> = _downloadedItems.asStateFlow()

    init {
        instance = this
    }

    private fun loadItems(): List<DownloadedItem> {
        val jsonStr = prefs.getString(KEY_ITEMS, null) ?: return emptyList()
        return try {
            val array = JSONArray(jsonStr)
            val list = mutableListOf<DownloadedItem>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    DownloadedItem(
                        id = obj.getString("id"),
                        title = obj.getString("title"),
                        mediaType = obj.optString("mediaType", "MOVIE"),
                        mediaId = obj.optString("mediaId", ""),
                        seasonNumber = if (obj.has("seasonNumber")) obj.optInt("seasonNumber") else null,
                        episodeNumber = if (obj.has("episodeNumber")) obj.optInt("episodeNumber") else null,
                        localFilePath = obj.optString("localFilePath", ""),
                        downloadId = obj.optLong("downloadId", -1L),
                        quality = obj.optString("quality", "1080p"),
                        languages = optStringList(obj.optJSONArray("languages")),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        catalogJson = obj.optString("catalogJson", "")
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun optStringList(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        val list = mutableListOf<String>()
        for (i in 0 until array.length()) {
            val s = array.optString(i)
            if (s.isNotBlank()) list.add(s)
        }
        return list
    }

    private fun saveItems(items: List<DownloadedItem>) {
        val array = JSONArray()
        items.forEach { item ->
            val obj = JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("mediaType", item.mediaType)
                put("mediaId", item.mediaId)
                item.seasonNumber?.let { put("seasonNumber", it) }
                item.episodeNumber?.let { put("episodeNumber", it) }
                put("localFilePath", item.localFilePath)
                put("downloadId", item.downloadId)
                put("quality", item.quality)
                put("languages", JSONArray(item.languages))
                put("timestamp", item.timestamp)
                put("catalogJson", item.catalogJson)
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_ITEMS, array.toString()).apply()
        _downloadedItems.value = items
    }

    fun addOrUpdateDownloadedItem(item: DownloadedItem) {
        val current = _downloadedItems.value.toMutableList()
        current.removeAll { it.id == item.id }
        current.add(0, item)
        saveItems(current)
    }

    fun removeDownloadedItem(id: String) {
        val current = _downloadedItems.value.toMutableList()
        current.removeAll { it.id == id }
        saveItems(current)
    }

    fun isDownloaded(mediaId: String, seasonNumber: Int? = null, episodeNumber: Int? = null): Boolean {
        return _downloadedItems.value.any { item ->
            item.mediaId.equals(mediaId, ignoreCase = true) &&
                    (seasonNumber == null || item.seasonNumber == seasonNumber) &&
                    (episodeNumber == null || item.episodeNumber == episodeNumber)
        }
    }
}
