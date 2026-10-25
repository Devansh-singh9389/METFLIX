

package com.example.metflix.data.downloads

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.scheduler.Requirements
import com.example.metflix.data.network.SecureHttpClient
import com.example.metflix.data.player.MediaCacheManager
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@OptIn(UnstableApi::class)
object Media3DownloadManagerProvider {

    private const val BROWSER_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

    @Volatile
    private var downloadManager: DownloadManager? = null

    @Volatile
    private var downloadOkHttpClient: OkHttpClient? = null

    private fun getDownloadOkHttpClient(context: Context): OkHttpClient {
        return downloadOkHttpClient ?: synchronized(this) {
            downloadOkHttpClient ?: run {
                val baseClient = SecureHttpClient.getClient(context)
                val dispatcher = Dispatcher().apply {
                    maxRequests = 64
                    maxRequestsPerHost = 16
                }
                val client = baseClient.newBuilder()
                    .dispatcher(dispatcher)
                    .connectionPool(ConnectionPool(32, 10, TimeUnit.MINUTES))
                    .cache(null)
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .retryOnConnectionFailure(true)
                    .addInterceptor { chain ->
                        val original = chain.request()
                        val host = original.url.host.lowercase()
                        val builder = original.newBuilder()
                            .header("User-Agent", BROWSER_USER_AGENT)
                            .header("Accept", "*/*")
                            .header("Accept-Language", "en-US,en;q=0.9")
                            .header("Sec-CH-UA-Platform", "\"Windows\"")
                            .header("Sec-CH-UA", "\"Chromium\";v=\"124\", \"Not:A-Brand\";v=\"8\", \"Google Chrome\";v=\"124\"")
                            .header("Sec-CH-UA-Mobile", "?0")

                        if (host.contains("zephyrix") || host.contains("zn-grid") || host.contains("watchanimeworld")) {
                            if (original.header("Referer") == null) {
                                builder.header("Referer", "https://play.zephyrix.org/video/")
                            }
                            if (original.header("Origin") == null) {
                                builder.header("Origin", "https://play.zephyrix.org")
                            }
                            builder.header("Sec-Fetch-Site", if (host.contains("zephyrix")) "same-origin" else "cross-site")
                            builder.header("Sec-Fetch-Mode", "cors")
                            builder.header("Sec-Fetch-Dest", "empty")
                        }
                        chain.proceed(builder.build())
                    }
                    .build()
                downloadOkHttpClient = client
                client
            }
        }
    }

    fun buildDataSourceFactory(context: Context): DataSource.Factory {
        val appContext = context.applicationContext
        val okHttpClient = getDownloadOkHttpClient(appContext)
        val okHttpDataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
            .setUserAgent(BROWSER_USER_AGENT)

        return DefaultDataSource.Factory(appContext, okHttpDataSourceFactory)
    }

    @Synchronized
    fun get(context: Context): DownloadManager {
        val appContext = context.applicationContext
        return downloadManager ?: synchronized(this) {
            downloadManager ?: run {
                val databaseProvider = StandaloneDatabaseProvider(appContext)
                val downloadCache = MediaCacheManager.getDownloadCache(appContext)
                val dataSourceFactory = buildDataSourceFactory(appContext)
                val executor = Executors.newFixedThreadPool(8)

                val manager = DownloadManager(
                    appContext,
                    databaseProvider,
                    downloadCache,
                    dataSourceFactory,
                    executor
                ).apply {
                    maxParallelDownloads = 2
                    minRetryCount = 10
                    requirements = Requirements(Requirements.NETWORK)
                }

                downloadManager = manager
                manager
            }
        }
    }

    @Synchronized
    fun resetDownloadManager() {
        try {
            downloadManager?.release()
        } catch (_: Exception) {}
        downloadManager = null
    }
}
