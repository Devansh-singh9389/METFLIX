package com.example.metflix.data.entities

import com.example.metflix.data.network.resolvers.ResolvedStream
import com.example.metflix.data.network.resolvers.StreamServer

enum class StreamFormat {
    HLS,
    MKV,
    MP4,
    DASH,
    UNKNOWN
}

/**
 * Normalized candidate stream returned by a StreamProvider.
 * Distinguishes reported languages from cryptographically/manifest-verified audio tracks.
 */
data class StreamCandidate(
    val providerId: String,
    val providerName: String,
    val streamUrl: String,
    val format: StreamFormat = StreamFormat.UNKNOWN,
    val reportedLanguages: List<String> = emptyList(),
    val isAudioVerified: Boolean = false,
    val headers: Map<String, String> = emptyMap(),
    val latencyMs: Long? = null,
    val quality: String = "1080p",
    val backupUrls: List<String> = emptyList(),
    val isDirectStream: Boolean = true
) {
    fun toResolvedStream(server: StreamServer): ResolvedStream {
        val audioLangs = if (reportedLanguages.isNotEmpty()) reportedLanguages else server.audioLanguages
        val badge = when {
            isAudioVerified && audioLangs.any { it.equals("Hindi", ignoreCase = true) } -> "Hindi Verified"
            audioLangs.any { it.equals("Hindi", ignoreCase = true) } -> "Hindi+Eng"
            audioLangs.size > 1 -> "Multi-Audio"
            else -> server.badge
        }

        return ResolvedStream(
            server = server.copy(
                badge = badge,
                audioLanguages = audioLangs,
                latencyMs = latencyMs,
                isAvailable = true,
                isDirect = isDirectStream
            ),
            streamUrl = streamUrl,
            isHls = format == StreamFormat.HLS || streamUrl.contains(".m3u8"),
            headers = headers,
            backupUrls = backupUrls,
            latencyMs = latencyMs,
            audioLanguages = audioLangs,
            isRegional = audioLangs.any { it.equals("Hindi", ignoreCase = true) }
        )
    }
}
