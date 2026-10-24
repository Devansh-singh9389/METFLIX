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

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Scheduler
import com.example.metflix.R

@OptIn(UnstableApi::class)
class MetFlixDownloadService : DownloadService(
    FOREGROUND_NOTIFICATION_ID,
    1000L,
    CHANNEL_ID,
    R.string.app_name,
    0
) {
    companion object {
        const val CHANNEL_ID = "metflix_media3_download_channel"
        const val FOREGROUND_NOTIFICATION_ID = 2002
    }

    private val notificationHelper: DownloadNotificationHelper by lazy {
        createChannelIfNeeded()
        DownloadNotificationHelper(this, CHANNEL_ID)
    }

    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "MetFlix Media Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows real-time progress for downloaded movies, series, and anime."
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun getDownloadManager(): DownloadManager {
        return Media3DownloadManagerProvider.get(this)
    }

    override fun getScheduler(): Scheduler? {
        return null // Immediate foreground execution for Android TV
    }

    override fun getForegroundNotification(
        downloads: List<Download>,
        notMetRequirements: Int
    ): Notification {
        return notificationHelper.buildProgressNotification(
            this,
            R.drawable.app_logo,
            null,
            null,
            downloads,
            notMetRequirements
        )
    }
}
