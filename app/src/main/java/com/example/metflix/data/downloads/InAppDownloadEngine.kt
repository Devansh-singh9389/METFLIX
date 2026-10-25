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
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.os.StatFs
import com.example.metflix.data.player.MediaCacheManager
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
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
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(UnstableApi::class)
@Singleton
class InAppDownloadEngine @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val downloadStore: InAppDownloadStore
) {
    companion object {
        private const val TAG = "InAppDownloadEngine"

        @Volatile
        var instance: InAppDownloadEngine? = null
            private set
    }

    private val media3Manager: DownloadManager by lazy {
        Media3DownloadManagerProvider.get(context)
    }

    private val scope = CoroutineScope(Dispatchers.Main.immediate)
    private var progressPollingJob: Job? = null

    private val activeStreamsMap = ConcurrentHashMap<String, ActiveDownloadState>()
    private val _activeDownloads = MutableStateFlow<Map<String, ActiveDownloadState>>(emptyMap())
    val activeDownloads: StateFlow<Map<String, ActiveDownloadState>> = _activeDownloads.asStateFlow()

    private fun updateActiveDownloadsState() {
        val map = mutableMapOf<String, ActiveDownloadState>()
        for ((_, state) in activeStreamsMap) {
            map[state.mediaId] = state
        }
        _activeDownloads.value = map
    }

    init {
        instance = this
        restoreTasksFromStore()
        setupMedia3Listener()
        startProgressPolling()
        registerNetworkCallback()
    }

    private fun setupMedia3Listener() {
        media3Manager.addListener(object : DownloadManager.Listener {
            override fun onDownloadChanged(
                downloadManager: DownloadManager,
                download: Download,
                finalException: Exception?
            ) {
                if (finalException != null) {
                    Log.e(TAG, "Media3 download error for ${download.request.id}: ${finalException.message}", finalException)
                }
                syncDownloadState(download)
            }

            override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) {
                val mediaId = download.request.id
                activeStreamsMap.remove(mediaId)
                updateActiveDownloadsState()
                val stored = downloadStore.storedTasks.value.find { it.mediaId == mediaId }
                if (stored != null) {
                    try {
                        val file = java.io.File(stored.localFilePath)
                        if (file.exists()) file.delete()
                        val partFile = java.io.File(stored.partFilePath)
                        if (partFile.exists()) partFile.delete()
                    } catch (_: Exception) {}
                }
                downloadStore.removeTask(mediaId)
                DownloadedCatalogRepository.instance?.removeDownloadedItem(mediaId)
            }
        })
    }

    private fun syncDownloadState(download: Download) {
        val mediaId = download.request.id
        val stored = downloadStore.storedTasks.value.find { it.mediaId == mediaId }
        val title = stored?.title ?: String(download.request.data)
        val downloadId = stored?.downloadId ?: mediaId.hashCode().toLong()

        val status = when (download.state) {
            Download.STATE_DOWNLOADING, Download.STATE_RESTARTING -> DownloadStatus.RUNNING
            Download.STATE_STOPPED -> DownloadStatus.PAUSED
            Download.STATE_COMPLETED -> DownloadStatus.COMPLETED
            Download.STATE_FAILED -> DownloadStatus.FAILED
            Download.STATE_QUEUED -> DownloadStatus.RUNNING
            Download.STATE_REMOVING -> DownloadStatus.PAUSED
            else -> DownloadStatus.PAUSED
        }

        val media3Percent = download.percentDownloaded.takeIf { it in 0.0f..100.0f }?.toInt()
        val downloaded = download.bytesDownloaded.takeIf { it > 0 } ?: (stored?.downloadedBytes ?: 0L)
        val total = when {
            download.contentLength > 0 -> download.contentLength
            stored?.totalBytes != null && stored.totalBytes > 0 -> stored.totalBytes
            media3Percent != null && media3Percent > 0 && downloaded > 0 -> (downloaded * 100.0 / media3Percent).toLong()
            else -> 0L
        }
        val percent = when {
            media3Percent != null && media3Percent > 0 -> media3Percent
            total > 0 && downloaded > 0 -> ((downloaded * 100) / total).toInt().coerceIn(0, 100)
            else -> 0
        }

        val state = ActiveDownloadState(
            downloadId = downloadId,
            mediaId = mediaId,
            title = title,
            bytesDownloaded = downloaded,
            totalBytes = total,
            progressPercent = percent,
            speedBps = 0L,
            etaSeconds = 0L,
            status = status
        )

        activeStreamsMap[mediaId] = state
        updateActiveDownloadsState()

        if (status == DownloadStatus.COMPLETED) {
            val completedItem = DownloadedItem(
                id = mediaId,
                title = title,
                mediaType = stored?.mediaType ?: "MOVIE",
                mediaId = stored?.mediaId ?: mediaId,
                seasonNumber = stored?.seasonNumber,
                episodeNumber = stored?.episodeNumber,
                localFilePath = stored?.localFilePath ?: "",
                downloadId = downloadId,
                quality = stored?.quality ?: "1080p",
                languages = stored?.languages ?: emptyList(),
                posterUrl = stored?.posterUrl ?: "",
                catalogJson = stored?.catalogJson ?: "",
                directUrl = stored?.directUrl ?: download.request.uri.toString()
            )
            DownloadedCatalogRepository.instance?.addOrUpdateDownloadedItem(completedItem)
        }

        if (stored != null) {
            val statusStr = when (status) {
                DownloadStatus.RUNNING -> "RUNNING"
                DownloadStatus.PAUSED -> "PAUSED"
                DownloadStatus.COMPLETED -> "COMPLETED"
                DownloadStatus.FAILED -> "FAILED"
                DownloadStatus.PENDING -> "RUNNING"
            }
            if (stored.status != statusStr || (status == DownloadStatus.COMPLETED && stored.status != "COMPLETED")) {
                downloadStore.saveOrUpdateTask(
                    stored.copy(
                        downloadedBytes = downloaded,
                        totalBytes = total,
                        status = statusStr
                    )
                )
            }
        }
    }

    private fun startProgressPolling() {
        progressPollingJob?.cancel()
        progressPollingJob = scope.launch {
            var lastUpdate = System.currentTimeMillis()
            val previousBytesMap = mutableMapOf<String, Long>()
            val smoothedSpeedMap = mutableMapOf<String, Long>()

            while (isActive) {
                delay(500L)
                val now = System.currentTimeMillis()
                val timeDiff = (now - lastUpdate).coerceAtLeast(1L)

                val currentDownloads = media3Manager.currentDownloads
                if (currentDownloads.isNotEmpty()) {
                    currentDownloads.forEach { download ->
                        val mediaId = download.request.id
                        val stored = downloadStore.storedTasks.value.find { it.mediaId == mediaId }
                        val title = stored?.title ?: String(download.request.data)
                        val downloadId = stored?.downloadId ?: mediaId.hashCode().toLong()

                        val status = when (download.state) {
                            Download.STATE_DOWNLOADING, Download.STATE_RESTARTING -> DownloadStatus.RUNNING
                            Download.STATE_STOPPED -> DownloadStatus.PAUSED
                            Download.STATE_COMPLETED -> DownloadStatus.COMPLETED
                            Download.STATE_FAILED -> DownloadStatus.FAILED
                            Download.STATE_QUEUED -> DownloadStatus.RUNNING
                            Download.STATE_REMOVING -> DownloadStatus.PAUSED
                            else -> DownloadStatus.PAUSED
                        }

                        val media3Percent = download.percentDownloaded.takeIf { it in 0.0f..100.0f }?.toInt()
                        val downloaded = download.bytesDownloaded.takeIf { it > 0 } ?: (stored?.downloadedBytes ?: 0L)
                        val total = when {
                            download.contentLength > 0 -> download.contentLength
                            stored?.totalBytes != null && stored.totalBytes > 0 -> stored.totalBytes
                            media3Percent != null && media3Percent > 0 && downloaded > 0 -> (downloaded * 100.0 / media3Percent).toLong()
                            else -> 0L
                        }
                        val percent = when {
                            media3Percent != null && media3Percent > 0 -> media3Percent
                            total > 0 && downloaded > 0 -> ((downloaded * 100) / total).toInt().coerceIn(0, 100)
                            else -> 0
                        }

                        if (total > 0 && stored != null && stored.totalBytes == 0L) {
                            downloadStore.saveOrUpdateTask(stored.copy(totalBytes = total, downloadedBytes = downloaded))
                        }

                        // Storage safety check for ultra-low-end devices (<150MB free)
                        if (status == DownloadStatus.RUNNING) {
                            try {
                                val targetFile = stored?.localFilePath?.let { java.io.File(it) }
                                val parentDir = targetFile?.parentFile ?: MediaCacheManager.resolveDownloadBaseDir(context)
                                val statFs = StatFs(parentDir.path)
                                if (statFs.availableBytes < 150 * 1024 * 1024L) {
                                    Log.w(TAG, "Storage critically low (<150 MB). Pausing download for $mediaId")
                                    pauseDownload(mediaId)
                                }
                            } catch (_: Exception) {}
                        }

                        val prev = previousBytesMap[mediaId] ?: downloaded
                        val bytesSince = (downloaded - prev).coerceAtLeast(0L)
                        val rawInstantSpeed = if (status == DownloadStatus.RUNNING) (bytesSince * 1000L) / timeDiff else 0L

                        // EMA smoothing: discrete HLS segments commit in bursts; smooth out spikes & pauses
                        val prevSmoothed = smoothedSpeedMap[mediaId] ?: rawInstantSpeed
                        val speedBps = when {
                            status != DownloadStatus.RUNNING -> 0L
                            prevSmoothed <= 0L -> rawInstantSpeed
                            rawInstantSpeed > 0L -> (0.35 * rawInstantSpeed + 0.65 * prevSmoothed).toLong()
                            else -> (0.80 * prevSmoothed).toLong().coerceAtLeast(0L)
                        }
                        smoothedSpeedMap[mediaId] = speedBps

                        val remainingBytes = (total - downloaded).coerceAtLeast(0L)
                        val etaSeconds = if (speedBps > 0 && remainingBytes > 0) remainingBytes / speedBps else 0L

                        previousBytesMap[mediaId] = downloaded

                        activeStreamsMap[mediaId] = ActiveDownloadState(
                            downloadId = downloadId,
                            mediaId = mediaId,
                            title = title,
                            bytesDownloaded = downloaded,
                            totalBytes = total,
                            progressPercent = percent,
                            speedBps = speedBps,
                            etaSeconds = etaSeconds,
                            status = status
                        )
                    }
                    updateActiveDownloadsState()
                }
                lastUpdate = now
            }
        }
    }

    private fun restoreTasksFromStore() {
        val tasks = downloadStore.storedTasks.value
        tasks.forEach { task ->
            if (task.status == "RUNNING" || task.status == "PAUSED" || task.status == "FAILED") {
                val state = ActiveDownloadState(
                    downloadId = task.downloadId,
                    mediaId = task.mediaId,
                    title = task.title,
                    bytesDownloaded = task.downloadedBytes,
                    totalBytes = task.totalBytes,
                    progressPercent = if (task.totalBytes > 0) ((task.downloadedBytes * 100) / task.totalBytes).toInt().coerceIn(0, 100) else 0,
                    speedBps = 0L,
                    etaSeconds = 0L,
                    status = if (task.status == "FAILED") DownloadStatus.FAILED else DownloadStatus.PAUSED
                )
                activeStreamsMap[task.mediaId] = state
            }
        }
        updateActiveDownloadsState()
    }

    fun enqueueDownload(
        mediaId: String,
        title: String,
        directUrl: String,
        targetFilePath: String,
        quality: String = "1080p",
        languages: List<String> = emptyList(),
        mediaType: String = "MOVIE",
        seasonNumber: Int? = null,
        episodeNumber: Int? = null,
        catalogJson: String = "",
        posterUrl: String = "",
        estimatedTotalBytes: Long = 0L
    ): Long {
        val existing = activeStreamsMap[mediaId]
        if (existing != null && (existing.status == DownloadStatus.RUNNING || existing.status == DownloadStatus.PENDING)) {
            Log.w(TAG, "Download for $mediaId is already active ($existing). Ignoring duplicate enqueue.")
            return existing.downloadId
        }

        val downloadId = System.currentTimeMillis()

        var finalDirectUrl = directUrl
        val isHls = directUrl.contains(".m3u8", ignoreCase = true) ||
            directUrl.contains("/hls/", ignoreCase = true) ||
            directUrl.contains("master.txt", ignoreCase = true) ||
            directUrl.contains("master.", ignoreCase = true)

        if (isHls) {
            finalDirectUrl = HlsManifestFilter.filterHlsManifest(
                context = context,
                rawUrl = directUrl,
                selectedQuality = quality,
                selectedLanguages = languages,
                mediaId = mediaId
            )
        }

        val mimeType = when {
            finalDirectUrl.contains(".m3u8", ignoreCase = true) ||
            finalDirectUrl.contains("/hls/", ignoreCase = true) ||
            finalDirectUrl.contains("master.txt", ignoreCase = true) ||
            finalDirectUrl.contains("master.", ignoreCase = true) -> MimeTypes.APPLICATION_M3U8
            finalDirectUrl.contains(".mpd", ignoreCase = true) -> MimeTypes.APPLICATION_MPD
            else -> null
        }

        val request = DownloadRequest.Builder(mediaId, Uri.parse(finalDirectUrl))
            .setMimeType(mimeType)
            .setData(title.toByteArray())
            .build()

        val initialTask = StoredDownloadTask(
            downloadId = downloadId,
            mediaId = mediaId,
            title = title,
            directUrl = finalDirectUrl,
            localFilePath = targetFilePath,
            partFilePath = "$targetFilePath.part",
            downloadedBytes = 0L,
            totalBytes = estimatedTotalBytes,
            quality = quality,
            languages = languages,
            status = "RUNNING",
            mediaType = mediaType,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
            catalogJson = catalogJson,
            posterUrl = posterUrl
        )
        downloadStore.saveOrUpdateTask(initialTask)

        activeStreamsMap[mediaId] = ActiveDownloadState(
            downloadId = downloadId,
            mediaId = mediaId,
            title = title,
            bytesDownloaded = 0L,
            totalBytes = estimatedTotalBytes,
            progressPercent = 0,
            speedBps = 0L,
            etaSeconds = 0L,
            status = DownloadStatus.RUNNING
        )
        updateActiveDownloadsState()

        DownloadService.sendAddDownload(
            context,
            MetFlixDownloadService::class.java,
            request,
            /* foreground = */ true
        )

        return downloadId
    }

    fun pauseDownload(mediaId: String) {
        DownloadService.sendSetStopReason(
            context,
            MetFlixDownloadService::class.java,
            mediaId,
            Download.STOP_REASON_NONE + 1,
            /* foreground = */ false
        )
        activeStreamsMap[mediaId]?.let { current ->
            activeStreamsMap[mediaId] = current.copy(status = DownloadStatus.PAUSED, speedBps = 0L)
            updateActiveDownloadsState()
        }
        downloadStore.storedTasks.value.find { it.mediaId == mediaId }?.let { stored ->
            downloadStore.saveOrUpdateTask(stored.copy(status = "PAUSED"))
        }
    }

    fun resumeDownload(mediaId: String) {
        val stored = downloadStore.storedTasks.value.find { it.mediaId == mediaId }
        val current = activeStreamsMap[mediaId]
        val isFailed = current?.status == DownloadStatus.FAILED || stored?.status == "FAILED"

        if (isFailed && stored != null) {
            val mimeType = when {
                stored.directUrl.contains(".m3u8", ignoreCase = true) ||
                stored.directUrl.contains("/hls/", ignoreCase = true) ||
                stored.directUrl.contains("master.txt", ignoreCase = true) ||
                stored.directUrl.contains("master.", ignoreCase = true) -> MimeTypes.APPLICATION_M3U8
                stored.directUrl.contains(".mpd", ignoreCase = true) -> MimeTypes.APPLICATION_MPD
                else -> null
            }
            val request = DownloadRequest.Builder(mediaId, Uri.parse(stored.directUrl))
                .setMimeType(mimeType)
                .setData(stored.title.toByteArray())
                .build()

            DownloadService.sendAddDownload(
                context,
                MetFlixDownloadService::class.java,
                request,
                /* foreground = */ true
            )
        } else {
            DownloadService.sendSetStopReason(
                context,
                MetFlixDownloadService::class.java,
                mediaId,
                Download.STOP_REASON_NONE,
                /* foreground = */ true
            )
        }

        DownloadService.sendResumeDownloads(
            context,
            MetFlixDownloadService::class.java,
            /* foreground = */ true
        )

        activeStreamsMap[mediaId]?.let { cur ->
            activeStreamsMap[mediaId] = cur.copy(status = DownloadStatus.RUNNING)
            updateActiveDownloadsState()
        }
        stored?.let {
            downloadStore.saveOrUpdateTask(it.copy(status = "RUNNING"))
        }
    }

    private fun registerNetworkCallback() {
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (cm != null) {
                val req = NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()
                cm.registerNetworkCallback(req, object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        Log.d(TAG, "Network restored. Auto-resuming interrupted downloads...")
                        scope.launch {
                            delay(1200L) // Wait for network to stabilize
                            media3Manager.resumeDownloads()
                            for ((mediaId, state) in activeStreamsMap) {
                                if (state.status == DownloadStatus.FAILED) {
                                    resumeDownload(mediaId)
                                }
                            }
                        }
                    }
                })
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register network callback: ${e.message}")
        }
    }

    fun cancelDownload(mediaId: String) {
        DownloadService.sendRemoveDownload(
            context,
            MetFlixDownloadService::class.java,
            mediaId,
            /* foreground = */ false
        )
        activeStreamsMap.remove(mediaId)
        updateActiveDownloadsState()
        val stored = downloadStore.storedTasks.value.find { it.mediaId == mediaId }
        if (stored != null) {
            try {
                val file = java.io.File(stored.localFilePath)
                if (file.exists()) file.delete()
                val partFile = java.io.File(stored.partFilePath)
                if (partFile.exists()) partFile.delete()
            } catch (_: Exception) {}
        }
        downloadStore.removeTask(mediaId)
        DownloadedCatalogRepository.instance?.removeDownloadedItem(mediaId)
    }
}
