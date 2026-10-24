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
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.metflix.R

class InAppDownloadService : Service() {

    companion object {
        private const val TAG = "InAppDownloadService"
        private const val CHANNEL_ID = "metflix_download_channel"
        private const val NOTIFICATION_ID = 9001
        private const val EXTRA_TITLE = "extra_download_title"

        fun startService(context: Context, title: String) {
            try {
                val intent = Intent(context, InAppDownloadService::class.java).apply {
                    putExtra(EXTRA_TITLE, title)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting InAppDownloadService: ${e.message}")
            }
        }

        fun checkAutoStop(context: Context) {
            val engine = InAppDownloadEngine.instance ?: return
            val runningCount = engine.activeDownloads.value.values.count { it.status == DownloadStatus.RUNNING }
            if (runningCount <= 0) {
                try {
                    val intent = Intent(context, InAppDownloadService::class.java)
                    context.stopService(intent)
                } catch (_: Exception) {}
            }
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onCreate() {
        super.onCreate()
        acquireLocks()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val title = intent?.getStringExtra(EXTRA_TITLE) ?: "Media"
        val notification = buildNotification(title)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_STICKY
    }

    private fun acquireLocks() {
        try {
            val pm = getSystemService(POWER_SERVICE) as? PowerManager
            if (wakeLock == null && pm != null) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MetFlix:InAppDownloadWakeLock").apply {
                    setReferenceCounted(false)
                    acquire(24 * 60 * 60 * 1000L) // 24 hours max
                }
            }

            val wm = applicationContext.getSystemService(WIFI_SERVICE) as? WifiManager
            if (wifiLock == null && wm != null) {
                @Suppress("DEPRECATION")
                wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "MetFlix:InAppDownloadWifiLock").apply {
                    setReferenceCounted(false)
                    acquire()
                }
            }
            Log.d(TAG, "Acquired WakeLock and WifiLock for background downloading")
        } catch (e: Exception) {
            Log.e(TAG, "Error acquiring locks: ${e.message}")
        }
    }

    private fun releaseLocks() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
            wakeLock = null

            if (wifiLock?.isHeld == true) {
                wifiLock?.release()
            }
            wifiLock = null
            Log.d(TAG, "Released WakeLock and WifiLock")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing locks: ${e.message}")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "MetFlix Media Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress and status of active MetFlix downloads"
            }
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(title: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MetFlix Downloading")
            .setContentText("Downloading: $title")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        releaseLocks()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
