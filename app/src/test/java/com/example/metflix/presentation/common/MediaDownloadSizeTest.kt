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

package com.example.metflix.presentation.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaDownloadSizeTest {

    @Test
    fun testAnimeEpisode_240p_calculatesRealisticCompactSize() {
        // 24 min episode at 240p should be ~70MB, definitely NOT 190MB
        val sizeMb = calculateEstimatedSizeMb(
            quality = "240p SD",
            audioTrackCount = 1,
            runtimeMinutes = 24,
            mediaType = "ANIME_EPISODE"
        )
        assertTrue("Expected 240p anime episode to be around 65-75 MB, but was $sizeMb MB", sizeMb in 65..75)
    }

    @Test
    fun testLongMovie_240p_calculatesProportionalDurationSize() {
        // 114 min (1.54 hr) movie at 240p should be ~330-345MB
        val sizeMb = calculateEstimatedSizeMb(
            quality = "240p SD",
            audioTrackCount = 1,
            runtimeMinutes = 114,
            mediaType = "MOVIE"
        )
        assertTrue("Expected 1.54hr movie at 240p to be around 325-350 MB, but was $sizeMb MB", sizeMb in 325..350)
    }

    @Test
    fun testAnimeEpisode_1080p_vs_Movie_1080p() {
        val animeSizeMb = calculateEstimatedSizeMb(
            quality = "1080p Full HD",
            audioTrackCount = 1,
            runtimeMinutes = 24,
            mediaType = "ANIME_EPISODE"
        )
        val movieSizeMb = calculateEstimatedSizeMb(
            quality = "1080p Full HD",
            audioTrackCount = 1,
            runtimeMinutes = 114,
            mediaType = "MOVIE"
        )

        assertTrue("Anime 1080p should be ~590 MB, was $animeSizeMb", animeSizeMb in 580..610)
        assertTrue("Movie 1080p should be ~2820 MB, was $movieSizeMb", movieSizeMb in 2750..2900)
    }

    @Test
    fun testDualAudio_increasesSizeAccurately() {
        val singleAudio = calculateEstimatedSizeMb(
            quality = "720p HD",
            audioTrackCount = 1,
            runtimeMinutes = 45,
            mediaType = "TV_EPISODE"
        )
        val dualAudio = calculateEstimatedSizeMb(
            quality = "720p HD",
            audioTrackCount = 2,
            runtimeMinutes = 45,
            mediaType = "TV_EPISODE"
        )

        assertTrue("Dual audio should be larger than single audio", dualAudio > singleAudio)
        assertEquals(34, dualAudio - singleAudio) // 0.75 MB/min * 45 min = 33.75 MB (rounded with base)
    }

    @Test
    fun testFormatEstimatedSize() {
        assertEquals("70 MB", formatEstimatedSize(70))
        assertEquals("594 MB", formatEstimatedSize(594))
        assertEquals("1.00 GB", formatEstimatedSize(1000))
        assertEquals("2.82 GB", formatEstimatedSize(2821))
    }
}
