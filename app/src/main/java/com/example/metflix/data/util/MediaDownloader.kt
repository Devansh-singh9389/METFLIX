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

package com.example.metflix.data.util

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.util.Log
import android.widget.Toast
import com.example.metflix.data.downloads.DownloadedCatalogRepository
import com.example.metflix.data.downloads.DownloadedItem
import com.example.metflix.data.downloads.InAppDownloadEngine
import com.example.metflix.data.downloads.InAppDownloadStore
import com.example.metflix.data.settings.DownloadPreferences
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@EntryPoint
@InstallIn(SingletonComponent::class)
interface MediaDownloaderEntryPoint {
    fun mediaDownloader(): MediaDownloader
}

object MediaDownloaderProvider {
    fun get(context: Context): MediaDownloader {
        val appContext = context.applicationContext
        return EntryPointAccessors.fromApplication(
            appContext,
            MediaDownloaderEntryPoint::class.java
        ).mediaDownloader()
    }
}

@Singleton
class MediaDownloader @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val downloadPreferences: DownloadPreferences,
    private val inAppDownloadEngine: InAppDownloadEngine
) {
    companion object {
        private const val TAG = "MediaDownloader"

        /**
         * Cleans a catalog title into a safe filename.
         */
        fun sanitizeFileName(catalogTitle: String, extension: String = "mp4"): String {
            val cleanTitle = catalogTitle
                .replace(Regex("[\\\\/:*?\"<>|]"), "_")
                .trim()
            val ext = if (extension.startsWith(".")) extension else ".$extension"
            return if (cleanTitle.endsWith(ext, ignoreCase = true)) cleanTitle else "$cleanTitle$ext"
        }
    }

    /**
     * Enqueues a direct link download using system DownloadManager.
     */
    fun enqueueDownload(
        catalogTitle: String,
        directUrl: String,
        selectedQuality: String = "1080p",
        selectedLanguages: List<String> = emptyList(),
        mediaId: String = catalogTitle,
        mediaType: String = "MOVIE",
        seasonNumber: Int? = null,
        episodeNumber: Int? = null,
        catalogJson: String = ""
    ): Long {
        if (directUrl.isBlank()) {
            Toast.makeText(context, "Cannot download: Invalid stream URL", Toast.LENGTH_SHORT).show()
            return -1L
        }

        return try {
            val dirType = downloadPreferences.downloadLocation.value
            val targetDir = if (dirType.startsWith("/")) File(dirType) else context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
            try {
                val statFs = StatFs(targetDir.path)
                val availableBytes = statFs.availableBytes
                val reservedBytes = 500 * 1024 * 1024L
                if (availableBytes < reservedBytes) {
                    Toast.makeText(context, "Insufficient Storage Space on device (<500 MB free)", Toast.LENGTH_LONG).show()
                    return -1L
                }
            } catch (_: Exception) {}

            val extension = when {
                directUrl.contains(".mkv", ignoreCase = true) -> "mkv"
                directUrl.contains(".m3u8", ignoreCase = true) -> "m3u8"
                else -> "mp4"
            }

            val fileName = sanitizeFileName(catalogTitle, extension)
            val destinationDir = if (dirType.equalsIgnoreCase(Environment.DIRECTORY_MOVIES)) {
                Environment.DIRECTORY_MOVIES
            } else {
                Environment.DIRECTORY_DOWNLOADS
            }

            val fullPath = "${Environment.getExternalStoragePublicDirectory(destinationDir)}/MetFlix/$fileName"

            val uniqueId = if (seasonNumber != null && episodeNumber != null) {
                "${mediaId}_S${seasonNumber}_E${episodeNumber}"
            } else {
                "${mediaId}_${selectedQuality.replace(" ", "")}"
            }

            val downloadId = inAppDownloadEngine.enqueueDownload(
                mediaId = uniqueId,
                title = catalogTitle,
                directUrl = directUrl,
                targetFilePath = fullPath,
                quality = selectedQuality,
                languages = selectedLanguages
            )

            val downloadedItem = DownloadedItem(
                id = uniqueId,
                title = catalogTitle,
                mediaType = mediaType,
                mediaId = mediaId,
                seasonNumber = seasonNumber,
                episodeNumber = episodeNumber,
                localFilePath = fullPath,
                downloadId = downloadId,
                quality = selectedQuality,
                languages = selectedLanguages,
                catalogJson = catalogJson.ifBlank { "{\"title\":\"$catalogTitle\",\"mediaId\":\"$mediaId\",\"mediaType\":\"$mediaType\"}" }
            )

            DownloadedCatalogRepository.instance?.addOrUpdateDownloadedItem(downloadedItem)

            Toast.makeText(context, "In-App Download Started: $catalogTitle", Toast.LENGTH_LONG).show()
            downloadId
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start in-app download for $catalogTitle: ${e.message}", e)
            Toast.makeText(context, "Failed to start download: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            -1L
        }
    }

    private fun String.equalsIgnoreCase(other: String): Boolean = this.equals(other, ignoreCase = true)
}
