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

package com.example.metflix.data.network.anime

import android.util.Base64
import android.util.Log
import com.example.metflix.data.network.anime.models.AnimeExtractedStream
import java.net.URLDecoder
import java.util.regex.Matcher
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

@Singleton
class AnimeStreamExtractor @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "AnimeStreamExtractor"
        private const val BROWSER_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
        private const val DEFAULT_REFERER = "https://watchanimeworld.one/"
        private const val DEFAULT_ORIGIN = "https://watchanimeworld.one"

        val DEFAULT_STREAM_HEADERS = mapOf(
            "User-Agent" to BROWSER_USER_AGENT,
            "Referer" to DEFAULT_REFERER,
            "Origin" to DEFAULT_ORIGIN,
            "Accept" to "*/*",
            "Accept-Language" to "en-US,en;q=0.9",
            "Sec-Fetch-Site" to "same-origin",
            "Sec-Fetch-Mode" to "cors",
            "Sec-Fetch-Dest" to "empty",
            "Sec-CH-UA-Platform" to "\"Windows\"",
            "Sec-CH-UA" to "\"Chromium\";v=\"124\", \"Not:A-Brand\";v=\"8\", \"Google Chrome\";v=\"124\"",
            "Sec-CH-UA-Mobile" to "?0"
        )

        private val M3U8_PATTERN = Pattern.compile("""(https?://[^\s"'<>]+\.m3u8[^\s"'<>]*)""")
        private val MP4_PATTERN = Pattern.compile("""(https?://[^\s"'<>]+\.mp4[^\s"'<>]*)""")
        private val FILE_PATTERN = Pattern.compile("""file\s*:\s*["']([^"']+\.(?:m3u8|mp4)[^"']*)["']""")
        private val SOURCES_PATTERN = Pattern.compile("""sources\s*:\s*\[\s*\{[^}]*?["']?file["']?\s*:\s*["']([^"']+)["']""")
        private val SOURCE_TAG_PATTERN = Pattern.compile("""<source[^>]+src=["']([^"']+\.(?:m3u8|mp4)[^"']*)["']""")
        private val CONFIG_PATTERN = Pattern.compile("""(?:var|let|const|window\.)\s*CONFIG\s*=\s*(\{.+?\});""", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)
        private val PACKED_JS_PATTERN = Pattern.compile("""eval\(function\(p,a,c,k,e,[rd]\).+?\.split\('\|'\)\)\)""", Pattern.DOTALL)
    }

    private val fastHttpClient: OkHttpClient by lazy {
        okHttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(15))
            .readTimeout(java.time.Duration.ofSeconds(20))
            .followRedirects(true)
            .build()
    }

    suspend fun extractStream(
        embedUrl: String,
        preferredLanguage: String = "hin"
    ): AnimeExtractedStream = withContext(Dispatchers.IO) {
        val cleanUrl = embedUrl.trim()
        if (cleanUrl.isBlank()) {
            return@withContext AnimeExtractedStream(cleanUrl, DEFAULT_STREAM_HEADERS, isDirectStream = false)
        }

        // 1. Direct stream check
        if (cleanUrl.contains(".m3u8") || cleanUrl.contains(".mp4")) {
            return@withContext AnimeExtractedStream(cleanUrl, DEFAULT_STREAM_HEADERS, isDirectStream = true)
        }

        // 2. Base64 decoded data URL check (common in movie embeds)
        if (cleanUrl.contains("data=")) {
            parseDataQueryParam(cleanUrl)?.let { directUrl ->
                return@withContext AnimeExtractedStream(directUrl, DEFAULT_STREAM_HEADERS, isDirectStream = true)
            }
        }

        // 3. Inspect upstream embed page
        try {
            val html = fetchPageHtml(cleanUrl)
            if (html.isNotBlank()) {
                // If this is Dub Player (Abyss hub with audio configurations)
                val configMatcher = CONFIG_PATTERN.matcher(html)
                if (configMatcher.find()) {
                    val configJson = configMatcher.group(1)
                    val playerUrl = parseDubPlayerConfig(configJson, preferredLanguage)
                    if (!playerUrl.isNullOrBlank()) {
                        if (playerUrl.contains(".m3u8") || playerUrl.contains(".mp4")) {
                            return@withContext AnimeExtractedStream(playerUrl, DEFAULT_STREAM_HEADERS, isDirectStream = true)
                        }
                        // Inspect inner player page
                        val innerHtml = fetchPageHtml(playerUrl)
                        findDirectMediaUrl(innerHtml)?.let { resolvedStream ->
                            return@withContext AnimeExtractedStream(
                                streamUrl = resolvedStream,
                                headers = mapOf(
                                    "User-Agent" to BROWSER_USER_AGENT,
                                    "Referer" to playerUrl,
                                    "Origin" to "https://player.abyssplayer.com",
                                    "Accept" to "*/*",
                                    "Accept-Language" to "en-US,en;q=0.9",
                                    "Sec-Fetch-Site" to "same-origin",
                                    "Sec-Fetch-Mode" to "cors",
                                    "Sec-Fetch-Dest" to "empty",
                                    "Sec-CH-UA-Platform" to "\"Windows\"",
                                    "Sec-CH-UA" to "\"Chromium\";v=\"124\", \"Not:A-Brand\";v=\"8\", \"Google Chrome\";v=\"124\"",
                                    "Sec-CH-UA-Mobile" to "?0"
                                ),
                                isDirectStream = true
                            )
                        }
                    }
                }

                // Check for direct media patterns in the initial HTML
                findDirectMediaUrl(html)?.let { directUrl ->
                    return@withContext AnimeExtractedStream(directUrl, DEFAULT_STREAM_HEADERS, isDirectStream = true)
                }

                // Check for Dean Edwards packed JS and unpack it
                val packedMatcher = PACKED_JS_PATTERN.matcher(html)
                if (packedMatcher.find()) {
                    val packed = packedMatcher.group(0).orEmpty()
                    if (packed.isNotBlank()) {
                        val unpacked = unpackDeanEdwards(packed)
                        findDirectMediaUrl(unpacked)?.let { directUrl ->
                            return@withContext AnimeExtractedStream(directUrl, DEFAULT_STREAM_HEADERS, isDirectStream = true)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Stream extraction fallback for $cleanUrl: ${e.message}")
        }

        // Fallback: Return embed URL flagged as NOT direct stream so player knows not to feed HTML to ExoPlayer
        AnimeExtractedStream(cleanUrl, DEFAULT_STREAM_HEADERS, isDirectStream = false)
    }

    /** Fetches and returns the raw .m3u8 text using the stream's headers. Used for diagnostics. */
    suspend fun fetchPlaylistText(url: String, headers: Map<String, String>): String = withContext(Dispatchers.IO) {
        try {
            val b = Request.Builder().url(url)
            headers.forEach { (k, v) -> b.header(k, v) }
            if (!headers.keys.any { it.equals("Accept", true) }) {
                b.header("Accept", "*/*")
            }
            if (!headers.keys.any { it.equals("Sec-Fetch-Site", true) }) {
                b.header("Sec-Fetch-Site", "same-origin")
            }
            if (!headers.keys.any { it.equals("Sec-Fetch-Mode", true) }) {
                b.header("Sec-Fetch-Mode", "cors")
            }
            if (!headers.keys.any { it.equals("Sec-Fetch-Dest", true) }) {
                b.header("Sec-Fetch-Dest", "empty")
            }
            fastHttpClient.newCall(b.build()).execute().use { r ->
                if (r.isSuccessful) r.body?.string().orEmpty() else "HTTP ${r.code}"
            }
        } catch (e: Exception) { "ERR: ${e.message}" }
    }

    private fun fetchPageHtml(url: String, referer: String = DEFAULT_REFERER): String {
        return try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", BROWSER_USER_AGENT)
                .header("Referer", referer)
                .header("Origin", DEFAULT_ORIGIN)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Sec-Fetch-Site", "cross-site")
                .header("Sec-Fetch-Mode", "navigate")
                .header("Sec-Fetch-Dest", "iframe")
                .header("Sec-CH-UA-Platform", "\"Windows\"")
                .header("Sec-CH-UA", "\"Chromium\";v=\"124\", \"Not:A-Brand\";v=\"8\", \"Google Chrome\";v=\"124\"")
                .header("Sec-CH-UA-Mobile", "?0")
                .build()
            fastHttpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string().orEmpty() else ""
            }
        } catch (_: Exception) {
            ""
        }
    }

    private fun findDirectMediaUrl(text: String): String? {
        if (text.isBlank()) return null

        // Unescape escaped JSON slashes (\/)
        val unescaped = text.replace("""\/""", "/")

        // 1. Protocol-relative URLs: //domain.com/hls/master.m3u8 -> https://domain.com/hls/master.m3u8
        val protoRelativeMatcher = Pattern.compile("""["'](//[^\s"'<>]+\.(?:m3u8|mp4)[^\s"'<>]*)["']""").matcher(unescaped)
        if (protoRelativeMatcher.find()) {
            return "https:" + protoRelativeMatcher.group(1)
        }

        val m3u8Matcher = M3U8_PATTERN.matcher(unescaped)
        if (m3u8Matcher.find()) return m3u8Matcher.group(1)

        val mp4Matcher = MP4_PATTERN.matcher(unescaped)
        if (mp4Matcher.find()) return mp4Matcher.group(1)

        val fileMatcher = FILE_PATTERN.matcher(unescaped)
        if (fileMatcher.find()) return fileMatcher.group(1)

        val sourcesMatcher = SOURCES_PATTERN.matcher(unescaped)
        if (sourcesMatcher.find()) return sourcesMatcher.group(1)

        val sourceTagMatcher = SOURCE_TAG_PATTERN.matcher(unescaped)
        if (sourceTagMatcher.find()) return sourceTagMatcher.group(1)

        return null
    }

    /**
     * Unpacks Dean Edwards JavaScript packer: eval(function(p,a,c,k,e,d){...})
     */
    private fun unpackDeanEdwards(packed: String): String {
        return try {
            val pRegex = Pattern.compile("""\}\('(.*)',\s*(\d+),\s*(\d+),\s*'(.*?)'\.split\('\|'\)""")
            val matcher = pRegex.matcher(packed)
            if (!matcher.find()) return packed

            val payload = matcher.group(1) ?: return packed
            val radix = matcher.group(2)?.toIntOrNull() ?: return packed
            val count = matcher.group(3)?.toIntOrNull() ?: return packed
            val symtab = matcher.group(4)?.split("|") ?: return packed

            val wordRegex = Pattern.compile("""\b\w+\b""")
            val wordMatcher = wordRegex.matcher(payload)
            val sb = StringBuffer()

            while (wordMatcher.find()) {
                val word = wordMatcher.group()
                val index = try {
                    word.toInt(radix)
                } catch (_: Exception) {
                    -1
                }
                val replacement = if (index in symtab.indices && symtab[index].isNotBlank()) {
                    symtab[index]
                } else {
                    word
                }
                wordMatcher.appendReplacement(sb, Matcher.quoteReplacement(replacement))
            }
            wordMatcher.appendTail(sb)
            sb.toString()
        } catch (_: Exception) {
            packed
        }
    }

    private fun parseDataQueryParam(url: String): String? {
        return try {
            val query = url.substringAfter("?", "")
            for (param in query.split("&")) {
                val pair = param.split("=")
                if (pair.size == 2 && pair[0] == "data") {
                    val raw = URLDecoder.decode(pair[1], "UTF-8")
                    val pad = (4 - raw.length % 4) % 4
                    val decodedBytes = Base64.decode(raw + "=".repeat(pad), Base64.DEFAULT)
                    val jsonStr = String(decodedBytes, Charsets.UTF_8)
                    val json = JSONObject(jsonStr)
                    val streamUrl = json.optString("file").takeIf { it.isNotBlank() }
                        ?: json.optString("url").takeIf { it.isNotBlank() }
                        ?: json.optString("src").takeIf { it.isNotBlank() }
                    return streamUrl
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun parseDubPlayerConfig(configJson: String?, preferredLanguage: String): String? {
        if (configJson.isNullOrBlank()) return null
        return try {
            val json = JSONObject(configJson)
            val prefix = json.optString("prefix", "https://player.abyssplayer.com/")
            val readyObj = json.optJSONObject("ready") ?: return null

            // Prioritize preferred language (hin, eng, jpn, etc.)
            val token = readyObj.optString(preferredLanguage).takeIf { it.isNotBlank() }
                ?: readyObj.optString("hin").takeIf { it.isNotBlank() }
                ?: readyObj.optString("eng").takeIf { it.isNotBlank() }
                ?: readyObj.optString("jpn").takeIf { it.isNotBlank() }
                ?: readyObj.keys().asSequence().firstOrNull()?.let { readyObj.optString(it) }

            if (!token.isNullOrBlank()) {
                val playerUrl = if (prefix.endsWith("/")) "$prefix$token" else "$prefix/$token"
                Log.d(TAG, "Resolved Dub Player target for $preferredLanguage: $playerUrl")
                return playerUrl
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Fetches a "pointer" file (a JSON/text/redirect that contains the real media URL)
     * and returns the media URL it points to, or null if none was found.
     */
    suspend fun resolvePointerUrl(
        url: String,
        headers: Map<String, String>
    ): String? = withContext(Dispatchers.IO) {
        try {
            val builder = Request.Builder().url(url)

            // 1. Send every header the caller provided (this should include the full Referer)
            headers.forEach { (k, v) -> builder.addHeader(k, v) }

            // 2. Add browser-like headers Zephyrix's CDN is known to check
            if (!headers.keys.any { it.equals("Accept", true) }) {
                builder.addHeader("Accept", "*/*")
            }
            if (!headers.keys.any { it.equals("Accept-Language", true) }) {
                builder.addHeader("Accept-Language", "en-US,en;q=0.9")
            }
            builder.addHeader("Sec-Fetch-Site", "same-origin")
            builder.addHeader("Sec-Fetch-Mode", "cors")
            builder.addHeader("Sec-Fetch-Dest", "empty")
            builder.addHeader("Sec-CH-UA-Platform", "\"Windows\"")
            builder.addHeader(
                "Sec-CH-UA",
                "\"Chromium\";v=\"124\", \"Not:A-Brand\";v=\"8\", \"Google Chrome\";v=\"124\""
            )
            builder.addHeader("Sec-CH-UA-Mobile", "?0")

            fastHttpClient.newCall(builder.build()).execute().use { resp ->
                Log.i(TAG, "Pointer response: status=${resp.code} len=${resp.body?.contentLength()}")
                if (!resp.isSuccessful) {
                    Log.w(TAG, "Pointer fetch HTTP ${resp.code}: ${resp.body?.string()?.take(200)}")
                    return@use null
                }
                val body = resp.body?.string().orEmpty()
                Log.i(TAG, "Pointer body (${body.length} bytes): ${body.take(400)}")

                // The "security error" is only 14 bytes; if we get that, the Referer was still wrong.
                if (body.isBlank() || body.length < 10 || body.trim() == "security error") {
                    Log.w(TAG, "Pointer body too short or security error. Referer rejected.")
                    return@use null
                }

                // ⭐ CRITICAL: If the pointer file itself is an HLS playlist (e.g. Zephyrix master.txt starts with #EXTM3U)
                if (body.trimStart().startsWith("#EXTM3U")) {
                    Log.i(TAG, "Pointer file IS the HLS manifest! Using url: $url")
                    return@use url
                }

                findDirectMediaUrl(body)?.let { return@use it }
                val trimmed = body.trim()
                if (trimmed.startsWith("http") &&
                    (trimmed.contains(".m3u8") || trimmed.contains(".mp4"))
                ) {
                    return@use trimmed
                }
                try {
                    val json = JSONObject(trimmed)
                    listOf("url", "src", "file", "hls", "stream", "playlist", "source", "master")
                        .forEach { k ->
                            val v = json.optString(k).takeIf { it.isNotBlank() }
                            if (v != null && v.startsWith("http") &&
                                (v.contains(".m3u8") || v.contains(".mp4"))
                            ) return@use v
                        }
                } catch (_: Exception) {}

                // If the body contains a relative m3u8 path on a line
                if (trimmed.contains(".m3u8") || trimmed.contains(".mp4")) {
                    val line = trimmed.lines().firstOrNull { it.contains(".m3u8") || it.contains(".mp4") }?.trim().orEmpty()
                    if (line.isNotBlank() && !line.startsWith("#")) {
                        val full = if (line.startsWith("/")) {
                            val baseUri = android.net.Uri.parse(url)
                            "${baseUri.scheme}://${baseUri.host}$line"
                        } else {
                            "${url.substringBeforeLast('/')}/$line"
                        }
                        Log.i(TAG, "Resolved relative pointer URL: $full")
                        return@use full
                    }
                }

                Log.w(TAG, "Pointer body had no URL. Preview: ${body.take(200)}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "resolvePointerUrl error: ${e.message}", e)
            null
        }
    }
}
