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

import com.example.metflix.data.network.anime.models.AnimeAudioTrack
import com.example.metflix.data.network.anime.models.AnimePlaybackServer
import com.example.metflix.data.network.anime.models.AnimePlayerEngineType
import com.example.metflix.data.network.anime.models.AnimeQualityOption
import com.example.metflix.data.network.anime.models.AnimeStreamDiagnostics
import com.example.metflix.presentation.screens.anime.player.AnimePlayerViewModel
import com.example.metflix.presentation.screens.anime.player.components.formatBufferHealth
import com.example.metflix.presentation.screens.anime.player.components.formatStreamDataUsage
import com.example.metflix.presentation.screens.anime.player.components.formatStreamSpeed
import com.example.metflix.presentation.screens.anime.player.components.formatTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimePlayerModelsTest {

    @Test
    fun testDefaultAudioTracks() {
        val tracks = AnimePlayerViewModel.DEFAULT_AUDIO_TRACKS
        assertEquals(5, tracks.size)
        val codes = tracks.map { it.code }
        assertTrue(codes.contains("hin"))
        assertTrue(codes.contains("eng"))
        assertTrue(codes.contains("jpn"))
        assertTrue(codes.contains("tam"))
        assertTrue(codes.contains("tel"))
    }

    @Test
    fun testDefaultQualities() {
        val qualities = AnimePlayerViewModel.DEFAULT_QUALITIES
        assertEquals(5, qualities.size)
        assertEquals("Auto", qualities[0].label)
        assertEquals(1080, qualities[1].height)
        assertEquals(720, qualities[2].height)
        assertEquals(480, qualities[3].height)
        assertEquals(360, qualities[4].height)
    }

    @Test
    fun testFormatStreamSpeed() {
        assertEquals("3.5 MB/s", formatStreamSpeed(3_500_000L))
        assertEquals("750 KB/s", formatStreamSpeed(750_000L))
        assertEquals("500 B/s", formatStreamSpeed(500L))
        assertEquals("-- KB/s", formatStreamSpeed(0L))
    }

    @Test
    fun testFormatBufferHealth() {
        assertEquals("12.5s", formatBufferHealth(12_500L))
        assertEquals("800ms", formatBufferHealth(800L))
        assertEquals("0s", formatBufferHealth(0L))
    }

    @Test
    fun testFormatStreamDataUsage() {
        assertEquals("1.50 GB", formatStreamDataUsage((1.5 * 1024 * 1024 * 1024).toLong()))
        assertEquals("25.0 MB", formatStreamDataUsage(25 * 1024 * 1024L))
        assertEquals("512 KB", formatStreamDataUsage(512 * 1024L))
        assertEquals("0 KB", formatStreamDataUsage(0L))
    }

    @Test
    fun testFormatTime() {
        assertEquals("00:00", formatTime(0L))
        assertEquals("02:30", formatTime(150_000L))
        assertEquals("1:15:30", formatTime(4_530_000L))
    }

    @Test
    fun testStreamDiagnosticsDefaultValues() {
        val diag = AnimeStreamDiagnostics()
        assertEquals(0L, diag.downloadSpeedBps)
        assertEquals(0L, diag.bufferHealthMs)
        assertEquals(0L, diag.totalBytesDownloaded)
        assertEquals("", diag.resolution)
        assertEquals("", diag.bitrate)
    }

    @Test
    fun testAnimePlaybackServerProperties() {
        val server1 = AnimePlaybackServer(
            serverNumber = 1,
            title = "Server 1",
            subtitle = "Abyss (Multi-Audio)",
            embedUrl = "https://watchanimeworld.one/dub-player/t/123",
            rawId = "server-abyss"
        )
        val server2 = AnimePlaybackServer(
            serverNumber = 2,
            title = "Server 2",
            subtitle = "Play (Standard)",
            embedUrl = "https://play.zephyrix.org/video/abc",
            rawId = "server-play"
        )

        assertEquals(1, server1.serverNumber)
        assertEquals(2, server2.serverNumber)
        assertTrue(server1.embedUrl.contains("dub-player"))
        assertTrue(server2.embedUrl.contains("zephyrix"))
    }

    @Test
    fun testExtractedStreamAndEngineType() {
        val stream = com.example.metflix.data.network.anime.models.AnimeExtractedStream(
            streamUrl = "https://example.com/playlist.m3u8",
            headers = mapOf("Referer" to "https://watchanimeworld.one/"),
            isDirectStream = true
        )
        assertEquals("https://example.com/playlist.m3u8", stream.streamUrl)
        assertTrue(stream.isDirectStream)
        assertEquals("https://watchanimeworld.one/", stream.headers["Referer"])

        val defaultEngine = AnimePlayerEngineType.EXOPLAYER
        assertEquals(AnimePlayerEngineType.EXOPLAYER, defaultEngine)
    }
}
