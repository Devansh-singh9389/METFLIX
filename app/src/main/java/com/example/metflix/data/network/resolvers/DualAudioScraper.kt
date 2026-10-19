package com.example.metflix.data.network.resolvers

import android.util.Base64
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
        episode: Int = 1,
        imdbId: String? = null
    ): ResolvedStream? = withContext(Dispatchers.IO) {
        val mediaType = if (isTvShow) "TV S${season}E${episode}" else "Movie"
        Log.d(TAG, "Resolving Dual-Audio (Hindi + English + Regional) stream for '$movieTitle' ($mediaType, TMDB: $tmdbId, IMDb: $imdbId)")

        // 1. Try Multi-Source Engine (Stremio / MediaFusion / Local daemon)
        tryMultiSourceEngine(tmdbId, server, isTvShow, season, episode, imdbId)?.let { return@withContext it }

        // 2. Try StreamFlix Dual-Audio direct match
        tryStreamFlixDualAudio(tmdbId, movieTitle, server)?.let { return@withContext it }

        // 3. Try Vega / HubCloud Dual-Audio sources
        tryVegaHubCloudDualAudio(movieTitle, releaseYear, server, isTvShow, season, episode)?.let { return@withContext it }

        null
    }

    private suspend fun tryMultiSourceEngine(
        tmdbId: String,
        server: StreamServer,
        isTvShow: Boolean = false,
        season: Int = 1,
        episode: Int = 1,
        imdbId: String? = null
    ): ResolvedStream? = withContext(Dispatchers.IO) {
        val path = if (isTvShow) "tv/$tmdbId/$season/$episode" else "movie/$tmdbId"
        val endpoints = mutableListOf(
            "http://10.227.10.225:8787/api/streams/$path",
            "http://127.0.0.1:8787/api/streams/$path",
            "https://representative-grams-take-incoming.trycloudflare.com:8787/api/streams/$path"
        )

        // Add MediaFusion public Stremio stream addon when media ID or IMDb ID is available
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

                fastHttpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string() ?: return@use
                        val json = JSONObject(body)
                        val streamsArr = json.optJSONArray("streams") ?: return@use

                        for (i in 0 until streamsArr.length()) {
                            val sObj = streamsArr.optJSONObject(i) ?: continue
                            val title = sObj.optString("title") + " " + sObj.optString("name")
                            val isHindi = title.contains("hin", ignoreCase = true)
                            val isDual = title.contains("dual", ignoreCase = true) || title.contains("multi", ignoreCase = true)
                            val isRegional = isHindi || isDual ||
                                    title.contains("tamil", ignoreCase = true) ||
                                    title.contains("telugu", ignoreCase = true) ||
                                    title.contains("malayalam", ignoreCase = true) ||
                                    title.contains("kannada", ignoreCase = true) ||
                                    title.contains("bengali", ignoreCase = true) ||
                                    title.contains("jap", ignoreCase = true) ||
                                    title.contains("dub", ignoreCase = true)

                            if (isRegional || isHindi || isDual) {
                                val url = sObj.optString("url")
                                if (url.isNotBlank() && url.startsWith("http")) {
                                    val headersMap = mutableMapOf<String, String>()
                                    val headersObj = sObj.optJSONObject("headers")
                                    if (headersObj != null) {
                                        val keys = headersObj.keys()
                                        while (keys.hasNext()) {
                                            val k = keys.next()
                                            headersMap[k] = headersObj.optString(k)
                                        }
                                    }

                                    val detectedLangs = mutableListOf<String>()
                                    if (title.contains("hin", ignoreCase = true)) detectedLangs.add("Hindi")
                                    if (title.contains("eng", ignoreCase = true)) detectedLangs.add("English")
                                    if (title.contains("tam", ignoreCase = true)) detectedLangs.add("Tamil")
                                    if (title.contains("tel", ignoreCase = true)) detectedLangs.add("Telugu")
                                    if (title.contains("mal", ignoreCase = true)) detectedLangs.add("Malayalam")
                                    if (title.contains("jap", ignoreCase = true)) detectedLangs.add("Japanese")
                                    if (detectedLangs.isEmpty()) detectedLangs.addAll(listOf("Hindi", "English"))

                                    Log.d(TAG, "Multi-Source Engine found Multi-Audio stream from $endpoint: $url")
                                    return@withContext ResolvedStream(
                                        server = server.copy(
                                            audioLanguages = detectedLangs,
                                            badge = if (detectedLangs.contains("Hindi")) "Hindi+Eng" else "Multi-Audio"
                                        ),
                                        streamUrl = url,
                                        isHls = url.contains(".m3u8"),
                                        headers = headersMap,
                                        isRegional = true,
                                        audioLanguages = detectedLangs
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
                    val normalizedCleanTitle = movieTitle.replace(":", " ").replace("-", " ")
                        .replace(Regex("[^a-zA-Z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim().lowercase()

                    for (i in 0 until items.length()) {
                        val it = items.optJSONObject(i) ?: continue
                        val rawName = it.optString("moviename")
                        val normalizedName = rawName.replace(":", " ").replace("-", " ")
                            .replace(Regex("[^a-zA-Z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim().lowercase()

                        if (normalizedName.contains(normalizedCleanTitle) || normalizedCleanTitle.contains(normalizedName)) {
                            val movieLink = it.optString("movielink")
                            val isHindi = movieLink.contains("hin", ignoreCase = true) || rawName.contains("hindi", ignoreCase = true)
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
        server: StreamServer,
        isTvShow: Boolean = false,
        season: Int = 1,
        episode: Int = 1
    ): ResolvedStream? = withContext(Dispatchers.IO) {
        val sPad = String.format("%02d", season)
        val ePad = String.format("%02d", episode)

        val cleanTitle = movieTitle.replace(":", " ").replace("-", " ")
            .replace(Regex("[^a-zA-Z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()

        val queries = mutableListOf<String>()
        if (isTvShow) {
            queries.add("$cleanTitle S${sPad}E${ePad}")
            queries.add("$cleanTitle Season $season")
        } else {
            if (releaseYear.isNotBlank()) {
                queries.add("$cleanTitle $releaseYear")
            }
            queries.add(cleanTitle)
            queries.add("$cleanTitle Hindi")
        }

        val baseDomains = listOf(
            "https://new2.movies4u.garden/?s=",
            "https://vegamovies.vodka/?s=",
            "https://vegamovies.ist/?s=",
            "https://4khdhub.one/?s=",
            "https://new8.hdhub4u.cl/?s=",
            "https://hdhub4u.ms/?s=",
            "https://katmoviehd.tattoo/?s=",
            "https://bollyflix.city/?s="
        )

        val searchUrls = mutableListOf<String>()
        for (q in queries) {
            val enc = URLEncoder.encode(q, "UTF-8")
            for (domain in baseDomains) {
                searchUrls.add("$domain$enc")
            }
        }

        for (searchUrl in searchUrls) {
            try {
                val req = Request.Builder()
                    .url(searchUrl)
                    .header("User-Agent", BROWSER_USER_AGENT)
                    .build()

                fastHttpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use
                    val html = resp.body?.string().orEmpty()

                    // Match article post URLs containing dual audio keywords in link or text
                    val postMatcher = Pattern.compile(
                        """href=["'](https?://[^"']*(?:hindi|dual|multi)[^"']*)["'][^>]*>([^<]*)</a>|href=["'](https?://[^"']+)["'][^>]*>([^<]*(?:Hindi|Dual|Multi)[^<]*)</a>""",
                        Pattern.CASE_INSENSITIVE
                    ).matcher(html)

                    if (postMatcher.find()) {
                        val postUrl = postMatcher.group(1) ?: postMatcher.group(3)
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
                                    headers = mapOf(
                                        "User-Agent" to BROWSER_USER_AGENT,
                                        "Referer" to "https://vcloud.beer/"
                                    ),
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

                // 1. Check for VCloud or HubCloud intermediate redirect links
                val vcloudMatcher = Pattern.compile("""href=["'](https?://[^"']*(?:vcloud|hubcloud)[^"']*)["']""", Pattern.CASE_INSENSITIVE).matcher(html)
                if (vcloudMatcher.find()) {
                    val vcloudUrl = vcloudMatcher.group(1)
                    if (!vcloudUrl.isNullOrBlank()) {
                        val direct = resolveVcloudToDirectStream(vcloudUrl)
                        if (direct != null) return direct
                    }
                }

                // 2. Look for JS embedded hubcloud/vcloud URLs
                val linkRegex = Pattern.compile("""var\s+url\s*=\s*['"]([^'"]+)['"]""")
                val linkMatcher = linkRegex.matcher(html)
                if (linkMatcher.find()) {
                    val hubUrl = linkMatcher.group(1)
                    if (!hubUrl.isNullOrBlank() && (hubUrl.contains("hub") || hubUrl.contains("vcloud"))) {
                        val direct = resolveVcloudToDirectStream(hubUrl)
                        if (direct != null) return direct
                    }
                }

                // 3. Look for direct Cloudflare Workers or direct .mkv/.mp4 files
                val directMatcher = Pattern.compile("""href=["'](https?://[^"']*(?:workers\.dev|pixeldrain|\.mkv|\.mp4)[^"']*)["']""", Pattern.CASE_INSENSITIVE).matcher(html)
                if (directMatcher.find()) {
                    return directMatcher.group(1)
                }

                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Resolves a VCloud / HubCloud link (e.g. https://vcloud.beer/sepiqm8zv2v7z7y)
     * through its token redirect into the final raw Cloudflare Worker .mkv stream.
     */
    private fun resolveVcloudToDirectStream(vcloudUrl: String): String? {
        return try {
            val req1 = Request.Builder()
                .url(vcloudUrl)
                .header("User-Agent", BROWSER_USER_AGENT)
                .header("Referer", "https://new2.movies4u.garden/")
                .build()

            val step1Html = fastHttpClient.newCall(req1).execute().use { resp ->
                if (!resp.isSuccessful) return null
                resp.body?.string().orEmpty()
            }

            var nextUrl: String? = null

            // 1. In VCloud / HubCloud, token redirect is encoded in JS via double base64: atob(atob('...'))
            val atobMatcher = Pattern.compile("""atob\(atob\(['"]([^'"]+)['"]\)\)""", Pattern.CASE_INSENSITIVE).matcher(step1Html)
            if (atobMatcher.find()) {
                val b64 = atobMatcher.group(1)
                try {
                    val step1Decoded = String(Base64.decode(b64, Base64.DEFAULT), Charsets.UTF_8)
                    nextUrl = String(Base64.decode(step1Decoded, Base64.DEFAULT), Charsets.UTF_8)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to decode vcloud double base64 token: ${e.message}")
                }
            }

            // 2. Fallback to direct href matching if rendered server-side
            if (nextUrl.isNullOrBlank()) {
                val tokenMatcher = Pattern.compile("""href=["'](https?://[^"']+\?token=[^"']+)["']""", Pattern.CASE_INSENSITIVE).matcher(step1Html)
                val downloadElementMatcher = Pattern.compile("""href=["'](https?://[^"']*(?:hubcloud\.php|download|\.workers\.dev)[^"']*)["']""", Pattern.CASE_INSENSITIVE).matcher(step1Html)
                nextUrl = when {
                    tokenMatcher.find() -> tokenMatcher.group(1)
                    downloadElementMatcher.find() -> downloadElementMatcher.group(1)
                    else -> null
                }
            }

            if (!nextUrl.isNullOrBlank()) {
                val req2 = Request.Builder()
                    .url(nextUrl)
                    .header("User-Agent", BROWSER_USER_AGENT)
                    .header("Referer", vcloudUrl)
                    .build()

                val step2Html = fastHttpClient.newCall(req2).execute().use { resp ->
                    if (!resp.isSuccessful) return null
                    resp.body?.string().orEmpty()
                }

                // Look for direct Cloudflare R2, Workers, or raw .mkv download links
                val finalStreamMatcher = Pattern.compile("""href=["'](https?://[^"']*(?:workers\.dev|r2\.cloudflarestorage\.com|hub2|fslserver|buzzserver)[^"']*\.(?:mkv|mp4)[^"']*)["']""", Pattern.CASE_INSENSITIVE).matcher(step2Html)
                if (finalStreamMatcher.find()) {
                    return finalStreamMatcher.group(1)?.replace("&amp;", "&")
                }

                val workerMatcher = Pattern.compile("""href=["'](https?://[^"']+\.workers\.dev/[^"']+)["']""", Pattern.CASE_INSENSITIVE).matcher(step2Html)
                if (workerMatcher.find()) {
                    return workerMatcher.group(1)?.replace("&amp;", "&")
                }
            }

            null
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving VCloud link $vcloudUrl: ${e.message}")
            null
        }
    }
}
