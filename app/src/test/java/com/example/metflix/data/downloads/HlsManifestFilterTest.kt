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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HlsManifestFilterTest {

    private val sampleMultiAudioManifest = """
#EXTM3U
#EXT-X-VERSION:3
#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="audio",NAME="Japanese",DEFAULT=YES,AUTOSELECT=YES,LANGUAGE="jpn",URI="audio/jpn.m3u8"
#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="audio",NAME="Hindi",DEFAULT=NO,AUTOSELECT=YES,LANGUAGE="hin",URI="audio/hin.m3u8"
#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="audio",NAME="English",DEFAULT=NO,AUTOSELECT=YES,LANGUAGE="eng",URI="audio/eng.m3u8"
#EXT-X-STREAM-INF:BANDWIDTH=5000000,RESOLUTION=1920x1080,AUDIO="audio"
video/1080p.m3u8
#EXT-X-STREAM-INF:BANDWIDTH=2500000,RESOLUTION=1280x720,AUDIO="audio"
video/720p.m3u8
#EXT-X-STREAM-INF:BANDWIDTH=1000000,RESOLUTION=854x480,AUDIO="audio"
video/480p.m3u8
    """.trimIndent()

    private val baseUrl = "https://cdn.example.com/streams/master.m3u8"

    @Test
    fun testOnlyHindiRetainedWhenHindiSelected() {
        val filtered = HlsManifestFilter.filterManifestContent(
            manifestText = sampleMultiAudioManifest,
            baseUrl = baseUrl,
            selectedQuality = "1080p FHD",
            selectedLanguages = listOf("Hindi")
        )

        assertTrue("Should contain Hindi audio", filtered.contains("NAME=\"Hindi\""))
        assertFalse("Should NOT contain Japanese audio", filtered.contains("NAME=\"Japanese\""))
        assertFalse("Should NOT contain English audio", filtered.contains("NAME=\"English\""))

        // Hindi was originally DEFAULT=NO, should now be DEFAULT=YES
        assertTrue("Primary audio must be DEFAULT=YES", filtered.contains("DEFAULT=YES"))
        assertFalse("Primary audio should not be DEFAULT=NO", filtered.contains("DEFAULT=NO"))

        // Audio URI must be resolved to absolute URL
        assertTrue("Audio URI should be absolute", filtered.contains("URI=\"https://cdn.example.com/streams/audio/hin.m3u8\""))

        // Should retain only 1080p video variant
        assertTrue("Should contain 1080p video", filtered.contains("https://cdn.example.com/streams/video/1080p.m3u8"))
        assertFalse("Should NOT contain 720p video", filtered.contains("video/720p.m3u8"))
        assertFalse("Should NOT contain 480p video", filtered.contains("video/480p.m3u8"))
    }

    @Test
    fun testOnlyEnglishRetainedWhenEnglishSelected() {
        val filtered = HlsManifestFilter.filterManifestContent(
            manifestText = sampleMultiAudioManifest,
            baseUrl = baseUrl,
            selectedQuality = "720p HD",
            selectedLanguages = listOf("English")
        )

        assertTrue("Should contain English audio", filtered.contains("NAME=\"English\""))
        assertFalse("Should NOT contain Japanese audio", filtered.contains("NAME=\"Japanese\""))
        assertFalse("Should NOT contain Hindi audio", filtered.contains("NAME=\"Hindi\""))

        // English was originally DEFAULT=NO, should now be DEFAULT=YES
        assertTrue("Primary audio must be DEFAULT=YES", filtered.contains("DEFAULT=YES"))

        // Should retain only 720p video variant
        assertTrue("Should contain 720p video", filtered.contains("https://cdn.example.com/streams/video/720p.m3u8"))
        assertFalse("Should NOT contain 1080p video", filtered.contains("video/1080p.m3u8"))
    }

    @Test
    fun testBothHindiAndEnglishRetainedWhenBothSelected() {
        val filtered = HlsManifestFilter.filterManifestContent(
            manifestText = sampleMultiAudioManifest,
            baseUrl = baseUrl,
            selectedQuality = "1080p",
            selectedLanguages = listOf("Hindi", "English")
        )

        assertTrue("Should contain Hindi audio", filtered.contains("NAME=\"Hindi\""))
        assertTrue("Should contain English audio", filtered.contains("NAME=\"English\""))
        assertFalse("Should NOT contain Japanese audio", filtered.contains("NAME=\"Japanese\""))

        // Count audio lines
        val audioLines = filtered.lines().filter { it.startsWith("#EXT-X-MEDIA:TYPE=AUDIO") }
        assertEquals(2, audioLines.size)
        assertTrue("First track should be DEFAULT=YES", audioLines[0].contains("DEFAULT=YES"))
        assertTrue("Second track should be DEFAULT=NO", audioLines[1].contains("DEFAULT=NO"))
    }

    @Test
    fun testFallbackWhenNoAudioMatchesSelected() {
        val filtered = HlsManifestFilter.filterManifestContent(
            manifestText = sampleMultiAudioManifest,
            baseUrl = baseUrl,
            selectedQuality = "1080p",
            selectedLanguages = listOf("German")
        )

        // All original audio lines should be preserved as safe fallback
        val audioLines = filtered.lines().filter { it.startsWith("#EXT-X-MEDIA:TYPE=AUDIO") }
        assertEquals(3, audioLines.size)
    }

    @Test
    fun testLanguageMatchingFlexibility() {
        val hindiLine = """#EXT-X-MEDIA:TYPE=AUDIO,NAME="Hindi (Original)",LANGUAGE="hin""""
        val engLine = """#EXT-X-MEDIA:TYPE=AUDIO,NAME="English [Stereo]",LANGUAGE="en""""
        val japLine = """#EXT-X-MEDIA:TYPE=AUDIO,NAME="Japanese",LANGUAGE="jpn""""

        assertTrue(HlsManifestFilter.matchesSelectedLanguage(hindiLine, listOf("Hindi")))
        assertTrue(HlsManifestFilter.matchesSelectedLanguage(hindiLine, listOf("hin")))
        assertTrue(HlsManifestFilter.matchesSelectedLanguage(engLine, listOf("English")))
        assertTrue(HlsManifestFilter.matchesSelectedLanguage(engLine, listOf("eng")))
        assertTrue(HlsManifestFilter.matchesSelectedLanguage(japLine, listOf("Japanese")))
        assertFalse(HlsManifestFilter.matchesSelectedLanguage(japLine, listOf("Hindi")))
    }

    @Test
    fun testToAbsoluteUrl() {
        val base = "https://cdn.example.com/path/master.m3u8"
        assertEquals("https://cdn.example.com/path/sub/file.m3u8", HlsManifestFilter.toAbsoluteUrl("sub/file.m3u8", base))
        assertEquals("https://cdn.example.com/root/file.m3u8", HlsManifestFilter.toAbsoluteUrl("/root/file.m3u8", base))
        assertEquals("https://other.com/file.m3u8", HlsManifestFilter.toAbsoluteUrl("https://other.com/file.m3u8", base))
    }
}
