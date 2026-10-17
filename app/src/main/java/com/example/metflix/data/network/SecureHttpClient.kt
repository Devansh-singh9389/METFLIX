/*
 * Copyright 2025 Google LLC
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

package com.example.metflix.data.network

import android.content.Context
import java.io.File
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import okhttp3.Cache
import okhttp3.Call
import okhttp3.ConnectionPool
import okhttp3.EventListener
import okhttp3.OkHttpClient

/**
 * Shared singleton OkHttpClient instance using [SecureDns].
 *
 * Features:
 * - Transparent DNS-over-HTTPS bypass for Indian ISP blocking on TMDB.
 * - Dynamic IP affinity (promotes fast responsive CDN edges to index 0).
 * - Fast failover with 2s connect timeout.
 * - HTTP/2 socket multiplexing & TLS session resumption.
 * - Persistent HTTP caching for instant sub-millisecond offline/cold starts.
 */
object SecureHttpClient {

    @Volatile
    private var initializedClient: OkHttpClient? = null

    fun getClient(context: Context? = null): OkHttpClient {
        return initializedClient ?: synchronized(this) {
            initializedClient ?: buildClient(context).also { initializedClient = it }
        }
    }

    val client: OkHttpClient
        get() = getClient(null)

    private fun buildClient(context: Context?): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .dns(SecureDns)
            .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(3, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .eventListener(object : EventListener() {
                override fun connectEnd(
                    call: Call,
                    inetSocketAddress: InetSocketAddress,
                    proxy: Proxy,
                    protocol: okhttp3.Protocol?
                ) {
                    val host = call.request().url.host
                    if (host.contains("tmdb.org")) {
                        SecureDns.recordSuccess(host, inetSocketAddress.address)
                    }
                }
            })

        context?.cacheDir?.let { cacheDir ->
            runCatching {
                val httpCache = Cache(File(cacheDir, "tmdb_http_cache"), 25L * 1024 * 1024)
                builder.cache(httpCache)
            }
        }

        return builder.build()
    }
}
