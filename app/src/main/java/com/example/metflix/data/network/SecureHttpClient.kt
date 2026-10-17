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

import java.util.concurrent.TimeUnit
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient

/**
 * Shared singleton OkHttpClient instance using [SecureDns].
 *
 * Features:
 * - Transparent DNS-over-HTTPS bypass for Indian ISP blocking on TMDB.
 * - HTTP/2 socket multiplexing & TLS session resumption.
 * - Memory-conscious connection pool tuned for low-RAM Android TVs (5 idle connections max).
 */
object SecureHttpClient {

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(SecureDns)
            .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .writeTimeout(4, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}
