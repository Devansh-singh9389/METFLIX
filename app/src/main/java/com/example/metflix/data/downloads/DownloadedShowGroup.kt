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

import org.json.JSONObject
import java.io.File

sealed interface DownloadedMediaGroup {
    val id: String
    val displayTitle: String
    val posterUrl: String
    val totalSizeBytes: Long

    data class StandaloneMovie(
        val item: DownloadedItem
    ) : DownloadedMediaGroup {
        override val id: String get() = item.id
        override val displayTitle: String get() = item.title
        override val posterUrl: String get() = item.posterUrl
        override val totalSizeBytes: Long get() = calculateItemSize(item)
    }

    data class SeriesShow(
        val seriesTitle: String,
        override val posterUrl: String,
        val episodes: List<DownloadedItem>
    ) : DownloadedMediaGroup {
        override val id: String get() = "show_${seriesTitle.lowercase().replace(Regex("""[^a-z0-9]"""), "_")}"
        override val displayTitle: String get() = seriesTitle
        override val totalSizeBytes: Long get() = episodes.sumOf { calculateItemSize(it) }

        val seasonsCount: Int
            get() = episodes.mapNotNull { it.seasonNumber }.distinct().size.coerceAtLeast(1)

        val episodesCount: Int
            get() = episodes.size

        val isAllOptimized: Boolean
            get() = episodes.isNotEmpty() && episodes.all { ep ->
                ep.localFilePath.isNotBlank() &&
                        (ep.localFilePath.endsWith(".mp4", ignoreCase = true) ||
                         ep.localFilePath.endsWith(".mkv", ignoreCase = true)) &&
                        File(ep.localFilePath).exists()
            }
    }
}

fun calculateItemSize(item: DownloadedItem): Long {
    if (item.localFilePath.isNotBlank()) {
        val f = File(item.localFilePath)
        if (f.exists()) return f.length()
    }
    return 0L
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return "0 MB"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format("%.1f GB", gb)
        else -> String.format("%.0f MB", mb)
    }
}

/**
 * Extracts a series title from a DownloadedItem using catalogJson metadata or title parsing.
 */
fun extractSeriesTitle(item: DownloadedItem): String? {
    val isExplicitSeries = item.mediaType.contains("SERIES", ignoreCase = true) ||
            item.mediaType.contains("EPISODE", ignoreCase = true) ||
            item.mediaType.contains("SHOW", ignoreCase = true) ||
            item.seasonNumber != null ||
            item.episodeNumber != null

    val metaObj = runCatching {
        if (item.catalogJson.isNotBlank()) JSONObject(item.catalogJson) else null
    }.getOrNull()

    val jsonSeriesTitle = metaObj?.optString("seriesTitle")?.trim()
    if (!jsonSeriesTitle.isNullOrBlank()) {
        return jsonSeriesTitle
    }

    if (!isExplicitSeries) {
        // Check if title has episode indicators like "Episode 1" or "S1 E2"
        val hasEpisodeText = item.title.contains("Episode", ignoreCase = true) ||
                item.title.contains(Regex("""\bS\d+\s*E\d+\b""", RegexOption.IGNORE_CASE))
        if (!hasEpisodeText) return null
    }

    // Parse series title by stripping " - Episode ...", " - S1E1", etc.
    var clean = item.title
    clean = clean.replace(Regex("""\s*[-–—]\s*(?:Season\s*\d+\s*)?(?:Episode\s*\d+|EP\s*\d+|S\d+\s*E\d+).*$""", RegexOption.IGNORE_CASE), "")
    clean = clean.replace(Regex("""\s+S\d+\s*E\d+.*$""", RegexOption.IGNORE_CASE), "")
    clean = clean.replace(Regex("""\s+Episode\s*\d+.*$""", RegexOption.IGNORE_CASE), "")
    clean = clean.trim()

    return clean.ifBlank { item.title }
}

/**
 * Transforms flat downloaded items into Netflix-style grouped items:
 * - Standalone movies remain StandaloneMovie.
 * - Episodes with a shared series title are grouped into SeriesShow.
 */
fun groupDownloadedItems(items: List<DownloadedItem>): List<DownloadedMediaGroup> {
    val movies = mutableListOf<DownloadedMediaGroup.StandaloneMovie>()
    val showBuckets = LinkedHashMap<String, MutableList<DownloadedItem>>()
    val showPosters = HashMap<String, String>()

    for (item in items) {
        val seriesName = extractSeriesTitle(item)
        if (seriesName != null) {
            val list = showBuckets.getOrPut(seriesName) { mutableListOf() }
            list.add(item)
            if (item.posterUrl.isNotBlank() && !showPosters.containsKey(seriesName)) {
                showPosters[seriesName] = item.posterUrl
            }
        } else {
            movies.add(DownloadedMediaGroup.StandaloneMovie(item))
        }
    }

    val seriesGroups = showBuckets.map { (seriesName, eps) ->
        val sortedEps = eps.sortedWith(
            compareBy<DownloadedItem> { it.seasonNumber ?: 1 }
                .thenBy { it.episodeNumber ?: 1 }
                .thenBy { it.title }
        )
        val poster = showPosters[seriesName] ?: eps.firstOrNull { it.posterUrl.isNotBlank() }?.posterUrl ?: ""
        DownloadedMediaGroup.SeriesShow(
            seriesTitle = seriesName,
            posterUrl = poster,
            episodes = sortedEps
        )
    }

    return (seriesGroups + movies).sortedByDescending { group ->
        when (group) {
            is DownloadedMediaGroup.StandaloneMovie -> group.item.timestamp
            is DownloadedMediaGroup.SeriesShow -> group.episodes.maxOfOrNull { it.timestamp } ?: 0L
        }
    }
}
