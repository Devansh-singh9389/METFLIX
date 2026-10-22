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

package com.example.metflix.data.network.anime.models

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
data class AnimePlaybackServer(
    val serverNumber: Int, // Exactly 1 or 2
    val title: String,     // "Server 1", "Server 2"
    val subtitle: String,  // "Play (Fast)", "Abyss (Multi-Audio)"
    val embedUrl: String,
    val rawId: String = "",
    val directStreamUrl: String? = null,
    val directStreamType: String? = null,
    val decodedStreams: List<AnimeDecodedStream>? = null
)

@Immutable
data class AnimeAudioTrack(
    val code: String,      // "hin", "eng", "jpn", "tam", "tel"
    val displayName: String, // "Hindi", "English", "Japanese", "Tamil", "Telugu"
    val badge: String = code.uppercase()
)

@Immutable
data class AnimeQualityOption(
    val label: String,     // "Auto", "1080p", "720p", "480p", "360p"
    val height: Int? = null
)

@Immutable
data class AnimeStreamDiagnostics(
    val downloadSpeedBps: Long = 0L,
    val bufferHealthMs: Long = 0L,
    val totalBytesDownloaded: Long = 0L,
    val resolution: String = "",
    val bitrate: String = ""
)

enum class AnimePlayerEngineType {
    EXOPLAYER,
    WEBVIEW
}

@Immutable
data class AnimeExtractedStream(
    val streamUrl: String,
    val headers: Map<String, String> = emptyMap(),
    val isDirectStream: Boolean = true
)
