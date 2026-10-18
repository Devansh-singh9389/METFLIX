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

package com.example.metflix.data.network.resolvers

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DualAudioScraper @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "DualAudioScraper"
        private const val BROWSER_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
        private const val STREAMFLIX_DATA_URL = "https://api.streamflix.app/data.json"
        private const val STREAMFLIX_DEFAULT_CDN = "https://cf.streamflixserver.site"
    }

    private val fastHttpClient: OkHttpClient by lazy {
        okHttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofMillis(3000))
            .readTimeout(java.time.Duration.ofMillis(3500))
            .followRedirects(true)
            .build()
    }

    /**
     * Resolves a genuine Dual-Audio (Hindi + English) stream for movies.
     * Tries multiple multi-audio catalogs in priority order:
     * 1. Multi-Source aggregator engine (Node daemon on port 8787 / tunnel)
     * 2. StreamFlix Dual-Audio & Multi-Language direct CDN files
     * 3. Vega / HubCloud direct media endpoints
     */
    suspend fun resolveDualAudioStream(
        tmdbId: String,
        movieTitle: String,
        releaseYear: String = "",
        server: StreamServer,
        isTvShow: Boolean = false,
        season: Int = 1,
        episode: Int = 1
    ): ResolvedStream? = withContext(Dispatchers.IO) {
        val mediaType = if (isTvShow) "TV S${season}E${episode}" else "Movie"
        Log.d(TAG, "Resolving Dual-Audio (Hindi + English) stream for '$movieTitle' ($mediaType, TMDB: $tmdbId)")

        // 1. Try Multi-Source Engine (Stremio / TMDB-Embed aggregator)
        tryMultiSourceEngine(tmdbId, server, isTvShow, season, episode)?.let { return@withContext it }

        // 2. Try StreamFlix Dual-Audio direct match
        tryStreamFlixDualAudio(tmdbId, movieTitle, server)?.let { return@withContext it }

        // 3. Try Vega / HubCloud Dual-Audio sources
        tryVegaHubCloudDualAudio(movieTitle, releaseYear, server)?.let { return@withContext it }

        null
    }

    private suspend fun tryMultiSourceEngine(
        tmdbId: String,
        server: StreamServer,
        isTvShow: Boolean = false,
        season: Int = 1,
        episode: Int = 1
    ): ResolvedStream? = withContext(Dispatchers.IO) {
        val path = if (isTvShow) "tv/$tmdbId/$season/$episode" else "movie/$tmdbId"
        val endpoints = listOf(
            "http://10.227.10.225:8787/api/streams/$path",
            "http://127.0.0.1:8787/api/streams/$path",
            "https://representative-grams-take-incoming.trycloudflare.com:8787/api/streams/$path"
        )

        for (endpoint in endpoints) {
            try {
                val req = Request.Builder()
                    .url(endpoint)
                    .header("User-Agent", BROWSER_USER_AGENT)
                    .build()

                fastHttpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string() ?: return@use
                        val json = JSONObject(body)
                        val streamsArr = json.optJSONArray("streams") ?: return@use

                        for (i in 0 until streamsArr.length()) {
                            val sObj = streamsArr.optJSONObject(i) ?: continue
                            val title = sObj.optString("title")
                            val isHindi = title.contains("hin", ignoreCase = true)
                            val isDual = title.contains("dual", ignoreCase = true) || title.contains("multi", ignoreCase = true)

                            if (isHindi || isDual) {
                                val url = sObj.optString("url")
                                if (url.isNotBlank()) {
                                    val headersMap = mutableMapOf<String, String>()
                                    val headersObj = sObj.optJSONObject("headers")
                                    if (headersObj != null) {
                                        val keys = headersObj.keys()
                                        while (keys.hasNext()) {
                                            val k = keys.next()
                                            headersMap[k] = headersObj.optString(k)
                                        }
                                    }
                                    Log.d(TAG, "Multi-Source Engine found Dual-Audio stream: $url")
                                    return@withContext ResolvedStream(
                                        server = server.copy(
                                            audioLanguages = listOf("Hindi", "English"),
                                            badge = "Hindi+Eng"
                                        ),
                                        streamUrl = url,
                                        isHls = url.contains(".m3u8"),
                                        headers = headersMap,
                                        isRegional = true,
                                        audioLanguages = listOf("Hindi", "English")
                                    )
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Continue to next endpoint
            }
        }
        null
    }

    private suspend fun tryStreamFlixDualAudio(
        tmdbId: String,
        movieTitle: String,
        server: StreamServer
    ): ResolvedStream? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url(STREAMFLIX_DATA_URL)
                .header("User-Agent", BROWSER_USER_AGENT)
                .build()

            val resp = fastHttpClient.newCall(req).execute()
            resp.use { r ->
                if (!r.isSuccessful) return@withContext null
                val body = r.body?.string() ?: return@withContext null
                val json = JSONObject(body)
                val items = json.optJSONArray("data") ?: return@withContext null

                var matchItem: JSONObject? = null
                val cleanTitle = movieTitle.trim().lowercase()

                // Check TMDB ID matches
                for (i in 0 until items.length()) {
                    val it = items.optJSONObject(i) ?: continue
                    if (it.optString("tmdb") == tmdbId) {
                        val movieLink = it.optString("movielink")
                        val movieName = it.optString("moviename")
                        val isHindi = movieLink.contains("hin", ignoreCase = true) || movieName.contains("hindi", ignoreCase = true)
                        val isDual = movieLink.contains("dual", ignoreCase = true) || movieLink.contains("multi", ignoreCase = true)
                        if (isHindi || isDual) {
                            matchItem = it
                            break
                        }
                    }
                }

                // If not found by TMDB ID, check by title
                if (matchItem == null) {
                    for (i in 0 until items.length()) {
                        val it = items.optJSONObject(i) ?: continue
                        val name = it.optString("moviename").lowercase()
                        if (name.contains(cleanTitle) || cleanTitle.contains(name)) {
                            val movieLink = it.optString("movielink")
                            val isHindi = movieLink.contains("hin", ignoreCase = true) || name.contains("hindi", ignoreCase = true)
                            val isDual = movieLink.contains("dual", ignoreCase = true) || movieLink.contains("multi", ignoreCase = true)
                            if (isHindi || isDual) {
                                matchItem = it
                                break
                            }
                        }
                    }
                }

                if (matchItem != null) {
                    val movieLink = matchItem.optString("movielink")
                    if (movieLink.isNotBlank()) {
                        val directUrl = if (movieLink.startsWith("http")) movieLink else "$STREAMFLIX_DEFAULT_CDN$movieLink"
                        Log.d(TAG, "StreamFlix matched Dual-Audio release: $directUrl")
                        return@withContext ResolvedStream(
                            server = server.copy(
                                audioLanguages = listOf("Hindi", "English"),
                                badge = "Hindi+Eng"
                            ),
                            streamUrl = directUrl,
                            isHls = directUrl.contains(".m3u8"),
                            headers = mapOf("User-Agent" to BROWSER_USER_AGENT),
                            isRegional = true,
                            audioLanguages = listOf("Hindi", "English")
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "StreamFlix Dual-Audio search failed: ${e.message}")
        }
        null
    }

    private suspend fun tryVegaHubCloudDualAudio(
        movieTitle: String,
        releaseYear: String,
        server: StreamServer
    ): ResolvedStream? = withContext(Dispatchers.IO) {
        val query = if (releaseYear.isNotBlank()) "$movieTitle $releaseYear" else movieTitle
        val searchUrls = listOf(
            "https://vegamovies.vodka/?s=${URLEncoder.encode(query, "UTF-8")}",
            "https://4khdhub.one/?s=${URLEncoder.encode(query, "UTF-8")}",
            "https://new8.hdhub4u.cl/?s=${URLEncoder.encode(query, "UTF-8")}"
        )

        for (searchUrl in searchUrls) {
            try {
                val req = Request.Builder()
                    .url(searchUrl)
                    .header("User-Agent", BROWSER_USER_AGENT)
                    .build()

                fastHttpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use
                    val html = resp.body?.string().orEmpty()

                    // Match article post URLs containing dual audio keywords
                    val postMatcher = Pattern.compile("""href=["'](https?://[^"']+)["'][^>]*>([^<]*(?:Hindi|Dual|Multi)[^<]*)</a>""", Pattern.CASE_INSENSITIVE).matcher(html)
                    if (postMatcher.find()) {
                        val postUrl = postMatcher.group(1)
                        if (!postUrl.isNullOrBlank()) {
                            val directStream = resolvePostHubCloudDirectStream(postUrl)
                            if (directStream != null) {
                                Log.d(TAG, "Found Vega/HubCloud Dual-Audio stream: $directStream")
                                return@withContext ResolvedStream(
                                    server = server.copy(
                                        audioLanguages = listOf("Hindi", "English"),
                                        badge = "Hindi+Eng"
                                    ),
                                    streamUrl = directStream,
                                    isHls = directStream.contains(".m3u8"),
                                    headers = mapOf("User-Agent" to BROWSER_USER_AGENT),
                                    isRegional = true,
                                    audioLanguages = listOf("Hindi", "English")
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Try next source
            }
        }
        null
    }

    private fun resolvePostHubCloudDirectStream(postUrl: String): String? {
        return try {
            val req = Request.Builder()
                .url(postUrl)
                .header("User-Agent", BROWSER_USER_AGENT)
                .build()

            fastHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val html = resp.body?.string().orEmpty()

                // Look for HubCloud or direct stream URLs in post
                val linkRegex = Pattern.compile("""var\s+url\s*=\s*['"]([^'"]+)['"]""")
                val linkMatcher = linkRegex.matcher(html)
                if (linkMatcher.find()) {
                    val hubUrl = linkMatcher.group(1)
                    if (!hubUrl.isNullOrBlank() && hubUrl.contains("hub")) {
                        return hubUrl
                    }
                }

                val downloadMatcher = Pattern.compile("""href=["'](https?://[^"']*(?:hubcloud|vcloud|fastdl|filepress)[^"']*)["']""", Pattern.CASE_INSENSITIVE).matcher(html)
                if (downloadMatcher.find()) {
                    val dlUrl = downloadMatcher.group(1)
                    if (!dlUrl.isNullOrBlank()) return dlUrl
                }

                // Look for direct .mkv or .mp4 files
                val directMatcher = Pattern.compile("""href=["'](https?://[^"']+\.(?:mkv|mp4)[^"']*)["']""", Pattern.CASE_INSENSITIVE).matcher(html)
                if (directMatcher.find()) {
                    return directMatcher.group(1)
                }

                null
            }
        } catch (e: Exception) {
            null
        }
    }
}
