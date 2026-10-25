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

import androidx.annotation.Keep
import java.util.Locale

enum class DownloadStatus {
    RUNNING,
    PAUSED,
    COMPLETED,
    FAILED,
    PENDING
}

@Keep
data class ActiveDownloadState(
    val downloadId: Long,
    val mediaId: String,
    val title: String,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = 0L,
    val progressPercent: Int = 0,
    val speedBps: Long = 0L,
    val etaSeconds: Long = 0L,
    val status: DownloadStatus = DownloadStatus.PENDING,
    val partCount: Int = 8
) {
    fun getPartProgress(partIndex: Int): Float {
        val segmentSize = 100f / partCount
        val start = partIndex * segmentSize
        return ((progressPercent - start) / segmentSize).coerceIn(0f, 1f)
    }

    val formattedSpeed: String
        get() {
            if (speedBps <= 0) return "0 KB/s"
            val kb = speedBps / 1024.0
            val mb = kb / 1024.0
            return if (mb >= 1.0) String.format(Locale.US, "%.1f MB/s", mb) else String.format(Locale.US, "%.0f KB/s", kb)
        }

    val formattedEta: String
        get() {
            if (etaSeconds <= 0 || status != DownloadStatus.RUNNING) return ""
            val mins = etaSeconds / 60
            val secs = etaSeconds % 60
            return if (mins > 0) "${mins}m ${secs}s remaining" else "${secs}s remaining"
        }

    val formattedProgress: String
        get() {
            val currentMb = bytesDownloaded / (1024.0 * 1024.0)
            val totalMb = totalBytes / (1024.0 * 1024.0)
            return if (totalBytes > 0) {
                String.format(Locale.US, "%.1f MB / %.1f MB (%d%%)", currentMb, totalMb, progressPercent)
            } else if (bytesDownloaded > 0) {
                String.format(Locale.US, "%.1f MB downloaded (%d%%)", currentMb, progressPercent)
            } else {
                "$progressPercent%"
            }
        }

    val formattedDownloadedSize: String
        get() {
            val currentMb = bytesDownloaded / (1024.0 * 1024.0)
            val currentGb = currentMb / 1024.0
            return if (currentGb >= 1.0) String.format(Locale.US, "%.2f GB", currentGb) else String.format(Locale.US, "%.1f MB", currentMb)
        }

    val formattedTotalSize: String
        get() {
            val totalMb = totalBytes / (1024.0 * 1024.0)
            val totalGb = totalMb / 1024.0
            return if (totalGb >= 1.0) String.format(Locale.US, "%.2f GB", totalGb) else String.format(Locale.US, "%.1f MB", totalMb)
        }
}
