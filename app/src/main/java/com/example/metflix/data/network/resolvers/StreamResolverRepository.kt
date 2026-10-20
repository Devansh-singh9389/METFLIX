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
import android.util.Base64
import android.util.Log
import com.example.metflix.data.repositories.LiveTvRepository
import java.nio.ByteBuffer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import com.example.metflix.data.settings.DebridPreferences
import com.example.metflix.data.settings.DebridProviderType
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
    @param:ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val liveTvRepository: LiveTvRepository,
    private val dualAudioScraper: DualAudioScraper,
    private val animeScraper: AnimeScraper,
    private val streamRankingEngine: StreamRankingEngine,
    private val debridPreferences: DebridPreferences
) {
    companion object {
        private const val TAG = "StreamResolverRepo"
        private const val BROWSER_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
        private const val LOCAL_DECRYPTOR_BASE = "http://10.227.10.225:8000"
        private const val TUNNEL_DECRYPTOR_BASE = "https://representative-grams-take-incoming.trycloudflare.com"
    }

    val availableServers: List<StreamServer>
        get() {
            val debridProvider = debridPreferences.selectedProvider.value
            val isTorBox = debridProvider == DebridProviderType.TORBOX
            val debridName = if (isTorBox) "Premium Stream · TorBox" else "Premium Stream · AllDebrid"
            val debridBadge = if (isTorBox) "TorBox" else "AllDebrid"

            return listOf(
                StreamServer(
                    id = "alldebrid",
                    name = debridName,
                    description = "High-Speed Cached Cloud Stream • 4K/1080p Direct Play",
                    badge = debridBadge,
                    speed = "Ultra",
                    isPrimary = true,
                    audioLanguages = listOf("Hindi", "English", "Original")
                ),
                StreamServer(
                    id = "dualaudio",
                    name = "Hindi + English Dual-Audio",
                    description = "Hollywood & Regional Blockbusters • Hindi + English Audio",
                    badge = "Hindi+Eng",
                    speed = "Ultra",
                    isPrimary = false,
                    audioLanguages = listOf("Hindi", "English")
                ),
                StreamServer(
                    id = "anime",
                    name = "AnimeWorld (Dub & Sub)",
                    description = "Japanese + Hindi + English Multi-Audio • HLS Direct",
                    badge = "Multi-Audio",
                    speed = "Ultra",
                    isPrimary = false,
                    audioLanguages = listOf("Japanese", "Hindi", "English")
                ),
                StreamServer(
                    id = "vidlink",
                    name = "Standard Source (1080p Fast)",
                    description = "Direct On-Device Decryption • 1080p FHD",
                    badge = "1080p",
                    speed = "Fast",
                    audioLanguages = listOf("Original")
                ),
                StreamServer(
                    id = "streamflix",
                    name = "Direct CDN Stream",
                    description = "Direct High-Speed CDN • 5,000+ Direct MKV/MP4",
                    badge = "Direct",
                    speed = "Ultra",
                    audioLanguages = listOf("English", "Original")
                ),
                StreamServer(
                    id = "stremio",
                    name = "Multi-Source Engine",
                    description = "TMDB-Embed Multi-Source • Regional Mirrors",
                    badge = "Multi-Source",
                    speed = "Fast",
                    audioLanguages = listOf("Hindi", "English")
                ),
                StreamServer(
                    id = "iptv",
                    name = "Live TV & Regional VOD",
                    description = "Direct stream matched from loaded M3U playlist",
                    badge = "IPTV",
                    speed = "Direct"
                ),
                StreamServer(
                    id = "superembed",
                    name = "High-Definition Mirror",
                    description = "High Bandwidth Mirror • 1080p/720p HLS",
                    badge = "HD",
                    speed = "Fast"
                ),
                StreamServer(
                    id = "autoembed",
                    name = "Global Edge CDN",
                    description = "Global Edge Mirror • 1080p",
                    badge = "CDN",
                    speed = "Fast"
                ),
                StreamServer(
                    id = "vidsrc",
                    name = "Cloud Backup Mirror",
                    description = "Alternative Cloud Mirror • 1080p",
                    badge = "Cloud",
                    speed = "Normal"
                )
            )
        }

    /**
     * Resolves a video stream for a given movie using the requested or default server.
     */
    suspend fun resolveMovieStream(
        tmdbId: String,
        movieTitle: String,
        releaseYear: String = "",
        server: StreamServer? = null,
        isTvShow: Boolean = false,
        season: Int = 1,
        episode: Int = 1,
        imdbId: String? = null
    ): ResolvedStream = withContext(Dispatchers.IO) {
        val targetServer = server ?: availableServers.first()
        if (targetServer.id == "alldebrid" && !debridPreferences.hasVerifiedPremiumDebrid()) {
            throw IllegalStateException("Debrid is disabled or not verified")
        }
        val mediaType = if (isTvShow) "TV S${season}E${episode}" else "Movie"
        Log.d(TAG, "Resolving stream for '$movieTitle' ($mediaType, TMDB: $tmdbId, IMDb: $imdbId) on ${targetServer.name}")

        when (targetServer.id) {
            "alldebrid" -> {
                try {
                    resolveAllDebrid(tmdbId, movieTitle, releaseYear, targetServer, isTvShow, season, episode, imdbId)
                } catch (e: Exception) {
                    Log.d(TAG, "Debrid direct resolve returned no cached stream, falling back to DualAudio: ${e.message}")
                    resolveDualAudio(tmdbId, movieTitle, releaseYear, targetServer.copy(badge = "Fallback"), isTvShow, season, episode, imdbId)
                }
            }
            "anime" -> resolveAnime(tmdbId, movieTitle, season, episode, targetServer)
            "dualaudio" -> resolveDualAudio(tmdbId, movieTitle, releaseYear, targetServer, isTvShow, season, episode, imdbId)
            "vidlink" -> resolveVidLink(tmdbId, targetServer, isTvShow, season, episode)
            "streamflix" -> resolveStreamFlix(tmdbId, movieTitle, targetServer)
            "stremio" -> resolveStremioMultiSource(tmdbId, movieTitle, releaseYear, targetServer, isTvShow, season, episode, imdbId)
            "superembed" -> resolveSuperEmbed(tmdbId, targetServer, isTvShow, season, episode, imdbId)
            "iptv" -> resolveIptvMatch(movieTitle, releaseYear, targetServer)
            "autoembed" -> resolveAutoEmbed(tmdbId, targetServer, isTvShow, season, episode)
            "twoembed" -> resolve2Embed(tmdbId, targetServer, isTvShow, season, episode, imdbId)
            "vidsrc" -> resolveVidSrc(tmdbId, targetServer, isTvShow, season, episode)
            else -> resolveDualAudio(tmdbId, movieTitle, releaseYear, targetServer, isTvShow, season, episode, imdbId)
        }
    }

    /**
     * Hierarchical stream resolution:
     * Tier 1: If verified Debrid is linked, queries Debrid cache ONLY with a strict 3.5s timeout.
     *         If cached, playback starts in < 1.5s with ZERO free scraper overhead.
     * Tier 2: Free scrapers run if Debrid is unconfigured, on Live TV, or on cache miss (auto-fallback).
     */
    fun raceAndRankStreams(
        tmdbId: String,
        movieTitle: String,
        releaseYear: String = "",
        originalLanguage: String = "",
        isTvShow: Boolean = false,
        season: Int = 1,
        episode: Int = 1,
        imdbId: String? = null,
        forceNonDebrid: Boolean = false,
        preferHindi: Boolean = true,
        preferredQuality: String = "720p"
    ): Flow<ServerRaceResult> = channelFlow {
        val isRegional = originalLanguage.isNotBlank() && originalLanguage.lowercase() !in listOf("en", "eng")
        val isLiveChannel = tmdbId.startsWith("iptv") || tmdbId.startsWith("channel")

        Log.d(TAG, "Starting resolver pipeline for '$movieTitle' (TMDB: $tmdbId, IMDb: $imdbId, isLive: $isLiveChannel, forceNonDebrid: $forceNonDebrid, preferHindi: $preferHindi, quality: $preferredQuality)")

        val allServers = availableServers
        val debridServer = allServers.first { it.id == "alldebrid" }

        // =========================================================================
        // TIER 1: DEBRID FAST-PATH (Direct Cloud Cache Check with 3.5s Strict Timeout)
        // =========================================================================
        val hasVerifiedDebrid = debridPreferences.hasVerifiedPremiumDebrid()
        if (hasVerifiedDebrid && !forceNonDebrid && !isLiveChannel) {
            val providerName = debridPreferences.selectedProvider.value.displayName
            Log.d(TAG, "Tier 1: Verified Debrid active ($providerName). Checking cache (preferHindi=$preferHindi, quality=$preferredQuality)...")
            val startTime = System.currentTimeMillis()

            val debridResult: ResolvedStream? = withTimeoutOrNull(3500L) {
                try {
                    resolveAllDebrid(
                        tmdbId = tmdbId,
                        movieTitle = movieTitle,
                        releaseYear = releaseYear,
                        server = debridServer,
                        isTvShow = isTvShow,
                        season = season,
                        episode = episode,
                        imdbId = imdbId,
                        preferHindi = preferHindi,
                        preferredQuality = preferredQuality
                    )
                } catch (e: Exception) {
                    Log.d(TAG, "Tier 1 Debrid returned no cached stream: ${e.message}")
                    null
                }
            }

            if (debridResult != null && (debridResult.isDirectStream || debridResult.streamUrl.startsWith("http"))) {
                val elapsed = System.currentTimeMillis() - startTime
                Log.d(TAG, "Tier 1 FAST-PATH HIT: ${debridServer.name} resolved in ${elapsed}ms! Skipping all free scrapers.")

                val updatedDebridServer = debridServer.copy(
                    latencyMs = elapsed,
                    isAvailable = true,
                    isDirect = true,
                    badge = if (debridResult.audioLanguages.any { it.equals("Hindi", true) }) "Hindi+Eng" else debridServer.badge,
                    audioLanguages = debridResult.audioLanguages.takeIf { it.isNotEmpty() } ?: debridServer.audioLanguages,
                    speed = "Ultra",
                    rank = 1
                )

                val rankedList = listOf(updatedDebridServer) + allServers.filterNot { it.id == "alldebrid" }.mapIndexed { idx, s -> s.copy(rank = idx + 2) }

                send(
                    ServerRaceResult(
                        rankedServers = rankedList,
                        bestStream = debridResult.copy(server = updatedDebridServer),
                        isFinal = true
                    )
                )
                // Fast-path success: return immediately, zero scraping overhead!
                return@channelFlow
            }

            Log.d(TAG, "Tier 1 Debrid fast-path missed or timed out (>3.5s). Auto-fallback: ${debridPreferences.autoFallbackToFree.value}")

            if (!debridPreferences.autoFallbackToFree.value) {
                // User disabled auto-fallback: notify UI that Debrid did not have cached stream
                val unavailDebrid = debridServer.copy(
                    isAvailable = false,
                    badge = "Uncached"
                )
                send(
                    ServerRaceResult(
                        rankedServers = listOf(unavailDebrid) + allServers.filterNot { it.id == "alldebrid" },
                        bestStream = null,
                        isFinal = true
                    )
                )
                return@channelFlow
            }
        }

        // =========================================================================
        // TIER 2: STANDARD / FREE SCRAPERS (DualAudio, Anime, VidLink, IPTV, Embeds)
        // Rate-limited with Semaphore(3) to protect Android TV chipsets & network pool
        // =========================================================================
        val candidateServers = allServers.filterNot { s ->
            // If Debrid was already verified and missed, skip querying it again in Tier 2
            s.id == "alldebrid" && hasVerifiedDebrid
        }

        val resolvedResults = ConcurrentHashMap<String, Pair<StreamServer, ResolvedStream?>>()
        val serverScores = ConcurrentHashMap<String, Long>()
        val hasEmittedAnyStream = AtomicBoolean(false)

        fun buildRankedList(): List<StreamServer> {
            val completed = resolvedResults.values.map { it.first }
            val completedIds = completed.map { it.id }.toSet()
            val remaining = candidateServers.filterNot { it.id in completedIds }

            val sortedCompleted = completed.sortedByDescending { serverScores[it.id] ?: -9999L }
            val combined = sortedCompleted + remaining
            return combined.mapIndexed { index, s ->
                s.copy(rank = index + 1)
            }
        }

        val scrapeSemaphore = Semaphore(3)
        val jobs = candidateServers.map { server ->
            launch(Dispatchers.IO) {
                scrapeSemaphore.withPermit {
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
                            val stream = resolveMovieStream(tmdbId, movieTitle, releaseYear, server, isTvShow, season, episode, imdbId)
                            val probeResult = probeServer(stream)
                            isAvailable = probeResult.isAvailable
                            latencyMs = probeResult.latencyMs ?: (System.currentTimeMillis() - startTime)
                            val isUnverifiedEmbed = probeResult.isUnverifiedEmbed
                            resolved = if (probeResult.directStreamUrl != null) {
                                stream.copy(
                                    streamUrl = probeResult.directStreamUrl,
                                    isHls = probeResult.directStreamUrl.contains(".m3u8"),
                                    latencyMs = latencyMs
                                )
                            } else {
                                stream.copy(latencyMs = latencyMs)
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Server ${server.name} error: ${e.message}")
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

                    val isEmbedServer = server.id == "vidlink" || server.id == "vidsrc" || server.id == "superembed" || resolved?.streamUrl?.contains("/embed/") == true
                    val updatedServer = server.copy(
                        latencyMs = latencyMs,
                        isAvailable = isAvailable,
                        isRegionalMatch = isRegional && (isRealIptvMatch || server.id == "dualaudio" || server.id == "vidlink" || server.id == "stremio" || server.id == "superembed"),
                        isDirect = resolved?.isDirectStream == true,
                        audioLanguages = resolved?.audioLanguages?.takeIf { it.isNotEmpty() } ?: server.audioLanguages,
                        badge = when {
                            !isAvailable && isEmbedServer -> "Dead Embed"
                            !isAvailable -> "Offline"
                            isEmbedServer && resolved?.isDirectStream != true -> "Unverified"
                            resolved?.audioLanguages?.any { it.equals("Hindi", ignoreCase = true) } == true -> "Hindi+Eng"
                            resolved?.audioLanguages?.size ?: 0 > 1 -> "Multi-Audio"
                            isRegional && isRealIptvMatch -> "Regional"
                            resolved?.isDirectStream == true -> "Direct"
                            latencyMs < 350 -> "${latencyMs}ms"
                            else -> server.badge
                        },
                        speed = when {
                            !isAvailable && isEmbedServer -> "Unverified Embed"
                            !isAvailable -> "Unavailable"
                            isEmbedServer && resolved?.isDirectStream != true -> "Unverified Embed"
                            latencyMs < 200 -> "Ultra"
                            latencyMs < 500 -> "Fast"
                            latencyMs < 1200 -> "Normal"
                            else -> server.speed
                        }
                    )

                    resolvedResults[server.id] = Pair(updatedServer, resolved)
                    val currentRanked = buildRankedList()

                    // If this is the FIRST valid server to complete, emit it right away to start video!
                    if (isAvailable && resolved != null && !hasEmittedAnyStream.getAndSet(true)) {
                        Log.d(TAG, "Tier 2 fast winner: ${updatedServer.name} (${latencyMs}ms) - playback starts")
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
        }

        jobs.joinAll()

        val finalRanked = buildRankedList()
        val finalTopServer = finalRanked.firstOrNull { s ->
            resolvedResults[s.id]?.first?.isAvailable == true && resolvedResults[s.id]?.second != null
        }
        val finalBestStream = finalTopServer?.let { s ->
            val pair = resolvedResults[s.id]
            pair?.second?.copy(server = pair.first)
        } ?: resolveVidLink(tmdbId, candidateServers.first(), isTvShow, season, episode)

        Log.d(TAG, "Tier 2 resolver finished. Top server: ${finalRanked.firstOrNull()?.name}")
        send(
            ServerRaceResult(
                rankedServers = finalRanked,
                bestStream = finalBestStream,
                isFinal = true
            )
        )
    }

    private suspend fun resolveAllDebrid(
        tmdbId: String,
        movieTitle: String,
        releaseYear: String,
        server: StreamServer,
        isTvShow: Boolean = false,
        season: Int = 1,
        episode: Int = 1,
        imdbId: String? = null,
        preferHindi: Boolean = true,
        preferredQuality: String = "720p"
    ): ResolvedStream = withContext(Dispatchers.IO) {
        val mediaId = if (isTvShow) {
            com.example.metflix.data.entities.MediaId.TvShow(tmdbId, season, episode, imdbId)
        } else {
            com.example.metflix.data.entities.MediaId.Movie(tmdbId, imdbId)
        }

        val candidates = streamRankingEngine.collectAndRankCandidates(
            mediaId = mediaId,
            title = movieTitle,
            releaseYear = releaseYear,
            preferHindi = preferHindi,
            preferredQuality = preferredQuality
        )

        val debridCandidate = candidates.firstOrNull { it.providerId == "alldebrid" || it.providerId == "torbox" }
            ?: candidates.firstOrNull { it.isDirectStream && (it.providerName.contains("Debrid", true) || it.providerName.contains("TorBox", true)) }

        if (debridCandidate != null) {
            Log.d(TAG, "Debrid direct stream matched: ${debridCandidate.streamUrl} (verified: ${debridCandidate.isAudioVerified})")
            return@withContext debridCandidate.toResolvedStream(server)
        }

        throw IllegalStateException("Debrid cache miss: No cached torrent streams found for '$movieTitle'")
    }

    private suspend fun resolveDualAudio(
        tmdbId: String,
        movieTitle: String,
        releaseYear: String,
        server: StreamServer,
        isTvShow: Boolean = false,
        season: Int = 1,
        episode: Int = 1,
        imdbId: String? = null
    ): ResolvedStream = withContext(Dispatchers.IO) {
        val mediaId = if (isTvShow) {
            com.example.metflix.data.entities.MediaId.TvShow(tmdbId, season, episode, imdbId)
        } else {
            com.example.metflix.data.entities.MediaId.Movie(tmdbId, imdbId)
        }

        val candidates = streamRankingEngine.collectAndRankCandidates(
            mediaId = mediaId,
            title = movieTitle,
            releaseYear = releaseYear,
            preferHindi = true
        )

        val bestCandidate = candidates.firstOrNull()
        if (bestCandidate != null) {
            Log.d(TAG, "StreamRankingEngine selected best candidate: ${bestCandidate.streamUrl} (verified: ${bestCandidate.isAudioVerified})")
            return@withContext bestCandidate.toResolvedStream(server)
        }

        Log.d(TAG, "DualAudioScraper found no direct dual-audio match, falling back to VidLink")
        val fallback = resolveVidLink(tmdbId, server.copy(audioLanguages = listOf("English", "Original")), isTvShow, season, episode)
        fallback.copy(
            server = server.copy(
                audioLanguages = listOf("English", "Original"),
                badge = "English Only"
            ),
            audioLanguages = listOf("English")
        )
    }

    private suspend fun resolveAnime(
        tmdbId: String,
        animeTitle: String,
        season: Int = 1,
        episode: Int = 1,
        server: StreamServer
    ): ResolvedStream = withContext(Dispatchers.IO) {
        val mediaId = com.example.metflix.data.entities.MediaId.Anime(
            title = animeTitle,
            episodeNumber = episode,
            seasonNumber = season
        )

        val candidates = streamRankingEngine.collectAndRankCandidates(
            mediaId = mediaId,
            title = animeTitle,
            preferHindi = true
        )

        val bestCandidate = candidates.firstOrNull()
        if (bestCandidate != null) {
            Log.d(TAG, "StreamRankingEngine selected best anime candidate: ${bestCandidate.streamUrl}")
            return@withContext bestCandidate.toResolvedStream(server)
        }

        Log.d(TAG, "AnimeScraper found no direct stream, falling back to VidLink")
        resolveVidLink(tmdbId, server, isTvShow = true, season = season, episode = episode)
    }

    private suspend fun resolveVidLink(
        tmdbId: String,
        server: StreamServer,
        isTvShow: Boolean = false,
        season: Int = 1,
        episode: Int = 1
    ): ResolvedStream {
        val embedUrl = if (isTvShow) {
            "https://vidlink.pro/tv/$tmdbId/$season/$episode"
        } else {
            "https://vidlink.pro/movie/$tmdbId"
        }
        val headers = mapOf(
            "User-Agent" to BROWSER_USER_AGENT,
            "Referer" to "https://vidlink.pro/"
        )

        // 1. Native standalone decryption (No laptop, backend server, or tunnel needed!)
        val nativeStream = fetchNativeVidlinkStream(tmdbId, server, isTvShow, season, episode)
        if (nativeStream != null) {
            Log.d(TAG, "Successfully resolved direct stream natively on-device: ${nativeStream.streamUrl}")
            return nativeStream
        }

        // 2. Fallback to external decryptor endpoints (only if configured or in DEBUG)
        val path = if (isTvShow) "tv/$tmdbId/$season/$episode" else "movie/$tmdbId"
        val decryptorEndpoints = mutableListOf<String>()
        if (com.example.metflix.BuildConfig.DEBUG) {
            decryptorEndpoints.add("$LOCAL_DECRYPTOR_BASE/$path")
            decryptorEndpoints.add("$TUNNEL_DECRYPTOR_BASE/$path")
        }
        if (com.example.metflix.data.network.NetworkConfig.USE_REMOTE_BACKEND) {
            val base = com.example.metflix.data.network.NetworkConfig.BASE_URL.trimEnd('/')
            if (base.isNotBlank() && !base.contains("example.com")) {
                decryptorEndpoints.add("$base/$path")
            }
        }

        for (endpoint in decryptorEndpoints) {
            val streamResult = fetchFromDecryptor(endpoint, server)
            if (streamResult != null) {
                Log.d(TAG, "Successfully resolved direct stream via decryptor: ${streamResult.streamUrl}")
                return streamResult
            }
        }

        Log.w(TAG, "Could not find direct stream for $tmdbId; falling back to web embed")
        return ResolvedStream(
            server = server,
            streamUrl = embedUrl,
            isHls = false,
            headers = headers,
            backupUrls = listOf(if (isTvShow) "https://vidlink.pro/tv/$tmdbId/$season/$episode?autoplay=true" else "https://vidlink.pro/movie/$tmdbId?autoplay=true")
        )
    }

    /**
     * Natively encrypts the media ID into a VidLink authorization token using XSalsa20-Poly1305.
     * Runs 100% locally on the Android TV with zero external backend or laptop required.
     */
    private fun generateVidlinkToken(mediaId: String): String {
        return try {
            val keyHex = "c75136c5668bbfe65a7ecad431a745db68b5f381555b38d8f6c699449cf11fcd"
            val key = ByteArray(32) { i ->
                keyHex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
            val nonce = ByteArray(24) // 24 zero bytes
            val timestamp = (System.currentTimeMillis() / 1000L) + 480L
            val mediaBytes = mediaId.toByteArray(Charsets.UTF_8)
            val buffer = ByteBuffer.allocate(mediaBytes.size + 8)
            buffer.put(mediaBytes)
            buffer.putLong(timestamp) // Big-endian 64-bit integer
            val message = buffer.array()

            val box = TweetNaclFast.SecretBox(key)
            val ciphertext = box.box(message, nonce) ?: return ""

            val fullPayload = ByteArray(nonce.size + ciphertext.size)
            System.arraycopy(nonce, 0, fullPayload, 0, nonce.size)
            System.arraycopy(ciphertext, 0, fullPayload, nonce.size, ciphertext.size)

            Base64.encodeToString(
                fullPayload,
                Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate native VidLink token: ${e.message}")
            ""
        }
    }

    private fun fetchNativeVidlinkStream(
        tmdbId: String,
        server: StreamServer,
        isTvShow: Boolean = false,
        season: Int = 1,
        episode: Int = 1
    ): ResolvedStream? {
        val mediaId = if (isTvShow) "$tmdbId/$season/$episode" else tmdbId
        val token = generateVidlinkToken(mediaId)
        if (token.isBlank()) return null

        val apiUrl = if (isTvShow) {
            "https://vidlink.pro/api/b/tv/$token?multiLang=1"
        } else {
            "https://vidlink.pro/api/b/movie/$token?multiLang=1"
        }
        return try {
            val request = Request.Builder()
                .url(apiUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36")
                .header("Origin", "https://vidlink.pro")
                .header("Referer", "https://vidlink.pro/")
                .header("x-playback-environment", "browser")
                .build()

            val fastClient = okHttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofMillis(3500))
                .readTimeout(java.time.Duration.ofMillis(4000))
                .build()

            fastClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Native Vidlink API returned HTTP ${response.code} for $apiUrl")
                    return null
                }
                val bodyString = response.body?.string() ?: return null
                parseVidlinkStreamJson(bodyString, server)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch native Vidlink stream: ${e.message}")
            null
        }
    }

    private fun parseVidlinkStreamJson(
        bodyString: String,
        server: StreamServer
    ): ResolvedStream? {
        return try {
            val rootJson = JSONObject(bodyString)
            val streamObj = rootJson.optJSONObject("stream") ?: return null
            val qualitiesObj = streamObj.optJSONObject("qualities")

            // Check for direct master HLS playlist
            val playlistUrl = streamObj.optString("playlist").takeIf { it.isNotBlank() }
                ?: streamObj.optString("hls").takeIf { it.isNotBlank() }

            // Parse available qualities in priority order
            val qualityList = mutableListOf<QualityTrack>()
            val qualityOrder = listOf("1080", "720", "480", "360")
            if (qualitiesObj != null) {
                for (q in qualityOrder) {
                    val qItem = qualitiesObj.optJSONObject(q) ?: continue
                    val url = qItem.optString("url")
                    if (!url.isNullOrBlank()) {
                        val height = q.toIntOrNull() ?: 0
                        qualityList.add(QualityTrack(quality = "${q}p", height = height, url = url))
                    }
                }
            }

            val finalStreamUrl = playlistUrl ?: qualityList.firstOrNull()?.url ?: return null
            val isHls = finalStreamUrl.contains(".m3u8") || playlistUrl != null

            val backupUrls = if (playlistUrl != null) {
                qualityList.map { it.url }
            } else {
                qualityList.drop(1).map { it.url }
            }

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

            // Parse multi-audio languages if returned in stream payload
            val audioLangs = mutableListOf<String>()
            val audioArr = streamObj.optJSONArray("audio") ?: streamObj.optJSONArray("audioTracks")
            if (audioArr != null) {
                for (i in 0 until audioArr.length()) {
                    val aItem = audioArr.optJSONObject(i)
                    val lang = aItem?.optString("language") ?: audioArr.optString(i)
                    if (!lang.isNullOrBlank()) {
                        audioLangs.add(lang)
                    }
                }
            }

            ResolvedStream(
                server = if (audioLangs.isNotEmpty()) server.copy(audioLanguages = audioLangs) else server,
                streamUrl = finalStreamUrl,
                isHls = isHls,
                headers = emptyMap(), // Direct CDN streams must NOT have Referer
                subtitles = subtitles,
                qualities = qualityList,
                backupUrls = backupUrls,
                audioLanguages = audioLangs
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse VidLink stream JSON: ${e.message}")
            null
        }
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
                parseVidlinkStreamJson(bodyString, server)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resolve from decryptor at $endpointUrl: ${e.message}")
            null
        }
    }

    private suspend fun resolveSuperEmbed(
        tmdbId: String,
        server: StreamServer,
        isTvShow: Boolean = false,
        season: Int = 1,
        episode: Int = 1,
        imdbId: String? = null
    ): ResolvedStream {
        val idParam = if (!imdbId.isNullOrBlank()) "video_id=$imdbId" else "video_id=$tmdbId&tmdb=1"
        val embedUrl = if (isTvShow) {
            "https://multiembed.mov/?$idParam&s=$season&e=$episode"
        } else {
            "https://multiembed.mov/?$idParam"
        }
        val headers = mapOf(
            "User-Agent" to BROWSER_USER_AGENT,
            "Referer" to "https://multiembed.mov/"
        )

        val backup = if (isTvShow) {
            "https://multiembed.mov/directstream.php?$idParam&s=$season&e=$episode"
        } else {
            "https://multiembed.mov/directstream.php?$idParam"
        }

        return ResolvedStream(
            server = server,
            streamUrl = embedUrl,
            isHls = false,
            headers = headers,
            backupUrls = listOf(backup)
        )
    }

    private suspend fun resolveAutoEmbed(
        tmdbId: String,
        server: StreamServer,
        isTvShow: Boolean = false,
        season: Int = 1,
        episode: Int = 1
    ): ResolvedStream {
        val embedUrl = if (isTvShow) {
            "https://player.autoembed.cc/embed/tv/$tmdbId/$season/$episode"
        } else {
            "https://player.autoembed.cc/embed/movie/$tmdbId"
        }
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

    private suspend fun resolve2Embed(
        tmdbId: String,
        server: StreamServer,
        isTvShow: Boolean = false,
        season: Int = 1,
        episode: Int = 1,
        imdbId: String? = null
    ): ResolvedStream {
        val embedUrl = if (isTvShow) {
            "https://www.2embed.cc/embedtv/$tmdbId&s=$season&e=$episode"
        } else {
            "https://www.2embed.cc/embed/$tmdbId"
        }
        val headers = mapOf(
            "User-Agent" to BROWSER_USER_AGENT,
            "Referer" to "https://www.2embed.cc/"
        )

        val targetImdb = imdbId?.takeIf { it.isNotBlank() } ?: tmdbId
        val backup = if (isTvShow) {
            "https://www.2embed.to/embed/imdb/tv?id=$targetImdb&s=$season&e=$episode"
        } else {
            "https://www.2embed.to/embed/imdb/movie?id=$targetImdb"
        }

        return ResolvedStream(
            server = server,
            streamUrl = embedUrl,
            isHls = false,
            headers = headers,
            backupUrls = listOf(backup)
        )
    }

    private suspend fun resolveVidSrc(
        tmdbId: String,
        server: StreamServer,
        isTvShow: Boolean = false,
        season: Int = 1,
        episode: Int = 1
    ): ResolvedStream {
        val embedUrl = if (isTvShow) {
            "https://vidsrc.xyz/embed/tv/$tmdbId/$season-$episode"
        } else {
            "https://vidsrc.xyz/embed/movie/$tmdbId"
        }
        val altEmbedUrl = if (isTvShow) {
            "https://vidsrc.to/embed/tv/$tmdbId/$season/$episode"
        } else {
            "https://vidsrc.to/embed/movie/$tmdbId"
        }
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

    private data class StreamProbeResult(
        val isAvailable: Boolean,
        val latencyMs: Long?,
        val directStreamUrl: String?,
        val isUnverifiedEmbed: Boolean
    )

    private fun probeServer(stream: ResolvedStream): StreamProbeResult {
        if (stream.isDirectStream) {
            return StreamProbeResult(
                isAvailable = true,
                latencyMs = 45L,
                directStreamUrl = null,
                isUnverifiedEmbed = false
            )
        }
        val startTime = System.currentTimeMillis()
        val isEmbed = !stream.isDirectStream && stream.streamUrl.contains("/embed/")
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
                if (!isOk) {
                    return StreamProbeResult(
                        isAvailable = false,
                        latencyMs = elapsed,
                        directStreamUrl = null,
                        isUnverifiedEmbed = isEmbed
                    )
                }

                val directStream = if (!stream.isDirectStream) {
                    val htmlSnippet = resp.body?.string().orEmpty()
                    extractDirectStreamFromString(htmlSnippet)
                } else null

                StreamProbeResult(
                    isAvailable = true,
                    latencyMs = elapsed,
                    directStreamUrl = directStream,
                    isUnverifiedEmbed = false
                )
            }
        } catch (e: Exception) {
            val elapsed = System.currentTimeMillis() - startTime
            // Failed probe: dead embed streams are marked unavailable (isAvailable = false) and tagged as unverified embed
            StreamProbeResult(
                isAvailable = false,
                latencyMs = elapsed,
                directStreamUrl = null,
                isUnverifiedEmbed = isEmbed
            )
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

        // Multi-Audio bonus: Massive boost for streams with Hindi Dual-Audio
        if (resolved?.audioLanguages?.any { it.equals("Hindi", ignoreCase = true) } == true) {
            score += 600L
        } else if (resolved?.audioLanguages?.isNotEmpty() == true) {
            score += 250L
        }

        // Priority weighting for the Dual-Audio Engine
        if (server.id == "dualaudio") {
            score += 400L
        }

        if (isRegional) {
            if (server.id == "iptv" && isRealIptvMatch) {
                score += 850L
            } else if (server.id == "dualaudio") {
                score += 650L
            } else if (server.id == "stremio") {
                score += 550L
            } else if (server.id == "streamflix") {
                score += 480L
            } else if (server.id == "vidlink") {
                score += 450L
            } else if (server.id == "superembed") {
                score += 300L
            }
        } else {
            if (server.id == "dualaudio") {
                score += 400L
            } else if (server.id == "vidlink") {
                score += 350L
            } else if (server.id == "streamflix") {
                score += 340L
            } else if (server.id == "stremio") {
                score += 320L
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

    @Volatile
    private var streamflixCache: Pair<Long, org.json.JSONArray>? = null
    @Volatile
    private var streamflixBaseCache: Pair<Long, String>? = null

    private suspend fun fetchStreamflixData(): org.json.JSONArray? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val cached = streamflixCache
        if (cached != null && (now - cached.first) < 30 * 60 * 1000L) {
            return@withContext cached.second
        }

        try {
            val req = Request.Builder()
                .url("https://api.streamflix.app/data.json")
                .header("User-Agent", BROWSER_USER_AGENT)
                .build()
            val resp = okHttpClient.newCall(req).execute()
            resp.use { r ->
                if (!r.isSuccessful) return@withContext null
                val body = r.body?.string() ?: return@withContext null
                val json = JSONObject(body)
                val items = json.optJSONArray("data")
                if (items != null) {
                    streamflixCache = Pair(now, items)
                }
                items
            }
        } catch (e: Exception) {
            Log.w(TAG, "StreamFlix data fetch failed: ${e.message}")
            null
        }
    }

    private suspend fun fetchStreamflixBase(): String = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val cached = streamflixBaseCache
        if (cached != null && (now - cached.first) < 30 * 60 * 1000L) {
            return@withContext cached.second
        }

        val defaultBase = "https://cf.streamflixserver.site"
        try {
            val req = Request.Builder()
                .url("https://api.streamflix.app/config/config-streamflixapp.json")
                .header("User-Agent", BROWSER_USER_AGENT)
                .build()
            val resp = okHttpClient.newCall(req).execute()
            resp.use { r ->
                if (!r.isSuccessful) return@withContext defaultBase
                val body = r.body?.string() ?: return@withContext defaultBase
                val json = JSONObject(body)
                val downloadArr = json.optJSONArray("download")
                val base = downloadArr?.optString(0) ?: defaultBase
                streamflixBaseCache = Pair(now, base)
                base
            }
        } catch (e: Exception) {
            defaultBase
        }
    }

    private suspend fun resolveStreamFlix(
        tmdbId: String,
        movieTitle: String,
        server: StreamServer
    ): ResolvedStream = withContext(Dispatchers.IO) {
        try {
            val items = fetchStreamflixData()
            val base = fetchStreamflixBase()

            var matchItem: JSONObject? = null
            if (items != null) {
                for (i in 0 until items.length()) {
                    val it = items.optJSONObject(i) ?: continue
                    if (it.optString("tmdb") == tmdbId) {
                        matchItem = it
                        break
                    }
                }
                if (matchItem == null) {
                    val cleanTitle = movieTitle.trim().lowercase()
                    for (i in 0 until items.length()) {
                        val it = items.optJSONObject(i) ?: continue
                        val name = it.optString("moviename").lowercase()
                        if (name.contains(cleanTitle) || cleanTitle.contains(name)) {
                            matchItem = it
                            break
                        }
                    }
                }
            }

            if (matchItem != null) {
                val movieLink = matchItem.optString("movielink")
                val movieName = matchItem.optString("moviename")
                if (movieLink.isNotBlank()) {
                    val directUrl = if (movieLink.startsWith("http")) movieLink else "$base$movieLink"
                    val isHindi = movieLink.contains("hin", ignoreCase = true) || movieName.contains("hindi", ignoreCase = true)
                    val isDual = movieLink.contains("dual", ignoreCase = true) || movieLink.contains("multi", ignoreCase = true)
                    val audioLangs = when {
                        isHindi && isDual -> listOf("Hindi", "English")
                        isHindi -> listOf("Hindi")
                        isDual -> listOf("Dual-Audio")
                        else -> listOf("English", "Original")
                    }

                    Log.d(TAG, "StreamFlix resolved direct stream for '$movieTitle': $directUrl")
                    return@withContext ResolvedStream(
                        server = server.copy(audioLanguages = audioLangs),
                        streamUrl = directUrl,
                        isHls = directUrl.contains(".m3u8"),
                        headers = mapOf("User-Agent" to BROWSER_USER_AGENT),
                        audioLanguages = audioLangs
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error resolving StreamFlix: ${e.message}")
        }

        resolveVidLink(tmdbId, server)
    }

    private suspend fun resolveStremioMultiSource(
        tmdbId: String,
        movieTitle: String,
        releaseYear: String,
        server: StreamServer,
        isTvShow: Boolean = false,
        season: Int = 1,
        episode: Int = 1,
        imdbId: String? = null
    ): ResolvedStream = withContext(Dispatchers.IO) {
        val path = if (isTvShow) "tv/$tmdbId/$season/$episode" else "movie/$tmdbId"
        val endpoints = mutableListOf(
            "http://10.227.10.225:8787/api/streams/$path",
            "http://127.0.0.1:8787/api/streams/$path",
            "https://representative-grams-take-incoming.trycloudflare.com:8787/api/streams/$path"
        )
        val mediaId = imdbId?.takeIf { it.isNotBlank() } ?: tmdbId
        if (mediaId.isNotBlank()) {
            val stremioPath = if (isTvShow) "series/$mediaId:$season:$episode.json" else "movie/$mediaId.json"
            endpoints.add("https://mediafusion.elfhosted.com/stream/$stremioPath")
        }

        for (endpoint in endpoints) {
            try {
                val req = Request.Builder()
                    .url(endpoint)
                    .header("User-Agent", BROWSER_USER_AGENT)
                    .build()
                val fastClient = okHttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofMillis(3000))
                    .readTimeout(java.time.Duration.ofMillis(3500))
                    .build()

                val resp = fastClient.newCall(req).execute()
                resp.use { r ->
                    if (r.isSuccessful) {
                        val body = r.body?.string() ?: return@use
                        val json = JSONObject(body)
                        val streamsArr = json.optJSONArray("streams")
                        if (streamsArr != null && streamsArr.length() > 0) {
                            var bestStreamObj: JSONObject? = null
                            var bestLangs = listOf("Original")
                            for (i in 0 until streamsArr.length()) {
                                val sObj = streamsArr.optJSONObject(i) ?: continue
                                val title = sObj.optString("title")
                                val isHindi = title.contains("hin", ignoreCase = true)
                                val isDual = title.contains("dual", ignoreCase = true) || title.contains("multi", ignoreCase = true)
                                if (isHindi || isDual) {
                                    bestStreamObj = sObj
                                    bestLangs = listOf("Hindi", "English")
                                    break
                                }
                            }
                            if (bestStreamObj == null) {
                                bestStreamObj = streamsArr.optJSONObject(0)
                            }

                            if (bestStreamObj != null) {
                                val url = bestStreamObj.optString("url")
                                val headersMap = mutableMapOf<String, String>()
                                val headersObj = bestStreamObj.optJSONObject("headers")
                                if (headersObj != null) {
                                    val keys = headersObj.keys()
                                    while (keys.hasNext()) {
                                        val k = keys.next()
                                        headersMap[k] = headersObj.optString(k)
                                    }
                                }

                                if (url.isNotBlank()) {
                                    Log.d(TAG, "Multi-Source Engine resolved stream from $endpoint: $url")
                                    return@withContext ResolvedStream(
                                        server = server.copy(audioLanguages = bestLangs),
                                        streamUrl = url,
                                        isHls = url.contains(".m3u8"),
                                        headers = headersMap,
                                        audioLanguages = bestLangs
                                    )
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Try next endpoint
            }
        }

        resolveVidLink(tmdbId, server, isTvShow, season, episode)
    }
}
