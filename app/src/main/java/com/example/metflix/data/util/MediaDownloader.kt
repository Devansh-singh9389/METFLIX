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
import android.util.Log
import android.widget.Toast
import com.example.metflix.data.settings.DownloadPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaDownloader @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val downloadPreferences: DownloadPreferences
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
        selectedLanguages: List<String> = emptyList()
    ): Long {
        if (directUrl.isBlank()) {
            Toast.makeText(context, "Cannot download: Invalid stream URL", Toast.LENGTH_SHORT).show()
            return -1L
        }

        return try {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            if (downloadManager == null) {
                Toast.makeText(context, "Download Manager unavailable", Toast.LENGTH_SHORT).show()
                return -1L
            }

            val extension = when {
                directUrl.contains(".mkv", ignoreCase = true) -> "mkv"
                directUrl.contains(".m3u8", ignoreCase = true) -> "m3u8"
                else -> "mp4"
            }

            val fileName = sanitizeFileName(catalogTitle, extension)
            val req = DownloadManager.Request(Uri.parse(directUrl)).apply {
                setTitle(catalogTitle)
                val langText = if (selectedLanguages.isNotEmpty()) " (${selectedLanguages.joinToString(", ")})" else ""
                setDescription("Downloading $selectedQuality $langText")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
                
                val dirType = downloadPreferences.downloadLocation.value
                val destinationDir = if (dirType.equalsIgnoreCase(Environment.DIRECTORY_MOVIES)) {
                    Environment.DIRECTORY_MOVIES
                } else {
                    Environment.DIRECTORY_DOWNLOADS
                }

                setDestinationInExternalPublicDir(destinationDir, "MetFlix/$fileName")
            }

            val downloadId = downloadManager.enqueue(req)
            Toast.makeText(context, "Download started: $catalogTitle", Toast.LENGTH_LONG).show()
            downloadId
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enqueue download for $catalogTitle: ${e.message}", e)
            Toast.makeText(context, "Failed to start download: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            -1L
        }
    }

    private fun String.equalsIgnoreCase(other: String): Boolean = this.equals(other, ignoreCase = true)
}
