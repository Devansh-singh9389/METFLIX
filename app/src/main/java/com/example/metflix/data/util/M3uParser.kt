package com.example.metflix.data.util

import com.example.metflix.data.entities.LiveChannel
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.Locale

data class M3uPlaylist(
    val channels: List<LiveChannel>,
    val epgUrl: String? = null
)

object M3uParser {

    private val TVG_ID_REGEX = Regex("""tvg-id="([^"]*)"""", RegexOption.IGNORE_CASE)
    private val TVG_NAME_REGEX = Regex("""tvg-name="([^"]*)"""", RegexOption.IGNORE_CASE)
    private val TVG_LOGO_REGEX = Regex("""tvg-logo="([^"]*)"""", RegexOption.IGNORE_CASE)
    private val GROUP_TITLE_REGEX = Regex("""group-title="([^"]*)"""", RegexOption.IGNORE_CASE)
    private val TVG_COUNTRY_REGEX = Regex("""tvg-country="([^"]*)"""", RegexOption.IGNORE_CASE)
    private val TVG_LANGUAGE_REGEX = Regex("""tvg-language="([^"]*)"""", RegexOption.IGNORE_CASE)
    private val TVG_CHNO_REGEX = Regex("""tvg-chno="([0-9]+)"""", RegexOption.IGNORE_CASE)
    private val DRM_SCHEME_REGEX = Regex("""(?:drm-scheme|drm_scheme|drm-system|drm_system)="([^"]*)"""", RegexOption.IGNORE_CASE)
    private val DRM_LICENSE_REGEX = Regex("""(?:drm-license|drm_license|drm-license-url|drm_license_url|license-url|license_url)="([^"]*)"""", RegexOption.IGNORE_CASE)
    private val URL_TVG_REGEX = Regex("""(?:url-tvg|x-tvg-url)="([^"]*)"""", RegexOption.IGNORE_CASE)

    fun parsePlaylist(inputStream: InputStream, defaultCategory: String = "General", maxChannels: Int = 3500): M3uPlaylist {
        val reader = BufferedReader(InputStreamReader(inputStream))
        return parseReader(reader, defaultCategory, maxChannels)
    }

    fun parsePlaylist(content: String, defaultCategory: String = "General", maxChannels: Int = 3500): M3uPlaylist {
        val reader = BufferedReader(content.reader())
        return parseReader(reader, defaultCategory, maxChannels)
    }

    fun parse(inputStream: InputStream, defaultCategory: String = "General", maxChannels: Int = 3500): List<LiveChannel> {
        return parsePlaylist(inputStream, defaultCategory, maxChannels).channels
    }

    fun parse(content: String, defaultCategory: String = "General", maxChannels: Int = 3500): List<LiveChannel> {
        return parsePlaylist(content, defaultCategory, maxChannels).channels
    }

    private fun parseReader(reader: BufferedReader, defaultCategory: String, maxChannels: Int = 3500): M3uPlaylist {
        val channels = mutableListOf<LiveChannel>()
        var headerEpgUrl: String? = null
        var currentTvgId: String? = null
        var currentTvgName: String? = null
        var currentTvgChno: Int? = null
        var currentLogo: String? = null
        var currentGroup: String? = null
        var currentCountry: String? = null
        var currentLanguage: String? = null
        var currentDrmScheme: String? = null
        var currentDrmLicenseUrl: String? = null
        var currentName: String? = null
        var channelCounter = 1

        reader.useLines { lines ->
            for (rawLine in lines) {
                if (channels.size >= maxChannels) break
                val line = rawLine.trim()
                if (line.isEmpty()) continue

                if (line.startsWith("#EXTM3U", ignoreCase = true)) {
                    if (headerEpgUrl == null) {
                        headerEpgUrl = URL_TVG_REGEX.find(line)?.groupValues?.get(1)?.ifBlank { null }
                    }
                } else if (line.startsWith("#EXTINF:", ignoreCase = true)) {
                    currentTvgId = TVG_ID_REGEX.find(line)?.groupValues?.get(1)?.ifBlank { null }
                    currentTvgName = TVG_NAME_REGEX.find(line)?.groupValues?.get(1)?.ifBlank { null }
                    currentTvgChno = TVG_CHNO_REGEX.find(line)?.groupValues?.get(1)?.toIntOrNull()
                    currentLogo = TVG_LOGO_REGEX.find(line)?.groupValues?.get(1)?.ifBlank { null }
                    currentGroup = GROUP_TITLE_REGEX.find(line)?.groupValues?.get(1)?.ifBlank { null }
                    currentCountry = TVG_COUNTRY_REGEX.find(line)?.groupValues?.get(1)?.ifBlank { null }
                    currentLanguage = TVG_LANGUAGE_REGEX.find(line)?.groupValues?.get(1)?.ifBlank { null }
                    currentDrmScheme = DRM_SCHEME_REGEX.find(line)?.groupValues?.get(1)?.ifBlank { null }
                        ?.lowercase(Locale.ROOT)
                    currentDrmLicenseUrl = DRM_LICENSE_REGEX.find(line)?.groupValues?.get(1)?.ifBlank { null }

                    val commaIndex = line.lastIndexOf(',')
                    currentName = if (commaIndex != -1 && commaIndex + 1 < line.length) {
                        line.substring(commaIndex + 1).trim()
                    } else {
                        currentTvgName ?: currentTvgId ?: "Channel"
                    }
                } else if (line.startsWith("#KODIPROP:", ignoreCase = true)) {
                    val property = line.substringAfter(':', "").trim()
                    val key = property.substringBefore('=', "").trim().lowercase(Locale.ROOT)
                    val value = property.substringAfter('=', "").trim()
                    when {
                        key.endsWith("license_type") -> currentDrmScheme = value
                            .substringBefore('|')
                            .ifBlank { null }
                            ?.lowercase(Locale.ROOT)
                        key.endsWith("license_key") -> currentDrmLicenseUrl = value
                            .substringBefore('|')
                            .ifBlank { null }
                    }
                } else if (!line.startsWith("#")) {
                    val streamUrl = line
                    val name = currentName ?: currentTvgName ?: "Live Channel"

                    // Ignore broken or unsupported streams
                    if (streamUrl.startsWith("http://", ignoreCase = true) || streamUrl.startsWith("https://", ignoreCase = true)) {
                        val drmScheme = currentDrmScheme?.let(::normalizeDrmScheme)
                        val drmLicenseUrl = currentDrmLicenseUrl?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
                        val looksLikeDrm = streamUrl.contains(".mpd", ignoreCase = true) &&
                            (streamUrl.contains("widevine", ignoreCase = true) ||
                                streamUrl.contains("playready", ignoreCase = true) ||
                                streamUrl.contains("license", ignoreCase = true))
                        val hasConfiguredDrm = drmScheme != null && drmLicenseUrl != null

                        // Keep DRM streams only when Media3 has both a supported scheme and a license URL.
                        val hasDrmMetadata = looksLikeDrm || currentDrmScheme != null || currentDrmLicenseUrl != null
                        if (!hasDrmMetadata || hasConfiguredDrm) {
                            val category = normalizeCategory(currentGroup ?: defaultCategory)
                            val id = generateId(currentTvgId, name, streamUrl)
                            val cleanName = cleanChannelName(name)
                            val channelNum = currentTvgChno ?: (channelCounter++)

                            channels.add(
                                LiveChannel(
                                    id = id,
                                    name = cleanName,
                                    channelNumber = channelNum,
                                    streamUrl = streamUrl,
                                    logoUrl = currentLogo,
                                    category = category,
                                    country = currentCountry,
                                    language = currentLanguage,
                                    currentProgram = "Live Broadcast",
                                    programDescription = "Streaming live on $cleanName ($category)",
                                    isFavorite = false,
                                    epgId = currentTvgId,
                                    drmScheme = drmScheme,
                                    drmLicenseUrl = drmLicenseUrl,
                                    requiredSecurityLevel = if (drmScheme != null) "L3" else "NONE",
                                    isFta = drmScheme == null
                                )
                            )
                        }
                    }

                    // Reset state for next channel entry
                    currentTvgId = null
                    currentTvgName = null
                    currentTvgChno = null
                    currentLogo = null
                    currentGroup = null
                    currentCountry = null
                    currentLanguage = null
                    currentDrmScheme = null
                    currentDrmLicenseUrl = null
                    currentName = null
                }

            }
        }

        return M3uPlaylist(channels, headerEpgUrl)
    }

    private fun normalizeDrmScheme(raw: String): String? {
        return when (raw.trim().lowercase(Locale.ROOT)) {
            "widevine", "com.widevine.alpha" -> "widevine"
            "playready", "com.microsoft.playready" -> "playready"
            else -> null
        }
    }

    private fun normalizeCategory(group: String): String {
        val firstGroup = group.split(";", ",", "/").firstOrNull()?.trim() ?: "General"
        return when (firstGroup.lowercase(Locale.ROOT)) {
            "news", "actualités", "noticias" -> "News"
            "sports", "sport", "deportes" -> "Sports"
            "movies", "cinema", "films", "películas" -> "Movies"
            "entertainment", "variedades", "divertissement" -> "Entertainment"
            "music", "musique", "música" -> "Music"
            "animation", "kids", "enfants", "infantil" -> "Kids"
            "documentary", "documentaires", "documentales" -> "Documentary"
            "series", "séries" -> "Entertainment"
            "classic", "classique" -> "Movies"
            else -> {
                if (firstGroup.isBlank() || firstGroup.equals("undefined", ignoreCase = true)) {
                    "General"
                } else {
                    firstGroup.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
                }
            }
        }
    }

    private fun cleanChannelName(rawName: String): String {
        return rawName
            .replace(Regex("""\[.*?\]"""), "")
            .replace(Regex("""\(1080p\)|\(720p\)|\(576p\)|\(480p\)|\(360p\)""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun generateId(tvgId: String?, name: String, streamUrl: String): String {
        val urlHash = Integer.toHexString(streamUrl.hashCode())
        val base = if (!tvgId.isNullOrBlank()) {
            tvgId.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9_]"), "_")
        } else {
            name.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9_]"), "_")
        }
        return "${base}_$urlHash"
    }
}
