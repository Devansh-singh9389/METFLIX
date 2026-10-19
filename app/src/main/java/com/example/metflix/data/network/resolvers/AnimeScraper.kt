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
class AnimeScraper @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "AnimeScraper"
        private const val BROWSER_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
    }

    private val fastHttpClient: OkHttpClient by lazy {
        okHttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofMillis(3500))
            .readTimeout(java.time.Duration.ofMillis(4000))
            .followRedirects(true)
            .build()
    }

    /**
     * Resolves an anime stream, prioritizing Multi-Audio (Japanese, Hindi, English).
     * Inspired by Otaku & Prism provider architectures:
     * 1. Multi-Audio HLS manifests (e.g. Zephyrix / AnimeWorld / GogoEmbed)
     * 2. Direct Consumet / HiAnime multi-audio streams
     * 3. Fallback to direct episode stream
     */
    suspend fun resolveAnimeStream(
        tmdbId: String,
        animeTitle: String,
        season: Int = 1,
        episode: Int = 1,
        server: StreamServer
    ): ResolvedStream? = withContext(Dispatchers.IO) {
        val cleanTitle = cleanAnimeTitle(animeTitle)
        Log.d(TAG, "Resolving Anime stream for '$cleanTitle' (S${season}E${episode}, TMDB: $tmdbId)")

        // 1. Try Zephyrix / Multi-Source HLS provider
        tryZephyrixMultiAudio(cleanTitle, season, episode, server)?.let { return@withContext it }

        // 2. Try AnimeWorld / GogoStream provider
        tryAnimeWorldStream(cleanTitle, episode, server)?.let { return@withContext it }

        // 3. Try VidLink anime endpoint
        tryVidLinkAnime(tmdbId, season, episode, server)?.let { return@withContext it }

        null
    }

    private fun cleanAnimeTitle(title: String): String {
        return title
            .replace(Regex("""\(TV\)|\(Movie\)|\(Dub\)|\(Sub\)""", RegexOption.IGNORE_CASE), "")
            .replace(":", " ")
            .trim()
    }

    private suspend fun tryZephyrixMultiAudio(
        title: String,
        season: Int,
        episode: Int,
        server: StreamServer
    ): ResolvedStream? = withContext(Dispatchers.IO) {
        try {
            // Query Zephyrix player API or public multi-audio anime mirrors
            val encodedQuery = URLEncoder.encode("$title episode $episode", "UTF-8")
            val searchUrl = "https://play.zephyrix.org/api/search?q=$encodedQuery"

            val req = Request.Builder()
                .url(searchUrl)
                .header("User-Agent", BROWSER_USER_AGENT)
                .header("Referer", "https://watchanimeworld.one/")
                .build()

            fastHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body?.string().orEmpty()
                if (body.contains(".m3u8")) {
                    val m3u8Matcher = Pattern.compile("""https?://[^\s"'<>]+\.m3u8[^\s"'<>]*""").matcher(body)
                    if (m3u8Matcher.find()) {
                        val streamUrl = m3u8Matcher.group(0).orEmpty()
                        if (streamUrl.isNotBlank()) {
                            Log.d(TAG, "Found Zephyrix Multi-Audio HLS stream: $streamUrl")
                            return@withContext ResolvedStream(
                                server = server.copy(
                                    badge = "Multi-Audio",
                                    audioLanguages = listOf("Japanese", "Hindi", "English"),
                                    speed = "Ultra"
                                ),
                                streamUrl = streamUrl,
                                isHls = true,
                                headers = mapOf(
                                    "User-Agent" to BROWSER_USER_AGENT,
                                    "Referer" to "https://play.zephyrix.org/",
                                    "Origin" to "https://play.zephyrix.org"
                                ),
                                isRegional = true,
                                audioLanguages = listOf("Japanese", "Hindi", "English")
                            )
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Proceed to next provider
        }
        null
    }

    private suspend fun tryAnimeWorldStream(
        title: String,
        episode: Int,
        server: StreamServer
    ): ResolvedStream? = withContext(Dispatchers.IO) {
        try {
            val encoded = URLEncoder.encode("$title episode $episode", "UTF-8")
            val targetUrl = "https://watchanimeworld.one/?s=$encoded"

            val req = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", BROWSER_USER_AGENT)
                .build()

            fastHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val html = resp.body?.string().orEmpty()

                // Look for iframe player or direct embed
                val iframeMatcher = Pattern.compile("""<iframe[^>]+src=["'](https?://[^"']+)["']""").matcher(html)
                if (iframeMatcher.find()) {
                    val embedUrl = iframeMatcher.group(1)
                    if (!embedUrl.isNullOrBlank()) {
                        return@withContext ResolvedStream(
                            server = server.copy(
                                badge = "Multi-Audio",
                                audioLanguages = listOf("Japanese", "Hindi", "English")
                            ),
                            streamUrl = embedUrl,
                            isHls = embedUrl.contains(".m3u8"),
                            headers = mapOf(
                                "User-Agent" to BROWSER_USER_AGENT,
                                "Referer" to "https://watchanimeworld.one/"
                            ),
                            isRegional = true,
                            audioLanguages = listOf("Japanese", "Hindi", "English")
                        )
                    }
                }
            }
        } catch (_: Exception) {
            // Proceed
        }
        null
    }

    private suspend fun tryVidLinkAnime(
        tmdbId: String,
        season: Int,
        episode: Int,
        server: StreamServer
    ): ResolvedStream? = withContext(Dispatchers.IO) {
        try {
            val embedUrl = "https://vidlink.pro/tv/$tmdbId/$season/$episode?multiLang=1"
            return@withContext ResolvedStream(
                server = server.copy(
                    badge = "Multi-Lang",
                    audioLanguages = listOf("Japanese", "English")
                ),
                streamUrl = embedUrl,
                isHls = false,
                headers = mapOf(
                    "User-Agent" to BROWSER_USER_AGENT,
                    "Referer" to "https://vidlink.pro/"
                ),
                audioLanguages = listOf("Japanese", "English")
            )
        } catch (_: Exception) {
            null
        }
    }
}
