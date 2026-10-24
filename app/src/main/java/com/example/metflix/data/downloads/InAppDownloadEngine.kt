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
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class InAppDownloadEngine @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val downloadStore: InAppDownloadStore
) {
    companion object {
        private const val TAG = "InAppDownloadEngine"
        private const val BUFFER_SIZE = 128 * 1024 // 128 KB write buffer tuned for TV flash storage
        private const val BROWSER_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

        @Volatile
        var instance: InAppDownloadEngine? = null
            private set
    }

    private val throttledDispatcher = Dispatchers.IO.limitedParallelism(2)
    private val scope = CoroutineScope(throttledDispatcher)

    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val activeCalls = ConcurrentHashMap<String, Call>()
    private val activeStreamsMap = ConcurrentHashMap<String, ActiveDownloadState>()

    private val _activeDownloads = MutableStateFlow<Map<String, ActiveDownloadState>>(emptyMap())
    val activeDownloads: StateFlow<Map<String, ActiveDownloadState>> = _activeDownloads.asStateFlow()

    init {
        instance = this
        restoreTasksFromStore()
    }

    private fun restoreTasksFromStore() {
        val tasks = downloadStore.storedTasks.value
        tasks.forEach { task ->
            if (task.status == "RUNNING" || task.status == "PAUSED") {
                val state = ActiveDownloadState(
                    downloadId = task.downloadId,
                    mediaId = task.mediaId,
                    title = task.title,
                    bytesDownloaded = task.downloadedBytes,
                    totalBytes = task.totalBytes,
                    progressPercent = if (task.totalBytes > 0) ((task.downloadedBytes * 100) / task.totalBytes).toInt().coerceIn(0, 100) else 0,
                    speedBps = 0L,
                    etaSeconds = 0L,
                    status = DownloadStatus.PAUSED
                )
                activeStreamsMap[task.mediaId] = state
            }
        }
        _activeDownloads.value = activeStreamsMap.toMap()
    }

    fun enqueueDownload(
        mediaId: String,
        title: String,
        directUrl: String,
        targetFilePath: String,
        quality: String = "1080p",
        languages: List<String> = emptyList()
    ): Long {
        val downloadId = System.currentTimeMillis()
        val partFilePath = if (targetFilePath.endsWith(".part")) targetFilePath else "$targetFilePath.part"

        val initialTask = StoredDownloadTask(
            downloadId = downloadId,
            mediaId = mediaId,
            title = title,
            directUrl = directUrl,
            localFilePath = targetFilePath,
            partFilePath = partFilePath,
            downloadedBytes = 0L,
            totalBytes = 0L,
            quality = quality,
            languages = languages,
            status = "RUNNING"
        )
        downloadStore.saveOrUpdateTask(initialTask)

        startDownloadStream(initialTask)
        return downloadId
    }

    fun pauseDownload(mediaId: String) {
        activeCalls[mediaId]?.cancel()
        activeCalls.remove(mediaId)
        activeJobs[mediaId]?.cancel()
        activeJobs.remove(mediaId)

        activeStreamsMap[mediaId]?.let { current ->
            val updated = current.copy(status = DownloadStatus.PAUSED, speedBps = 0L)
            activeStreamsMap[mediaId] = updated
            _activeDownloads.value = activeStreamsMap.toMap()

            downloadStore.storedTasks.value.find { it.mediaId == mediaId }?.let { stored ->
                downloadStore.saveOrUpdateTask(stored.copy(status = "PAUSED", downloadedBytes = current.bytesDownloaded))
            }
        }
        InAppDownloadService.checkAutoStop(context)
    }

    fun resumeDownload(mediaId: String) {
        val stored = downloadStore.storedTasks.value.find { it.mediaId == mediaId } ?: return
        startDownloadStream(stored)
    }

    fun cancelDownload(mediaId: String) {
        activeCalls[mediaId]?.cancel()
        activeCalls.remove(mediaId)
        activeJobs[mediaId]?.cancel()
        activeJobs.remove(mediaId)

        activeStreamsMap[mediaId]?.let { state ->
            try {
                val stored = downloadStore.storedTasks.value.find { it.mediaId == mediaId }
                val partPath = stored?.partFilePath ?: "${state.mediaId}.part"
                val partFile = File(partPath)
                if (partFile.exists()) partFile.delete()
            } catch (_: Exception) {}
        }

        activeStreamsMap.remove(mediaId)
        _activeDownloads.value = activeStreamsMap.toMap()
        downloadStore.removeTask(mediaId)

        InAppDownloadService.checkAutoStop(context)
    }

    private fun startDownloadStream(task: StoredDownloadTask) {
        activeJobs[task.mediaId]?.cancel()

        InAppDownloadService.startService(context, task.title)

        val job = scope.launch {
            downloadLoop(task)
        }
        activeJobs[task.mediaId] = job
    }

    private suspend fun downloadLoop(task: StoredDownloadTask) = withContext(throttledDispatcher) {
        val partFile = File(task.partFilePath)
        partFile.parentFile?.mkdirs()

        var downloadedBytes = if (partFile.exists()) partFile.length() else 0L
        var totalBytes = task.totalBytes
        var eTag = task.eTag

        val reqBuilder = Request.Builder()
            .url(task.directUrl)
            .header("User-Agent", BROWSER_USER_AGENT)

        if (downloadedBytes > 0) {
            reqBuilder.header("Range", "bytes=$downloadedBytes-")
            if (eTag.isNotBlank()) {
                reqBuilder.header("If-Range", eTag)
            }
        }

        val call = okHttpClient.newCall(reqBuilder.build())
        activeCalls[task.mediaId] = call

        try {
            call.execute().use { response ->
                val code = response.code
                if (!response.isSuccessful && code != 206) {
                    Log.e(TAG, "Download failed HTTP $code for ${task.title}")
                    updateState(task, downloadedBytes, totalBytes, 0L, 0L, DownloadStatus.FAILED)
                    return@withContext
                }

                val newETag = response.header("ETag").orEmpty()
                if (newETag.isNotBlank()) eTag = newETag

                val contentLength = response.body?.contentLength() ?: 0L

                val isPartial = code == 206
                if (!isPartial && downloadedBytes > 0) {
                    Log.w(TAG, "Server returned 200 OK instead of 206 Partial Content. Truncating file to byte 0.")
                    downloadedBytes = 0L
                }

                if (totalBytes <= 0) {
                    totalBytes = if (isPartial) downloadedBytes + contentLength else contentLength
                }

                // Sparse File Pre-Allocation
                var raf: RandomAccessFile? = null
                try {
                    raf = RandomAccessFile(partFile, "rw")
                    if (!isPartial) {
                        raf.setLength(0)
                        if (totalBytes > 0) {
                            try {
                                raf.setLength(totalBytes)
                            } catch (e: Exception) {
                                Log.e(TAG, "Sparse pre-allocation ENOSPC error: ${e.message}")
                            }
                        }
                    }
                    raf.seek(downloadedBytes)

                    val inputStream = response.body?.byteStream()
                    if (inputStream == null) {
                        updateState(task, downloadedBytes, totalBytes, 0L, 0L, DownloadStatus.FAILED)
                        return@withContext
                    }

                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesRead: Int
                    var lastUpdate = System.currentTimeMillis()
                    var bytesSinceUpdate = 0L
                    var lastSavedPercent = if (totalBytes > 0) ((downloadedBytes * 100) / totalBytes).toInt() else 0

                    updateState(task, downloadedBytes, totalBytes, 0L, 0L, DownloadStatus.RUNNING)

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        raf.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        bytesSinceUpdate += bytesRead

                        val now = System.currentTimeMillis()
                        val timeDiff = now - lastUpdate
                        if (timeDiff >= 500L) { // 500ms UI emission throttling to eliminate Compose TV frame drops
                            val speedBps = (bytesSinceUpdate * 1000L) / timeDiff
                            val remainingBytes = (totalBytes - downloadedBytes).coerceAtLeast(0L)
                            val etaSeconds = if (speedBps > 0) remainingBytes / speedBps else 0L

                            updateState(task, downloadedBytes, totalBytes, speedBps, etaSeconds, DownloadStatus.RUNNING)

                            val currentPercent = if (totalBytes > 0) ((downloadedBytes * 100) / totalBytes).toInt() else 0
                            if (currentPercent - lastSavedPercent >= 5) {
                                lastSavedPercent = currentPercent
                                downloadStore.saveOrUpdateTask(
                                    task.copy(
                                        downloadedBytes = downloadedBytes,
                                        totalBytes = totalBytes,
                                        eTag = eTag,
                                        status = "RUNNING"
                                    )
                                )
                            }

                            lastUpdate = now
                            bytesSinceUpdate = 0L
                        }
                    }

                    // Complete! Atomic rename .part file to final target file with copyTo fallback
                    raf.close()
                    val targetFile = File(task.localFilePath)
                    targetFile.parentFile?.mkdirs()
                    if (targetFile.exists()) targetFile.delete()

                    var renamed = partFile.renameTo(targetFile)
                    if (!renamed) {
                        try {
                            partFile.copyTo(targetFile, overwrite = true)
                            partFile.delete()
                            renamed = true
                        } catch (e: Exception) {
                            Log.e(TAG, "Cross-filesystem copyTo fallback failed: ${e.message}")
                        }
                    }
                    Log.d(TAG, "Download finished! Renamed .part to ${targetFile.name} (Success: $renamed)")

                    updateState(task, downloadedBytes, totalBytes, 0L, 0L, DownloadStatus.COMPLETED)
                    activeStreamsMap.remove(task.mediaId)
                    _activeDownloads.value = activeStreamsMap.toMap()

                    downloadStore.saveOrUpdateTask(
                        task.copy(
                            downloadedBytes = downloadedBytes,
                            totalBytes = totalBytes,
                            status = "COMPLETED"
                        )
                    )
                } finally {
                    try { raf?.close() } catch (_: Exception) {}
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Stream error for ${task.title}: ${e.message}")
            updateState(task, downloadedBytes, totalBytes, 0L, 0L, DownloadStatus.FAILED)
        } finally {
            InAppDownloadService.checkAutoStop(context)
        }
    }

    private fun updateState(
        task: StoredDownloadTask,
        downloaded: Long,
        total: Long,
        speedBps: Long,
        etaSeconds: Long,
        status: DownloadStatus
    ) {
        val percent = if (total > 0) ((downloaded * 100) / total).toInt().coerceIn(0, 100) else 0
        val state = ActiveDownloadState(
            downloadId = task.downloadId,
            mediaId = task.mediaId,
            title = task.title,
            bytesDownloaded = downloaded,
            totalBytes = total,
            progressPercent = percent,
            speedBps = speedBps,
            etaSeconds = etaSeconds,
            status = status
        )
        activeStreamsMap[task.mediaId] = state
        _activeDownloads.value = activeStreamsMap.toMap()
    }
}
