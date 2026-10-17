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

import android.content.Context
import android.util.Log
import com.example.metflix.data.repositories.LiveTvRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StreamResolverRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val liveTvRepository: LiveTvRepository
) {
    companion object {
        private const val TAG = "StreamResolverRepo"
        private const val BROWSER_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
        private const val LOCAL_DECRYPTOR_BASE = "http://10.227.10.225:8000"
        private const val TUNNEL_DECRYPTOR_BASE = "https://representative-grams-take-incoming.trycloudflare.com"
    }

    val availableServers: List<StreamServer> = listOf(
        StreamServer(
            id = "vidlink",
            name = "Server 1 (VidLink Pro)",
            description = "Multi-Language Subtitles & Audio • 1080p FHD",
            badge = "VIP",
            speed = "Ultra",
            isPrimary = true
        ),
        StreamServer(
            id = "superembed",
            name = "Server 2 (SuperEmbed)",
            description = "High Bandwidth Mirror • 1080p/720p",
            badge = "HD",
            speed = "Fast"
        ),
        StreamServer(
            id = "iptv",
            name = "Server 3 (IPTV VOD Match)",
            description = "Direct stream matched from loaded M3U playlist",
            badge = "IPTV",
            speed = "Direct"
        ),
        StreamServer(
            id = "autoembed",
            name = "Server 4 (AutoEmbed)",
            description = "Global Edge Mirror • 1080p",
            badge = "CDN",
            speed = "Fast"
        ),
        StreamServer(
            id = "twoembed",
            name = "Server 5 (2Embed Mirror)",
            description = "Stable Fallback Server • 720p/1080p",
            badge = "Backup",
            speed = "Normal"
        ),
        StreamServer(
            id = "vidsrc",
            name = "Server 6 (VidSrc Cloud)",
            description = "Alternative Cloud Mirror • 1080p",
            badge = "Cloud",
            speed = "Normal"
        )
    )

    /**
     * Resolves a video stream for a given movie using the requested or default server.
     */
    suspend fun resolveMovieStream(
        tmdbId: String,
        movieTitle: String,
        releaseYear: String = "",
        server: StreamServer? = null
    ): ResolvedStream = withContext(Dispatchers.IO) {
        val targetServer = server ?: availableServers.first()
        Log.d(TAG, "Resolving stream for '$movieTitle' (TMDB: $tmdbId) on ${targetServer.name}")

        when (targetServer.id) {
            "vidlink" -> resolveVidLink(tmdbId, targetServer)
            "superembed" -> resolveSuperEmbed(tmdbId, targetServer)
            "iptv" -> resolveIptvMatch(movieTitle, releaseYear, targetServer)
            "autoembed" -> resolveAutoEmbed(tmdbId, targetServer)
            "twoembed" -> resolve2Embed(tmdbId, targetServer)
            "vidsrc" -> resolveVidSrc(tmdbId, targetServer)
            else -> resolveVidLink(tmdbId, availableServers.first())
        }
    }

    /**
     * Simultaneously queries all available servers in parallel, measures round-trip latency,
     * verifies stream availability, prioritizes regional and direct streams,
     * and streams progressive rank updates.
     * The fastest responding valid stream is emitted immediately to minimize player startup time.
     */
    fun raceAndRankStreams(
        tmdbId: String,
        movieTitle: String,
        releaseYear: String = "",
        originalLanguage: String = ""
    ): Flow<ServerRaceResult> = channelFlow {
        val isRegional = originalLanguage.isNotBlank() && originalLanguage.lowercase() !in listOf("en", "eng")
        Log.d(TAG, "Starting parallel server race for '$movieTitle' (TMDB: $tmdbId, Lang: $originalLanguage, isRegional: $isRegional)")

        val servers = availableServers
        val resolvedResults = ConcurrentHashMap<String, Pair<StreamServer, ResolvedStream?>>()
        val serverScores = ConcurrentHashMap<String, Long>()
        val hasEmittedAnyStream = AtomicBoolean(false)

        fun buildRankedList(): List<StreamServer> {
            val completed = resolvedResults.values.map { it.first }
            val completedIds = completed.map { it.id }.toSet()
            val remaining = servers.filterNot { it.id in completedIds }

            val sortedCompleted = completed.sortedByDescending { serverScores[it.id] ?: -9999L }
            val combined = sortedCompleted + remaining
            return combined.mapIndexed { index, s ->
                s.copy(rank = index + 1)
            }
        }

        val jobs = servers.map { server ->
            launch(Dispatchers.IO) {
                val startTime = System.currentTimeMillis()
                var resolved: ResolvedStream? = null
                var isRealIptvMatch = false
                var isAvailable = false
                var latencyMs: Long? = null

                try {
                    if (server.id == "iptv") {
                        val (iptvStream, matched) = resolveIptvMatchInternal(movieTitle, releaseYear, server)
                        if (matched && iptvStream != null) {
                            resolved = iptvStream
                            isRealIptvMatch = true
                            isAvailable = true
                            latencyMs = System.currentTimeMillis() - startTime
                        } else {
                            isAvailable = false
                            latencyMs = System.currentTimeMillis() - startTime
                        }
                    } else {
                        val stream = resolveMovieStream(tmdbId, movieTitle, releaseYear, server)
                        val probeResult = probeServer(stream)
                        isAvailable = probeResult.first
                        latencyMs = probeResult.second ?: (System.currentTimeMillis() - startTime)
                        resolved = if (probeResult.third != null) {
                            stream.copy(
                                streamUrl = probeResult.third!!,
                                isHls = probeResult.third!!.contains(".m3u8"),
                                latencyMs = latencyMs
                            )
                        } else {
                            stream.copy(latencyMs = latencyMs)
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Server ${server.name} error during race: ${e.message}")
                    isAvailable = false
                    latencyMs = System.currentTimeMillis() - startTime
                }

                val score = computeScore(
                    server = server,
                    resolved = resolved,
                    isAvailable = isAvailable,
                    isRealIptvMatch = isRealIptvMatch,
                    latencyMs = latencyMs,
                    isRegional = isRegional
                )
                serverScores[server.id] = score

                val updatedServer = server.copy(
                    latencyMs = latencyMs,
                    isAvailable = isAvailable,
                    isRegionalMatch = isRegional && (isRealIptvMatch || server.id == "vidlink" || server.id == "superembed"),
                    isDirect = resolved?.isDirectStream == true,
                    badge = when {
                        isRegional && isRealIptvMatch -> "Regional"
                        resolved?.isDirectStream == true -> "Direct"
                        latencyMs != null && latencyMs < 350 -> "${latencyMs}ms"
                        !isAvailable -> "Offline"
                        else -> server.badge
                    },
                    speed = when {
                        latencyMs != null && latencyMs < 200 -> "Ultra"
                        latencyMs != null && latencyMs < 500 -> "Fast"
                        latencyMs != null && latencyMs < 1200 -> "Normal"
                        else -> server.speed
                    }
                )

                resolvedResults[server.id] = Pair(updatedServer, resolved)

                val currentRanked = buildRankedList()

                // If this is the FIRST valid server to complete, emit it right away to start video!
                if (isAvailable && resolved != null && !hasEmittedAnyStream.getAndSet(true)) {
                    Log.d(TAG, "Fastest server won race: ${updatedServer.name} (${latencyMs}ms)")
                    send(
                        ServerRaceResult(
                            rankedServers = currentRanked,
                            bestStream = resolved.copy(server = updatedServer),
                            isFinal = false
                        )
                    )
                } else {
                    val topValidServer = currentRanked.firstOrNull { s ->
                        resolvedResults[s.id]?.first?.isAvailable == true && resolvedResults[s.id]?.second != null
                    }
                    val topStream = topValidServer?.let { s ->
                        val pair = resolvedResults[s.id]
                        pair?.second?.copy(server = pair.first)
                    }

                    send(
                        ServerRaceResult(
                            rankedServers = currentRanked,
                            bestStream = topStream,
                            isFinal = false
                        )
                    )
                }
            }
        }

        jobs.joinAll()

        val finalRanked = buildRankedList()
        val finalTopServer = finalRanked.firstOrNull { s ->
            resolvedResults[s.id]?.first?.isAvailable == true && resolvedResults[s.id]?.second != null
        }
        val finalBestStream = finalTopServer?.let { s ->
            val pair = resolvedResults[s.id]
            pair?.second?.copy(server = pair.first)
        } ?: resolveVidLink(tmdbId, servers.first())

        Log.d(TAG, "Server race finished. Top ranked server: ${finalRanked.firstOrNull()?.name}")
        send(
            ServerRaceResult(
                rankedServers = finalRanked,
                bestStream = finalBestStream,
                isFinal = true
            )
        )
    }

    private suspend fun resolveVidLink(tmdbId: String, server: StreamServer): ResolvedStream {
        val embedUrl = "https://vidlink.pro/movie/$tmdbId"
        val headers = mapOf(
            "User-Agent" to BROWSER_USER_AGENT,
            "Referer" to "https://vidlink.pro/"
        )

        // Try decryptor endpoints: Cloudflare tunnel first, then local LAN
        val decryptorEndpoints = listOf(
            "$TUNNEL_DECRYPTOR_BASE/movie/$tmdbId",
            "$LOCAL_DECRYPTOR_BASE/movie/$tmdbId"
        )

        for (endpoint in decryptorEndpoints) {
            val streamResult = fetchFromDecryptor(endpoint, server)
            if (streamResult != null) {
                Log.d(TAG, "Successfully resolved direct stream via decryptor: ${streamResult.streamUrl}")
                return streamResult
            }
        }

        Log.w(TAG, "Decryptor could not find direct stream for $tmdbId; falling back to web embed")
        return ResolvedStream(
            server = server,
            streamUrl = embedUrl,
            isHls = false,
            headers = headers,
            backupUrls = listOf("https://vidlink.pro/movie/$tmdbId?autoplay=true")
        )
    }

    private fun fetchFromDecryptor(
        endpointUrl: String,
        server: StreamServer
    ): ResolvedStream? {
        return try {
            val request = Request.Builder()
                .url(endpointUrl)
                .header("User-Agent", BROWSER_USER_AGENT)
                .build()

            val fastClient = okHttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofMillis(3500))
                .readTimeout(java.time.Duration.ofMillis(4000))
                .build()

            fastClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Decryptor returned HTTP ${response.code} for $endpointUrl")
                    return null
                }
                val bodyString = response.body?.string() ?: return null
                val rootJson = JSONObject(bodyString)
                val streamObj = rootJson.optJSONObject("stream") ?: return null
                val qualitiesObj = streamObj.optJSONObject("qualities") ?: return null

                // Parse available qualities in priority order
                val qualityList = mutableListOf<QualityTrack>()
                val qualityOrder = listOf("1080", "720", "480", "360")
                for (q in qualityOrder) {
                    val qItem = qualitiesObj.optJSONObject(q) ?: continue
                    val url = qItem.optString("url")
                    if (!url.isNullOrBlank()) {
                        val height = q.toIntOrNull() ?: 0
                        qualityList.add(QualityTrack(quality = "${q}p", height = height, url = url))
                    }
                }

                if (qualityList.isEmpty()) {
                    return null
                }

                val bestQuality = qualityList.first()
                val backupUrls = qualityList.drop(1).map { it.url }

                // Parse captions
                val subtitles = mutableListOf<SubtitleTrack>()
                val captionsArray = streamObj.optJSONArray("captions")
                if (captionsArray != null) {
                    for (i in 0 until captionsArray.length()) {
                        val cap = captionsArray.optJSONObject(i) ?: continue
                        val capUrl = cap.optString("url")
                        val lang = cap.optString("language")
                        if (!capUrl.isNullOrBlank() && !lang.isNullOrBlank()) {
                            subtitles.add(
                                SubtitleTrack(
                                    label = lang,
                                    language = lang,
                                    uri = capUrl
                                )
                            )
                        }
                    }
                }

                ResolvedStream(
                    server = server,
                    streamUrl = bestQuality.url,
                    isHls = false,
                    headers = emptyMap(), // Direct CDN streams must NOT have Referer
                    subtitles = subtitles,
                    qualities = qualityList,
                    backupUrls = backupUrls
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resolve from decryptor at $endpointUrl: ${e.message}")
            null
        }
    }

    private suspend fun resolveSuperEmbed(tmdbId: String, server: StreamServer): ResolvedStream {
        val embedUrl = "https://multiembed.mov/?video_id=$tmdbId&tmdb=1"
        val headers = mapOf(
            "User-Agent" to BROWSER_USER_AGENT,
            "Referer" to "https://multiembed.mov/"
        )

        return ResolvedStream(
            server = server,
            streamUrl = embedUrl,
            isHls = false,
            headers = headers,
            backupUrls = listOf("https://multiembed.mov/directstream.php?video_id=$tmdbId&tmdb=1")
        )
    }

    private suspend fun resolveAutoEmbed(tmdbId: String, server: StreamServer): ResolvedStream {
        val embedUrl = "https://player.autoembed.cc/embed/movie/$tmdbId"
        val headers = mapOf(
            "User-Agent" to BROWSER_USER_AGENT,
            "Referer" to "https://player.autoembed.cc/"
        )

        return ResolvedStream(
            server = server,
            streamUrl = embedUrl,
            isHls = false,
            headers = headers,
            backupUrls = emptyList()
        )
    }

    private suspend fun resolve2Embed(tmdbId: String, server: StreamServer): ResolvedStream {
        val embedUrl = "https://www.2embed.cc/embed/$tmdbId"
        val headers = mapOf(
            "User-Agent" to BROWSER_USER_AGENT,
            "Referer" to "https://www.2embed.cc/"
        )

        return ResolvedStream(
            server = server,
            streamUrl = embedUrl,
            isHls = false,
            headers = headers,
            backupUrls = listOf("https://www.2embed.to/embed/imdb/movie?id=$tmdbId")
        )
    }

    private suspend fun resolveVidSrc(tmdbId: String, server: StreamServer): ResolvedStream {
        val embedUrl = "https://vidsrc.xyz/embed/movie/$tmdbId"
        val altEmbedUrl = "https://vidsrc.to/embed/movie/$tmdbId"
        val headers = mapOf(
            "User-Agent" to BROWSER_USER_AGENT,
            "Referer" to "https://vidsrc.xyz/"
        )

        return ResolvedStream(
            server = server,
            streamUrl = embedUrl,
            isHls = false,
            headers = headers,
            backupUrls = listOf(altEmbedUrl)
        )
    }

    private suspend fun resolveIptvMatchInternal(
        movieTitle: String,
        releaseYear: String,
        server: StreamServer
    ): Pair<ResolvedStream?, Boolean> {
        val normalizedTitle = movieTitle.trim().lowercase()
        val searchResults = runCatching { liveTvRepository.searchChannels(movieTitle) }.getOrElse { emptyList() }

        val bestMatch = searchResults.firstOrNull { ch ->
            val chName = ch.name.lowercase()
            chName.contains(normalizedTitle) && (releaseYear.isBlank() || chName.contains(releaseYear))
        } ?: searchResults.firstOrNull { ch ->
            val chName = ch.name.lowercase()
            chName.contains(normalizedTitle)
        }

        if (bestMatch != null && bestMatch.streamUrl.isNotBlank()) {
            Log.d(TAG, "Found IPTV VOD match for '$movieTitle': ${bestMatch.name} -> ${bestMatch.streamUrl}")
            val resolved = ResolvedStream(
                server = server,
                streamUrl = bestMatch.streamUrl,
                isHls = bestMatch.streamUrl.contains(".m3u8"),
                headers = emptyMap(),
                isRegional = true
            )
            return Pair(resolved, true)
        }

        return Pair(null, false)
    }

    private suspend fun resolveIptvMatch(
        movieTitle: String,
        releaseYear: String,
        server: StreamServer
    ): ResolvedStream {
        val (stream, matched) = resolveIptvMatchInternal(movieTitle, releaseYear, server)
        if (matched && stream != null) {
            return stream
        }

        // Fallback to VidLink if no matching IPTV channel was found
        Log.d(TAG, "No IPTV match found for '$movieTitle', falling back to primary server")
        return resolveVidLink(movieTitle, availableServers.first())
    }

    private fun probeServer(stream: ResolvedStream): Triple<Boolean, Long?, String?> {
        if (stream.isDirectStream) {
            return Triple(true, 45L, null)
        }
        val startTime = System.currentTimeMillis()
        return try {
            val fastClient = okHttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofMillis(2500))
                .readTimeout(java.time.Duration.ofMillis(2500))
                .followRedirects(true)
                .build()

            val reqBuilder = Request.Builder().url(stream.streamUrl)
            stream.headers.forEach { (k, v) -> reqBuilder.header(k, v) }

            val response = fastClient.newCall(reqBuilder.build()).execute()
            response.use { resp ->
                val elapsed = System.currentTimeMillis() - startTime
                val isOk = resp.isSuccessful || resp.code in 200..399
                if (!isOk) return Triple(false, elapsed, null)

                val directStream = if (!stream.isDirectStream) {
                    val htmlSnippet = resp.body?.string().orEmpty()
                    extractDirectStreamFromString(htmlSnippet)
                } else null

                Triple(true, elapsed, directStream)
            }
        } catch (e: Exception) {
            val elapsed = System.currentTimeMillis() - startTime
            // Embed URLs may have bot protection, but still render fine in WebView
            Triple(true, elapsed, null)
        }
    }

    private fun extractDirectStreamFromString(html: String): String? {
        val m3u8Matcher = Pattern.compile("""(https?://[^"'\s<>]+\.m3u8[^"'\s<>]*)""").matcher(html)
        if (m3u8Matcher.find()) {
            val stream = m3u8Matcher.group(1)
            if (!stream.isNullOrBlank()) return stream
        }
        val mp4Matcher = Pattern.compile("""(https?://[^"'\s<>]+\.mp4[^"'\s<>]*)""").matcher(html)
        if (mp4Matcher.find()) {
            val stream = mp4Matcher.group(1)
            if (!stream.isNullOrBlank()) return stream
        }
        return null
    }

    private fun computeScore(
        server: StreamServer,
        resolved: ResolvedStream?,
        isAvailable: Boolean,
        isRealIptvMatch: Boolean,
        latencyMs: Long?,
        isRegional: Boolean
    ): Long {
        if (!isAvailable) return -1000L

        var score = 1000L - (latencyMs?.coerceIn(0L, 1000L) ?: 500L)

        if (resolved?.isDirectStream == true) {
            score += 350L
        }

        if (isRegional) {
            if (server.id == "iptv" && isRealIptvMatch) {
                score += 850L
            } else if (server.id == "vidlink") {
                score += 450L
            } else if (server.id == "superembed") {
                score += 300L
            }
        } else {
            if (server.id == "vidlink") {
                score += 350L
            } else if (server.id == "superembed") {
                score += 250L
            } else if (server.id == "autoembed") {
                score += 200L
            }
        }

        return score
    }

    /**
     * Attempts to extract a direct HLS (.m3u8) or MP4 stream from an embed page HTML.
     */
    private fun extractDirectStreamFromHtml(url: String, headers: Map<String, String>): String? {
        return try {
            val fastClient = okHttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofMillis(2500))
                .readTimeout(java.time.Duration.ofMillis(3000))
                .build()
            val reqBuilder = Request.Builder().url(url)
            headers.forEach { (k, v) -> reqBuilder.header(k, v) }
            val response = fastClient.newCall(reqBuilder.build()).execute()
            response.use { resp ->
                if (!resp.isSuccessful) return null
                val html = resp.body?.string().orEmpty()

                // Check for .m3u8 playlist URLs
                val m3u8Matcher = Pattern.compile("""(https?://[^"'\s<>]+\.m3u8[^"'\s<>]*)""").matcher(html)
                if (m3u8Matcher.find()) {
                    val stream = m3u8Matcher.group(1)
                    if (!stream.isNullOrBlank()) {
                        Log.d(TAG, "Extracted direct m3u8: $stream")
                        return stream
                    }
                }

                // Check for mp4 sources
                val mp4Matcher = Pattern.compile("""(https?://[^"'\s<>]+\.mp4[^"'\s<>]*)""").matcher(html)
                if (mp4Matcher.find()) {
                    val stream = mp4Matcher.group(1)
                    if (!stream.isNullOrBlank()) {
                        Log.d(TAG, "Extracted direct mp4: $stream")
                        return stream
                    }
                }

                // Check for embedded iframe src
                val iframeMatcher = Pattern.compile("""<iframe[^>]+src=["'](https?://[^"']+)["']""").matcher(html)
                if (iframeMatcher.find()) {
                    val iframeSrc = iframeMatcher.group(1)
                    if (!iframeSrc.isNullOrBlank() && !iframeSrc.contains("recaptcha") && !iframeSrc.contains("ads")) {
                        Log.d(TAG, "Found nested iframe src: $iframeSrc, attempting recursive resolution")
                        return extractDirectStreamFromHtml(iframeSrc, headers)
                    }
                }

                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Stream extraction error for $url: ${e.message}")
            null
        }
    }
}
