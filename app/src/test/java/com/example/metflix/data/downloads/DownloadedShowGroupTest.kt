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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadedShowGroupTest {

    @Test
    fun testExtractSeriesTitle_parsesFromVariousFormats() {
        val item1 = DownloadedItem(
            id = "solo_1",
            title = "Solo Leveling - Episode 1",
            mediaType = "ANIME_EPISODE",
            mediaId = "solo-leveling_s1_e1",
            seasonNumber = 1,
            episodeNumber = 1,
            localFilePath = ""
        )
        assertEquals("Solo Leveling", extractSeriesTitle(item1))

        val item2 = DownloadedItem(
            id = "naruto_2",
            title = "Naruto Shippuden S2 E15 - The Secret Weapon",
            mediaType = "SERIES",
            mediaId = "naruto-shippuden_s2_e15",
            seasonNumber = 2,
            episodeNumber = 15,
            localFilePath = ""
        )
        assertEquals("Naruto Shippuden", extractSeriesTitle(item2))

        val movieItem = DownloadedItem(
            id = "inception",
            title = "Inception",
            mediaType = "MOVIE",
            mediaId = "inception",
            localFilePath = ""
        )
        assertEquals(null, extractSeriesTitle(movieItem))
    }

    @Test
    fun testGroupDownloadedItems_groupsEpisodesUnderSeriesShow() {
        val items = listOf(
            DownloadedItem(
                id = "demon_1",
                title = "Demon Slayer - Episode 1",
                mediaType = "ANIME_EPISODE",
                mediaId = "demon-slayer_s1_e1",
                seasonNumber = 1,
                episodeNumber = 1,
                localFilePath = ""
            ),
            DownloadedItem(
                id = "demon_2",
                title = "Demon Slayer - Episode 2",
                mediaType = "ANIME_EPISODE",
                mediaId = "demon-slayer_s1_e2",
                seasonNumber = 1,
                episodeNumber = 2,
                localFilePath = ""
            ),
            DownloadedItem(
                id = "oppenheimer",
                title = "Oppenheimer",
                mediaType = "MOVIE",
                mediaId = "oppenheimer",
                localFilePath = ""
            )
        )

        val groups = groupDownloadedItems(items)
        assertEquals(2, groups.size)

        val show = groups.filterIsInstance<DownloadedMediaGroup.SeriesShow>().firstOrNull()
        assertNotNull(show)
        assertEquals("Demon Slayer", show?.seriesTitle)
        assertEquals(2, show?.episodesCount)
        assertEquals(1, show?.seasonsCount)

        val movie = groups.filterIsInstance<DownloadedMediaGroup.StandaloneMovie>().firstOrNull()
        assertNotNull(movie)
        assertEquals("Oppenheimer", movie?.displayTitle)
    }

    @Test
    fun testFormatFileSize_formatsMbAndGb() {
        assertEquals("0 MB", formatFileSize(0L))
        assertEquals("50 MB", formatFileSize(50L * 1024 * 1024))
        assertEquals("1.5 GB", formatFileSize((1.5 * 1024 * 1024 * 1024).toLong()))
    }
}
