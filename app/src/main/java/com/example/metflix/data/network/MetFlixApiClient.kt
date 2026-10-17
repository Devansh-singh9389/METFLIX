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

import android.util.Log
import com.example.metflix.data.models.MovieCastResponseItem
import com.example.metflix.data.models.MovieCategoriesResponseItem
import com.example.metflix.data.models.MoviesResponseItem
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

@Singleton
class MetFlixApiClient @Inject constructor(
    private val okHttpClient: OkHttpClient
) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = true
    }

    suspend fun getMovies(endpoint: String = NetworkConfig.MOVIES_ENDPOINT): Result<List<MoviesResponseItem>> {
        return fetchJson(resolveUrl(endpoint)) { raw ->
            json.decodeFromString<List<MoviesResponseItem>>(raw)
        }
    }

    suspend fun getCategories(endpoint: String = NetworkConfig.CATEGORIES_ENDPOINT): Result<List<MovieCategoriesResponseItem>> {
        return fetchJson(resolveUrl(endpoint)) { raw ->
            json.decodeFromString<List<MovieCategoriesResponseItem>>(raw)
        }
    }

    suspend fun getCast(endpoint: String = NetworkConfig.CAST_ENDPOINT): Result<List<MovieCastResponseItem>> {
        return fetchJson(resolveUrl(endpoint)) { raw ->
            json.decodeFromString<List<MovieCastResponseItem>>(raw)
        }
    }

    suspend fun searchMovies(query: String): Result<List<MoviesResponseItem>> {
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        val url = resolveUrl("${NetworkConfig.SEARCH_ENDPOINT}?q=$encodedQuery")
        return fetchJson(url) { raw ->
            json.decodeFromString<List<MoviesResponseItem>>(raw)
        }
    }

    private fun resolveUrl(pathOrUrl: String): String {
        return if (pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://")) {
            pathOrUrl
        } else {
            val base = NetworkConfig.BASE_URL.trimEnd('/')
            val path = pathOrUrl.trimStart('/')
            "$base/$path"
        }
    }

    private suspend fun <T> fetchJson(urlString: String, parser: (String) -> T): Result<T> = withContext(Dispatchers.IO) {
        runCatching {
            val requestBuilder = Request.Builder().url(urlString)
            NetworkConfig.CUSTOM_HEADERS.forEach { (key, value) ->
                requestBuilder.header(key, value)
            }

            val response = okHttpClient.newCall(requestBuilder.build()).execute()
            response.use { resp ->
                if (resp.isSuccessful) {
                    val bodyString = resp.body?.string().orEmpty()
                    parser(bodyString)
                } else {
                    val errorText = resp.body?.string() ?: "HTTP ${resp.code}"
                    Log.w("MetFlixApiClient", "HTTP Error ${resp.code} for $urlString: $errorText")
                    throw Exception("Network request failed (${resp.code}): $errorText")
                }
            }
        }
    }
}
