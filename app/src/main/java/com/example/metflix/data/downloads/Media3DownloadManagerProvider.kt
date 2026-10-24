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
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.offline.DownloadManager
import com.example.metflix.data.player.MediaCacheManager
import java.util.concurrent.Executors

@OptIn(UnstableApi::class)
object Media3DownloadManagerProvider {

    private const val BROWSER_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

    @Volatile
    private var downloadManager: DownloadManager? = null

    @Synchronized
    fun get(context: Context): DownloadManager {
        val appContext = context.applicationContext
        return downloadManager ?: synchronized(this) {
            downloadManager ?: run {
                val databaseProvider = StandaloneDatabaseProvider(appContext)
                val downloadCache = MediaCacheManager.getDownloadCache(appContext)
                val httpDataSourceFactory = DefaultHttpDataSource.Factory()
                    .setUserAgent(BROWSER_USER_AGENT)
                    .setAllowCrossProtocolRedirects(true)
                    .setConnectTimeoutMs(15_000)
                    .setReadTimeoutMs(30_000)

                val executor = Executors.newFixedThreadPool(2)

                val manager = DownloadManager(
                    appContext,
                    databaseProvider,
                    downloadCache,
                    httpDataSourceFactory,
                    executor
                ).apply {
                    maxParallelDownloads = 2
                }

                downloadManager = manager
                manager
            }
        }
    }
}
