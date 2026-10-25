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

package com.example.metflix.data.downloads

import android.content.Context
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.URI
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object HlsManifestFilter {
    private const val TAG = "HlsManifestFilter"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    /**
     * Filters an HLS master manifest for offline download.
     * Retains ONLY the audio tracks matching [selectedLanguages] and marks the primary one as DEFAULT=YES.
     * Retains ONLY the video variant matching [selectedQuality] (if multiple exist).
     * Converts any relative URIs to absolute URIs based on [rawUrl].
     * Writes the filtered manifest to a dedicated file in context.filesDir/download_manifests/
     * and returns the file URI string.
     */
    fun filterHlsManifest(
        context: Context,
        rawUrl: String,
        selectedQuality: String,
        selectedLanguages: List<String>,
        mediaId: String
    ): String {
        return try {
            val isLocalFile = rawUrl.startsWith("file:", ignoreCase = true) ||
                    (!rawUrl.startsWith("http://", ignoreCase = true) && !rawUrl.startsWith("https://", ignoreCase = true) && File(rawUrl).exists())

            val rawText: String
            val baseUrl: String

            if (isLocalFile) {
                val cleanPath = when {
                    rawUrl.startsWith("file://", ignoreCase = true) -> rawUrl.removePrefix("file://")
                    rawUrl.startsWith("file:/", ignoreCase = true) -> rawUrl.removePrefix("file:")
                    else -> rawUrl
                }
                val localFile = File(cleanPath)
                if (!localFile.exists()) {
                    Log.w(TAG, "Local manifest file not found at: $cleanPath")
                    return rawUrl
                }
                rawText = localFile.readText()
                baseUrl = rawUrl
            } else {
                val req = Request.Builder()
                    .url(rawUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .header("Accept", "*/*")
                    .build()
                val resp = httpClient.newCall(req).execute()
                if (!resp.isSuccessful) {
                    Log.w(TAG, "Failed to download remote manifest ($rawUrl): HTTP ${resp.code}")
                    return rawUrl
                }
                rawText = resp.body?.string().orEmpty()
                baseUrl = rawUrl
            }

            if (!rawText.contains("#EXTM3U")) {
                Log.d(TAG, "URL does not point to an HLS master manifest (#EXTM3U missing): $rawUrl")
                return rawUrl
            }

            val filteredContent = filterManifestContent(
                manifestText = rawText,
                baseUrl = baseUrl,
                selectedQuality = selectedQuality,
                selectedLanguages = selectedLanguages
            )

            val manifestDir = File(context.filesDir, "download_manifests").apply { mkdirs() }
            val cleanMediaId = mediaId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val targetFile = File(manifestDir, "dl_${cleanMediaId}_${System.currentTimeMillis()}.m3u8")
            targetFile.writeText(filteredContent)

            Log.i(TAG, "Created dedicated download manifest: ${targetFile.absolutePath} for mediaId: $mediaId, languages: $selectedLanguages")
            targetFile.toURI().toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error filtering HLS manifest: ${e.message}", e)
            rawUrl
        }
    }

    /**
     * Pure function to filter HLS manifest text based on language and quality.
     * Accessible for unit testing.
     */
    fun filterManifestContent(
        manifestText: String,
        baseUrl: String,
        selectedQuality: String = "1080p",
        selectedLanguages: List<String> = emptyList()
    ): String {
        val lines = manifestText.lines()
        val audioLines = mutableListOf<String>()

        // 1. Collect all audio lines and resolve their relative URIs to absolute URIs
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("#EXT-X-MEDIA:TYPE=AUDIO", ignoreCase = true)) {
                var resolvedLine = trimmed
                val uriMatcher = Pattern.compile("""URI="([^"]+)"""").matcher(trimmed)
                if (uriMatcher.find()) {
                    val origUri = uriMatcher.group(1).orEmpty()
                    val absUri = toAbsoluteUrl(origUri, baseUrl)
                    resolvedLine = resolvedLine.replace("URI=\"$origUri\"", "URI=\"$absUri\"")
                }
                audioLines.add(resolvedLine)
            }
        }

        // 2. Filter audio lines matching selectedLanguages
        val matchedAudioLines = if (selectedLanguages.isNotEmpty() && audioLines.isNotEmpty()) {
            audioLines.filter { matchesSelectedLanguage(it, selectedLanguages) }
        } else {
            audioLines
        }

        // Determine final audio lines: if matches found, use them; otherwise fallback to original audioLines
        val finalAudioLines = if (matchedAudioLines.isNotEmpty()) {
            matchedAudioLines.mapIndexed { index, audioLine ->
                var updated = audioLine
                if (index == 0) {
                    // Primary audio track: ensure DEFAULT=YES and AUTOSELECT=YES
                    updated = if (updated.contains("DEFAULT=NO", ignoreCase = true)) {
                        updated.replace("DEFAULT=NO", "DEFAULT=YES", ignoreCase = true)
                    } else if (!updated.contains("DEFAULT=", ignoreCase = true)) {
                        "$updated,DEFAULT=YES"
                    } else {
                        updated
                    }
                    if (updated.contains("AUTOSELECT=NO", ignoreCase = true)) {
                        updated = updated.replace("AUTOSELECT=NO", "AUTOSELECT=YES", ignoreCase = true)
                    } else if (!updated.contains("AUTOSELECT=", ignoreCase = true)) {
                        updated = "$updated,AUTOSELECT=YES"
                    }
                } else {
                    // Secondary audio tracks: ensure DEFAULT=NO
                    updated = if (updated.contains("DEFAULT=YES", ignoreCase = true)) {
                        updated.replace("DEFAULT=YES", "DEFAULT=NO", ignoreCase = true)
                    } else if (!updated.contains("DEFAULT=", ignoreCase = true)) {
                        "$updated,DEFAULT=NO"
                    } else {
                        updated
                    }
                }
                updated
            }
        } else {
            audioLines
        }

        // 3. Parse and filter video variants (#EXT-X-STREAM-INF)
        data class Variant(val streamInfTag: String, val streamUri: String, val resolution: Int)
        val variants = mutableListOf<Variant>()

        var currentStreamInf: String? = null
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("#EXT-X-STREAM-INF:", ignoreCase = true)) {
                currentStreamInf = trimmed
            } else if (currentStreamInf != null && trimmed.isNotBlank() && !trimmed.startsWith("#")) {
                val resMatch = Regex("""RESOLUTION=(\d+x(\d+))""").find(currentStreamInf)
                val height = resMatch?.groupValues?.get(2)?.toIntOrNull() ?: 1080
                variants.add(Variant(currentStreamInf, toAbsoluteUrl(trimmed, baseUrl), height))
                currentStreamInf = null
            }
        }

        // Pick best matching variant for selectedQuality
        val chosenVariant: Variant? = if (variants.isNotEmpty()) {
            val targetRes = when {
                selectedQuality.contains("1080", ignoreCase = true) -> 1080
                selectedQuality.contains("720", ignoreCase = true) -> 720
                selectedQuality.contains("480", ignoreCase = true) -> 480
                selectedQuality.contains("360", ignoreCase = true) -> 360
                selectedQuality.contains("240", ignoreCase = true) -> 240
                else -> 1080
            }
            variants.minByOrNull { kotlin.math.abs(it.resolution - targetRes) } ?: variants.first()
        } else {
            null
        }

        // 4. Reconstruct manifest
        val result = StringBuilder()
        result.appendLine("#EXTM3U")
        result.appendLine("#EXT-X-VERSION:3")

        // Append kept audio lines
        for (aLine in finalAudioLines) {
            result.appendLine(aLine)
        }

        // Append subtitles (if any, with resolved URIs)
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("#EXT-X-MEDIA:TYPE=SUBTITLES", ignoreCase = true)) {
                var subLine = trimmed
                val uriMatcher = Pattern.compile("""URI="([^"]+)"""").matcher(trimmed)
                if (uriMatcher.find()) {
                    val origUri = uriMatcher.group(1).orEmpty()
                    val absUri = toAbsoluteUrl(origUri, baseUrl)
                    subLine = subLine.replace("URI=\"$origUri\"", "URI=\"$absUri\"")
                }
                result.appendLine(subLine)
            }
        }

        // Append video variant(s)
        if (chosenVariant != null) {
            result.appendLine(chosenVariant.streamInfTag)
            result.appendLine(chosenVariant.streamUri)
        } else {
            // No #EXT-X-STREAM-INF found, or already a media playlist
            for (line in lines) {
                val trimmed = line.trim()
                if (!trimmed.startsWith("#EXTM3U") &&
                    !trimmed.startsWith("#EXT-X-VERSION") &&
                    !trimmed.startsWith("#EXT-X-MEDIA")
                ) {
                    if (trimmed.isNotBlank() && !trimmed.startsWith("#")) {
                        result.appendLine(toAbsoluteUrl(trimmed, baseUrl))
                    } else if (trimmed.isNotBlank()) {
                        result.appendLine(trimmed)
                    }
                }
            }
        }

        return result.toString()
    }

    /**
     * Checks if an #EXT-X-MEDIA:TYPE=AUDIO line matches any of [selectedLanguages].
     */
    fun matchesSelectedLanguage(audioLine: String, selectedLanguages: List<String>): Boolean {
        if (selectedLanguages.isEmpty()) return true

        val name = Regex("""NAME="([^"]+)"""", RegexOption.IGNORE_CASE).find(audioLine)?.groupValues?.get(1)?.trim().orEmpty()
        val lang = Regex("""LANGUAGE="([^"]+)"""", RegexOption.IGNORE_CASE).find(audioLine)?.groupValues?.get(1)?.trim().orEmpty()

        return selectedLanguages.any { selected ->
            val sel = selected.trim()
            if (sel.isBlank()) return@any false

            // 1. Exact equality (case-insensitive)
            name.equals(sel, ignoreCase = true) || lang.equals(sel, ignoreCase = true) ||
            // 2. Contains match (e.g. "Hindi (Original)" contains "Hindi")
            name.contains(sel, ignoreCase = true) || sel.contains(name, ignoreCase = true) ||
            // 3. Known ISO 639 codes & common aliases
            when (sel.lowercase()) {
                "hindi" -> lang.equals("hin", ignoreCase = true) || lang.equals("hi", ignoreCase = true) || name.contains("hin", ignoreCase = true)
                "english" -> lang.equals("eng", ignoreCase = true) || lang.equals("en", ignoreCase = true) || name.contains("eng", ignoreCase = true)
                "japanese" -> lang.equals("jpn", ignoreCase = true) || lang.equals("ja", ignoreCase = true) || name.contains("jap", ignoreCase = true)
                "tamil" -> lang.equals("tam", ignoreCase = true) || lang.equals("ta", ignoreCase = true)
                "telugu" -> lang.equals("tel", ignoreCase = true) || lang.equals("te", ignoreCase = true)
                "korean" -> lang.equals("kor", ignoreCase = true) || lang.equals("ko", ignoreCase = true)
                "spanish" -> lang.equals("spa", ignoreCase = true) || lang.equals("es", ignoreCase = true)
                else -> false
            }
        }
    }

    /**
     * Resolves a relative URI against [baseUrl] to produce an absolute URL.
     */
    fun toAbsoluteUrl(uri: String, baseUrl: String): String {
        val clean = uri.trim()
        if (clean.startsWith("http://", ignoreCase = true) || clean.startsWith("https://", ignoreCase = true)) {
            return clean
        }
        return try {
            if (clean.startsWith("/")) {
                val parsed = URI(baseUrl)
                val portStr = if (parsed.port != -1 && parsed.port != 80 && parsed.port != 443) ":${parsed.port}" else ""
                "${parsed.scheme}://${parsed.host}$portStr$clean"
            } else {
                val base = baseUrl.substringBeforeLast("/")
                "$base/$clean"
            }
        } catch (_: Exception) {
            clean
        }
    }
}
