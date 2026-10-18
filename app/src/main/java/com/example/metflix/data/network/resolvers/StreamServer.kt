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

import androidx.compose.runtime.Immutable

@Immutable
data class StreamServer(
    val id: String,
    val name: String,
    val description: String,
    val badge: String = "HD",
    val speed: String = "Fast",
    val isPrimary: Boolean = false,
    val latencyMs: Long? = null,
    val isRegionalMatch: Boolean = false,
    val isDirect: Boolean = false,
    val rank: Int = 0,
    val isAvailable: Boolean = true,
    val audioLanguages: List<String> = emptyList()
)

@Immutable
data class SubtitleTrack(
    val label: String,
    val language: String,
    val uri: String
)

@Immutable
data class QualityTrack(
    val quality: String,
    val height: Int,
    val url: String
)

@Immutable
data class ServerRaceResult(
    val rankedServers: List<StreamServer>,
    val bestStream: ResolvedStream?,
    val isFinal: Boolean
)

@Immutable
data class ResolvedStream(
    val server: StreamServer,
    val streamUrl: String,
    val isHls: Boolean = true,
    val headers: Map<String, String> = emptyMap(),
    val subtitles: List<SubtitleTrack> = emptyList(),
    val qualities: List<QualityTrack> = emptyList(),
    val backupUrls: List<String> = emptyList(),
    val isRegional: Boolean = false,
    val latencyMs: Long? = null,
    val audioLanguages: List<String> = emptyList()
) {
    val isDirectStream: Boolean
        get() {
            val lower = streamUrl.lowercase()
            val isKnownEmbed = lower.contains("/embed/") ||
                    lower.contains("vidlink.pro") ||
                    lower.contains("multiembed.mov") ||
                    lower.contains("autoembed") ||
                    lower.contains("2embed")
            if (isKnownEmbed) return false
            return isHls ||
                    lower.contains(".m3u8") ||
                    lower.contains(".mp4") ||
                    lower.contains(".mkv")
        }
}
