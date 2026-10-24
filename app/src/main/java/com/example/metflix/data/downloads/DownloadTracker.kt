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

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadTracker @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "DownloadTracker"

        @Volatile
        var instance: DownloadTracker? = null
            private set
    }

    private val engine: InAppDownloadEngine by lazy {
        InAppDownloadEngine.instance ?: InAppDownloadEngine(
            context,
            OkHttpClient(),
            InAppDownloadStore.instance ?: InAppDownloadStore(context)
        )
    }

    val activeDownloads: StateFlow<Map<String, ActiveDownloadState>>
        get() = engine.activeDownloads

    init {
        instance = this
    }

    fun registerObserver(observerId: String) {
        Log.d(TAG, "DownloadTracker observer registered: $observerId")
    }

    fun unregisterObserver(observerId: String) {
        Log.d(TAG, "DownloadTracker observer unregistered: $observerId")
    }

    fun pauseDownload(downloadId: Long) {
        val mediaId = activeDownloads.value.values.find { it.downloadId == downloadId }?.mediaId
        if (mediaId != null) {
            engine.pauseDownload(mediaId)
        }
    }

    fun resumeDownload(downloadId: Long) {
        val mediaId = activeDownloads.value.values.find { it.downloadId == downloadId }?.mediaId
        if (mediaId != null) {
            engine.resumeDownload(mediaId)
        }
    }

    fun cancelDownload(downloadId: Long) {
        val mediaId = activeDownloads.value.values.find { it.downloadId == downloadId }?.mediaId
        if (mediaId != null) {
            engine.cancelDownload(mediaId)
        }
    }

    fun pauseDownloadByMediaId(mediaId: String) {
        engine.pauseDownload(mediaId)
    }

    fun resumeDownloadByMediaId(mediaId: String) {
        engine.resumeDownload(mediaId)
    }

    fun cancelDownloadByMediaId(mediaId: String) {
        engine.cancelDownload(mediaId)
    }

    fun findActiveDownload(vararg keys: String?): ActiveDownloadState? {
        val map = activeDownloads.value
        for (k in keys) {
            if (!k.isNullOrBlank()) {
                val direct = map[k]
                if (direct != null) return direct
            }
        }
        val nonNullKeys = keys.filterNotNull().filter { it.isNotBlank() }
        if (nonNullKeys.isEmpty()) return null
        return map.values.find { state ->
            nonNullKeys.any { k ->
                state.title.equals(k, ignoreCase = true) ||
                state.mediaId.equals(k, ignoreCase = true) ||
                state.mediaId.startsWith("${k}_") ||
                k.startsWith("${state.mediaId}_")
            }
        }
    }
}
