package com.example.metflix.data.network.providers

import android.util.Log
import com.example.metflix.data.entities.MediaId
import com.example.metflix.data.entities.StreamCandidate
import com.example.metflix.data.entities.StreamFormat
import com.example.metflix.data.network.resolvers.AudioLanguageDetector
import com.example.metflix.data.network.torbox.TorBoxCachedFile
import com.example.metflix.data.network.torbox.TorBoxCachedItem
import com.example.metflix.data.network.torbox.TorBoxClient
import com.example.metflix.data.settings.DebridPreferences
import com.example.metflix.data.settings.DebridProviderType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AllDebridProvider @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val debridPreferences: DebridPreferences,
    private val torBoxClient: TorBoxClient
) : StreamProvider {

    companion object {
        private const val TAG = "DebridProvider"
        private const val TORRENTIO_BASE = "https://torrentio.strem.fun"
        private const val BROWSER_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
    }

    override val id: String = "alldebrid"
    override val displayName: String
        get() = when (debridPreferences.selectedProvider.value) {
            DebridProviderType.ALLDEBRID -> "AllDebrid Cloud [AD+]"
            DebridProviderType.TORBOX -> "TorBox Cloud [TB+]"
        }
    override val priority: Int = 200

    private val fastHttpClient: OkHttpClient by lazy {
        okHttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofMillis(3500))
            .readTimeout(java.time.Duration.ofMillis(4000))
            .followRedirects(true)
            .build()
    }

    override fun canHandle(mediaId: MediaId): Boolean {
        if (!debridPreferences.hasValidKey()) return false
        return when (mediaId) {
            is MediaId.Movie -> !mediaId.imdbId.isNullOrBlank()
            is MediaId.TvShow -> !mediaId.imdbId.isNullOrBlank()
            is MediaId.Anime -> false
        }
    }

    override suspend fun findStreams(
        mediaId: MediaId,
        title: String,
        releaseYear: String
    ): List<StreamCandidate> = withContext(Dispatchers.IO) {
        val apiKey = debridPreferences.apiKey.value.trim()
        if (apiKey.isBlank() || !debridPreferences.isEnabled.value) {
            return@withContext emptyList()
        }

        val requestPath = when (mediaId) {
            is MediaId.Movie -> {
                val imdb = mediaId.imdbId ?: return@withContext emptyList()
                "stream/movie/$imdb.json"
            }
            is MediaId.TvShow -> {
                val imdb = mediaId.imdbId ?: return@withContext emptyList()
                "stream/series/$imdb:${mediaId.seasonNumber}:${mediaId.episodeNumber}.json"
            }
            is MediaId.Anime -> return@withContext emptyList()
        }

        if (debridPreferences.selectedProvider.value == DebridProviderType.TORBOX) {
            return@withContext resolveTorBoxStreams(mediaId, title, apiKey, requestPath)
        }

        // AllDebrid resolution path
        val endpointUrl = "$TORRENTIO_BASE/alldebrid=$apiKey/$requestPath"
        val maskedUrl = "$TORRENTIO_BASE/alldebrid=***REDACTED***/$requestPath"
        Log.d(TAG, "Fetching AllDebrid cached streams for '$title' ($maskedUrl)")

        try {
            val req = Request.Builder()
                .url(endpointUrl)
                .header("User-Agent", BROWSER_USER_AGENT)
                .build()

            fastHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.w(TAG, "Torrentio AllDebrid query failed with HTTP ${resp.code}")
                    return@withContext emptyList()
                }

                val bodyStr = resp.body?.string().orEmpty()
                val json = JSONObject(bodyStr)
                val streamsArray = json.optJSONArray("streams") ?: return@withContext emptyList()

                val candidates = mutableListOf<StreamCandidate>()

                for (i in 0 until streamsArray.length()) {
                    val streamObj = streamsArray.optJSONObject(i) ?: continue
                    val directUrl = streamObj.optString("url")

                    if (directUrl.isBlank()) continue

                    val streamName = streamObj.optString("name")
                    val streamTitle = streamObj.optString("title")
                    val fullDesc = "$streamName $streamTitle"

                    val isHindi = AudioLanguageDetector.isHindiTrack(fullDesc, fullDesc) ||
                            fullDesc.contains("hindi", ignoreCase = true) ||
                            fullDesc.contains("dual", ignoreCase = true)

                    val reportedLangs = if (isHindi) {
                        listOf("Hindi", "English")
                    } else {
                        listOf("English")
                    }

                    val quality = when {
                        fullDesc.contains("4k", ignoreCase = true) || fullDesc.contains("2160p", ignoreCase = true) -> "4K UHD"
                        fullDesc.contains("1080p", ignoreCase = true) -> "1080p"
                        fullDesc.contains("720p", ignoreCase = true) -> "720p"
                        else -> "HD"
                    }

                    val format = when {
                        directUrl.contains(".m3u8") -> StreamFormat.HLS
                        directUrl.contains(".mp4") -> StreamFormat.MP4
                        else -> StreamFormat.MKV
                    }

                    candidates.add(
                        StreamCandidate(
                            providerId = id,
                            providerName = "AllDebrid [$quality${if (isHindi) " • Hindi+Eng" else ""}]",
                            streamUrl = directUrl,
                            format = format,
                            reportedLanguages = reportedLangs,
                            isAudioVerified = isHindi,
                            headers = mapOf("User-Agent" to BROWSER_USER_AGENT),
                            latencyMs = 35L,
                            quality = quality,
                            isDirectStream = true
                        )
                    )
                }

                Log.d(TAG, "Found ${candidates.size} AllDebrid cached streams for '$title'")

                candidates.sortedWith(
                    compareByDescending<StreamCandidate> { it.isAudioVerified }
                        .thenByDescending { it.quality == "4K UHD" }
                        .thenByDescending { it.quality == "1080p" }
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving AllDebrid streams: ${e.message}")
            emptyList()
        }
    }

    /**
     * Resolves TorBox streams directly by:
     * 1. Querying unauthenticated public index (never sends TorBox credentials to Torrentio).
     * 2. Querying TorBox checkcached directly via HTTPS with the user's TorBox API key.
     * 3. Strictly matching target episode files (with NO fallback to wrong files).
     * 4. Requesting direct high-speed CDN playback URLs from TorBox.
     */
    private suspend fun resolveTorBoxStreams(
        mediaId: MediaId,
        title: String,
        apiKey: String,
        requestPath: String
    ): List<StreamCandidate> = coroutineScope {
        try {
            // 1. Obtain candidate info-hashes from public catalog (UNAUTHENTICATED)
            val catalogUrl = "$TORRENTIO_BASE/$requestPath"
            Log.d(TAG, "TorBox resolving candidate info-hashes via public catalog ($catalogUrl)")

            val req = Request.Builder()
                .url(catalogUrl)
                .header("User-Agent", BROWSER_USER_AGENT)
                .build()

            val candidateHashes = mutableListOf<TorrentioCandidate>()
            fastHttpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val bodyStr = resp.body?.string().orEmpty()
                    val json = JSONObject(bodyStr)
                    val streams = json.optJSONArray("streams")
                    if (streams != null) {
                        for (i in 0 until streams.length()) {
                            val streamObj = streams.optJSONObject(i) ?: continue
                            val hash = streamObj.optString("infoHash").trim().lowercase(Locale.ROOT)
                            if (hash.isNotBlank()) {
                                candidateHashes.add(
                                    TorrentioCandidate(
                                        infoHash = hash,
                                        title = streamObj.optString("title"),
                                        name = streamObj.optString("name"),
                                        fileIdx = if (streamObj.has("fileIdx")) streamObj.optInt("fileIdx") else null
                                    )
                                )
                            }
                        }
                    }
                } else {
                    Log.w(TAG, "Public catalog query returned HTTP ${resp.code}")
                }
            }

            val distinctHashes = candidateHashes.map { it.infoHash }.distinct().take(15)

            // 2. Query TorBox checkcached directly with user's TorBox key
            val cachedItems = if (distinctHashes.isNotEmpty()) {
                torBoxClient.checkCachedTorrents(apiKey, distinctHashes)
            } else {
                emptyList()
            }

            val cachedMap = cachedItems.filter { it.isCached }.associateBy { it.hash.lowercase(Locale.ROOT) }
            val candidateMap = candidateHashes.associateBy { it.infoHash }

            data class TargetResolution(
                val hash: String,
                val name: String,
                val fileId: Int?,
                val fullDesc: String,
                val quality: String,
                val isHindi: Boolean,
                val score: Int
            )

            val rawTargets = mutableListOf<TargetResolution>()

            for ((hash, cachedItem) in cachedMap) {
                val candidate = candidateMap[hash]
                val torrentName = cachedItem.name.ifBlank { candidate?.title ?: title }
                val cleanedName = cleanReleaseTitle(torrentName)
                val fullDesc = "${candidate?.name.orEmpty()} ${candidate?.title.orEmpty()} $torrentName $cleanedName"

                // Filter out completely mismatched releases (e.g. Spider-Man when searching for Obsession)
                if (!isTitleMatching(cleanedName, title)) {
                    Log.d(TAG, "Rejecting mismatched TorBox release '$torrentName' for target '$title'")
                    continue
                }

                val targetFileId: Int? = when (mediaId) {
                    is MediaId.TvShow -> {
                        if (cachedItem.files.isNotEmpty()) {
                            val matched = matchEpisodeFile(cachedItem.files, mediaId.seasonNumber, mediaId.episodeNumber)
                            if (matched == null) {
                                // Strictly reject torrent if specific episode file is missing
                                Log.d(TAG, "Skipping TorBox torrent '$torrentName': missing S${mediaId.seasonNumber}E${mediaId.episodeNumber}")
                                continue
                            }
                            matched.id
                        } else {
                            candidate?.fileIdx
                        }
                    }
                    is MediaId.Movie -> {
                        if (cachedItem.files.isNotEmpty()) {
                            val videoFile = cachedItem.files.filter { f ->
                                val ext = f.name.substringAfterLast('.', "").lowercase(Locale.ROOT)
                                ext in listOf("mkv", "mp4", "avi", "mov", "ts", "m4v")
                            }.maxByOrNull { it.size } ?: cachedItem.files.maxByOrNull { it.size }
                            videoFile?.id
                        } else {
                            candidate?.fileIdx
                        }
                    }
                    is MediaId.Anime -> continue
                }

                val isHindi = AudioLanguageDetector.isHindiTrack(fullDesc, fullDesc) ||
                        fullDesc.contains("hindi", ignoreCase = true) ||
                        fullDesc.contains("dual", ignoreCase = true)

                val quality = when {
                    fullDesc.contains("4k", ignoreCase = true) || fullDesc.contains("2160p", ignoreCase = true) -> "4K UHD"
                    fullDesc.contains("1080p", ignoreCase = true) -> "1080p"
                    fullDesc.contains("720p", ignoreCase = true) -> "720p"
                    else -> "HD"
                }

                var score = 0
                if (isHindi) score += 2000
                if (quality == "1080p") score += 500
                else if (quality == "720p") score += 400
                else if (quality == "4K UHD") score += 300

                rawTargets.add(TargetResolution(hash, cleanedName, targetFileId, fullDesc, quality, isHindi, score))
            }

            // Deduplicate by tier (quality + isHindi) so we only keep the single best release per tier
            val targetsToResolve = rawTargets
                .sortedByDescending { it.score }
                .distinctBy { "${it.quality}_${it.isHindi}" }

            // Fetch user's existing account torrents to reuse active torrent ID if already added
            val userTorrents = torBoxClient.getUserTorrents(apiKey)
            val userTorrentByHash = userTorrents.associateBy { it.hash.lowercase(Locale.ROOT) }

            // 3. Resolve direct streaming URLs on-demand for top candidates (sequential fallback, NOT 5 concurrent creations)
            val resolvedCandidates = mutableListOf<StreamCandidate>()

            for (target in targetsToResolve.take(3)) {
                try {
                    val existingTorrent = userTorrentByHash[target.hash]
                    val torrentId = existingTorrent?.id?.takeIf { it > 0 } ?: run {
                        val encodedName = URLEncoder.encode(target.name.ifBlank { title }, "UTF-8")
                        val magnet = "magnet:?xt=urn:btih:${target.hash}&dn=$encodedName"
                        torBoxClient.createTorrent(apiKey, magnet)
                    }

                    if (torrentId == null || torrentId <= 0) continue

                    // Obtain true torrent details with real server file IDs
                    val torrentDetails = existingTorrent ?: run {
                        var details = torBoxClient.getTorrentDetails(apiKey, torrentId)
                        if (details == null || details.files.isEmpty()) {
                            delay(1000)
                            details = torBoxClient.getTorrentDetails(apiKey, torrentId)
                        }
                        details
                    }

                    val realFileId = when (mediaId) {
                        is MediaId.TvShow -> {
                            val matched = if (torrentDetails != null && torrentDetails.files.isNotEmpty()) {
                                matchEpisodeFile(torrentDetails.files, mediaId.seasonNumber, mediaId.episodeNumber)
                            } else null
                            if (matched == null && torrentDetails != null && torrentDetails.files.isNotEmpty()) {
                                Log.d(TAG, "Skipping TorBox torrent '${target.name}': missing S${mediaId.seasonNumber}E${mediaId.episodeNumber}")
                                continue
                            }
                            matched?.id ?: target.fileId
                        }
                        is MediaId.Movie -> {
                            val videoFile = torrentDetails?.files?.filter { f ->
                                val ext = f.name.substringAfterLast('.', "").lowercase(Locale.ROOT)
                                ext in listOf("mkv", "mp4", "avi", "mov", "ts", "m4v")
                            }?.maxByOrNull { it.size } ?: torrentDetails?.files?.maxByOrNull { it.size }
                            videoFile?.id ?: target.fileId
                        }
                        is MediaId.Anime -> null
                    }

                    val directUrl = torBoxClient.requestDownloadLink(apiKey, torrentId, realFileId)
                    if (directUrl.isNullOrBlank()) continue

                    val reportedLangs = if (target.isHindi) listOf("Hindi", "English") else listOf("English")

                    val format = when {
                        directUrl.contains(".m3u8") -> StreamFormat.HLS
                        directUrl.contains(".mp4") -> StreamFormat.MP4
                        else -> StreamFormat.MKV
                    }

                    resolvedCandidates.add(
                        StreamCandidate(
                            providerId = id,
                            providerName = "TorBox Cloud [${target.quality}${if (target.isHindi) " • Hindi+Eng" else ""}]",
                            streamUrl = directUrl,
                            format = format,
                            reportedLanguages = reportedLangs,
                            isAudioVerified = target.isHindi,
                            headers = mapOf("User-Agent" to BROWSER_USER_AGENT),
                            latencyMs = 28L,
                            quality = target.quality,
                            isDirectStream = true
                        )
                    )

                    // If we have resolved 1 top high-priority stream (or 2 distinct options: Hindi + 1080p/720p), stop creating more torrents!
                    if (resolvedCandidates.size >= 2) break
                } catch (e: Exception) {
                    Log.w(TAG, "Error resolving TorBox target ${target.hash}: ${e.message}")
                }
            }

            // 4. Fallback to user's saved account library if no streams resolved from public catalog
            if (resolvedCandidates.isEmpty()) {
                Log.d(TAG, "No cached torrents found via public catalog; checking user's TorBox account library for '$title'")
                resolvedCandidates.addAll(resolveTorBoxFromLibrary(mediaId, title, apiKey, userTorrents))
            }

            resolvedCandidates.sortedWith(
                compareByDescending<StreamCandidate> { it.isAudioVerified }
                    .thenByDescending { it.quality == "4K UHD" }
                    .thenByDescending { it.quality == "1080p" }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error in TorBox direct cache resolution: ${e.message}")
            resolveTorBoxFromLibrary(mediaId, title, apiKey)
        }
    }

    /**
     * Resolves matching titles directly from user's existing TorBox account library.
     * Enforces strict TV episode matching with NO fallback to wrong files.
     */
    private suspend fun resolveTorBoxFromLibrary(
        mediaId: MediaId,
        title: String,
        apiKey: String,
        preloadedTorrents: List<TorBoxCachedItem>? = null
    ): List<StreamCandidate> {
        try {
            val userTorrents = preloadedTorrents ?: torBoxClient.getUserTorrents(apiKey)
            if (userTorrents.isEmpty()) return emptyList()

            val cleanTitle = title.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"), " ").trim()
            val titleTokens = cleanTitle.split(" ").filter { it.length > 2 }
            if (titleTokens.isEmpty()) return emptyList()

            val directCandidates = mutableListOf<StreamCandidate>()

            for (torrent in userTorrents) {
                val torrentName = torrent.name.lowercase(Locale.ROOT)
                val matchesTitle = titleTokens.any { torrentName.contains(it) }
                if (!matchesTitle) continue

                val matchedFile = when (mediaId) {
                    is MediaId.TvShow -> {
                        matchEpisodeFile(torrent.files, mediaId.seasonNumber, mediaId.episodeNumber)
                    }
                    is MediaId.Movie -> {
                        torrent.files.filter { f ->
                            val ext = f.name.substringAfterLast('.', "").lowercase(Locale.ROOT)
                            ext in listOf("mkv", "mp4", "avi", "mov", "ts", "m4v")
                        }.maxByOrNull { it.size } ?: torrent.files.maxByOrNull { it.size }
                    }
                    is MediaId.Anime -> null
                }

                // Strict enforcement: Drop torrent if TV episode was not matched
                if (matchedFile == null) continue

                val targetFileId = matchedFile.id
                val downloadLink = torBoxClient.requestDownloadLink(apiKey, torrent.id, targetFileId)
                if (!downloadLink.isNullOrBlank()) {
                    val isHindi = AudioLanguageDetector.isHindiTrack(torrent.name, matchedFile.name)
                    val format = when {
                        downloadLink.contains(".m3u8") -> StreamFormat.HLS
                        downloadLink.contains(".mp4") -> StreamFormat.MP4
                        else -> StreamFormat.MKV
                    }
                    val quality = when {
                        torrent.name.contains("4k", ignoreCase = true) || torrent.name.contains("2160p", ignoreCase = true) -> "4K UHD"
                        torrent.name.contains("1080p", ignoreCase = true) -> "1080p"
                        else -> "HD"
                    }

                    directCandidates.add(
                        StreamCandidate(
                            providerId = id,
                            providerName = "TorBox Cloud [Library • $quality${if (isHindi) " • Hindi" else ""}]",
                            streamUrl = downloadLink,
                            format = format,
                            reportedLanguages = if (isHindi) listOf("Hindi", "English") else listOf("English"),
                            isAudioVerified = isHindi,
                            headers = mapOf("User-Agent" to BROWSER_USER_AGENT),
                            latencyMs = 25L,
                            quality = quality,
                            isDirectStream = true
                        )
                    )
                }
            }
            return directCandidates
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving TorBox library: ${e.message}")
            return emptyList()
        }
    }

    /**
     * Strictly matches a file in a torrent to the requested season and episode number.
     * Prevents playing the wrong episode when browsing multi-episode packs.
     */
    private fun matchEpisodeFile(files: List<TorBoxCachedFile>, season: Int, episode: Int): TorBoxCachedFile? {
        val sStr = String.format(Locale.US, "s%02d", season)
        val eStr = String.format(Locale.US, "e%02d", episode)
        val sNumE = "s${season}e${episode}"
        val numXnum = "${season}x%02d".format(Locale.US, episode)
        val numXnumSimple = "${season}x${episode}"
        val seasonEpisodeWords = "season $season episode $episode"
        val seasonEpisodeShort = "s$season e$episode"

        return files.firstOrNull { file ->
            val name = file.name.lowercase(Locale.ROOT)
            (name.contains(sStr) && name.contains(eStr)) ||
                    name.contains(sNumE) ||
                    name.contains(numXnum) ||
                    name.contains(numXnumSimple) ||
                    name.contains(seasonEpisodeWords) ||
                    name.contains(seasonEpisodeShort)
        }
    }

    /**
     * Strips promotional prefixes, Chinese bracketed tags, website URLs, and year noise.
     */
    private fun cleanReleaseTitle(raw: String): String {
        return raw
            // Remove website domains like www.SSDSSE.com or rarbg.to
            .replace(Regex("""(?i)(?:https?://)?(?:www\.)?[a-z0-9\-]+\.(?:com|org|net|to|cc|me|vip|xyz)"""), " ")
            // Remove full-width brackets like 【...】 or [HDR+杜比...]
            .replace(Regex("""【[^】]*】"""), " ")
            .replace(Regex("""\[[^\]]*\]"""), " ")
            // Replace dots, underscores, hyphens with spaces
            .replace(Regex("""[._\-]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    /**
     * Verifies that the release title actually corresponds to the requested movie/show.
     * Prevents totally unrelated releases (e.g. Spider-Man when searching for Obsession).
     */
    private fun isTitleMatching(cleanedRelease: String, targetTitle: String): Boolean {
        val cleanTarget = targetTitle.lowercase(Locale.ROOT).replace(Regex("""[^a-z0-9\s]"""), " ").trim()
        val targetTokens = cleanTarget.split(Regex("""\s+""")).filter { it.length >= 3 }
        if (targetTokens.isEmpty()) return true

        val releaseLower = cleanedRelease.lowercase(Locale.ROOT)
        // At least 60% of significant target title words must be present in the release title
        val matches = targetTokens.count { token -> releaseLower.contains(token) }
        val threshold = if (targetTokens.size == 1) 1 else (targetTokens.size * 0.6).toInt().coerceAtLeast(1)
        return matches >= threshold
    }

    private data class TorrentioCandidate(
        val infoHash: String,
        val title: String,
        val name: String,
        val fileIdx: Int?
    )
}
