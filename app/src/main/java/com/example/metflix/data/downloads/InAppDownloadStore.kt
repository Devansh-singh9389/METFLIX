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
import androidx.annotation.Keep
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Keep
data class StoredDownloadTask(
    val downloadId: Long,
    val mediaId: String,
    val title: String,
    val directUrl: String,
    val localFilePath: String,
    val partFilePath: String,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val eTag: String = "",
    val quality: String = "1080p",
    val languages: List<String> = emptyList(),
    val status: String = "PAUSED",
    val timestamp: Long = System.currentTimeMillis(),
    val mediaType: String = "MOVIE",
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val posterUrl: String = "",
    val catalogJson: String = ""
)

@Singleton
class InAppDownloadStore @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    companion object {
        private const val PREFS_NAME = "metflix_inapp_download_store"
        private const val KEY_TASKS = "json_inapp_download_tasks"

        @Volatile
        var instance: InAppDownloadStore? = null
            private set
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _storedTasks = MutableStateFlow<List<StoredDownloadTask>>(loadTasks())
    val storedTasks: StateFlow<List<StoredDownloadTask>> = _storedTasks.asStateFlow()

    init {
        instance = this
    }

    private fun loadTasks(): List<StoredDownloadTask> {
        val jsonStr = prefs.getString(KEY_TASKS, null) ?: return emptyList()
        return try {
            val array = JSONArray(jsonStr)
            val list = mutableListOf<StoredDownloadTask>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    StoredDownloadTask(
                        downloadId = obj.optLong("downloadId", -1L),
                        mediaId = obj.getString("mediaId"),
                        title = obj.getString("title"),
                        directUrl = obj.getString("directUrl"),
                        localFilePath = obj.getString("localFilePath"),
                        partFilePath = obj.optString("partFilePath", "${obj.getString("localFilePath")}.part"),
                        downloadedBytes = obj.optLong("downloadedBytes", 0L),
                        totalBytes = obj.optLong("totalBytes", 0L),
                        eTag = obj.optString("eTag", ""),
                        quality = obj.optString("quality", "1080p"),
                        languages = optStringList(obj.optJSONArray("languages")),
                        status = obj.optString("status", "PAUSED"),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        mediaType = obj.optString("mediaType", "MOVIE"),
                        seasonNumber = if (obj.has("seasonNumber")) obj.optInt("seasonNumber") else null,
                        episodeNumber = if (obj.has("episodeNumber")) obj.optInt("episodeNumber") else null,
                        posterUrl = obj.optString("posterUrl", ""),
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

    private fun saveTasks(tasks: List<StoredDownloadTask>) {
        val array = JSONArray()
        tasks.forEach { task ->
            val obj = JSONObject().apply {
                put("downloadId", task.downloadId)
                put("mediaId", task.mediaId)
                put("title", task.title)
                put("directUrl", task.directUrl)
                put("localFilePath", task.localFilePath)
                put("partFilePath", task.partFilePath)
                put("downloadedBytes", task.downloadedBytes)
                put("totalBytes", task.totalBytes)
                put("eTag", task.eTag)
                put("quality", task.quality)
                put("languages", JSONArray(task.languages))
                put("status", task.status)
                put("timestamp", task.timestamp)
                put("mediaType", task.mediaType)
                task.seasonNumber?.let { put("seasonNumber", it) }
                task.episodeNumber?.let { put("episodeNumber", it) }
                put("posterUrl", task.posterUrl)
                put("catalogJson", task.catalogJson)
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_TASKS, array.toString()).apply()
        _storedTasks.value = tasks
    }

    fun saveOrUpdateTask(task: StoredDownloadTask) {
        val current = _storedTasks.value.toMutableList()
        current.removeAll { it.mediaId == task.mediaId || it.downloadId == task.downloadId }
        current.add(0, task)
        saveTasks(current)
    }

    fun removeTask(mediaId: String) {
        val current = _storedTasks.value.toMutableList()
        current.removeAll { it.mediaId == mediaId }
        saveTasks(current)
    }
}
