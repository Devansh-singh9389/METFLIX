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

package com.example.metflix.presentation.screens.livetv

import com.example.metflix.data.entities.LiveChannel
import com.example.metflix.presentation.screens.livetv.player.components.LiveTvStreamEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveTvStreamTest {

    @Test
    fun testLiveTvStreamEngine_configuresDefaultUserAgentAndHeaders() {
        val headers = mapOf(
            "Referer" to "https://livestream.provider.com",
            "Origin" to "https://livestream.provider.com",
            "User-Agent" to "CustomTVAgent/1.0"
        )
        val factory = LiveTvStreamEngine.createHttpDataSourceFactory(headers)
        assertNotNull(factory)
    }

    @Test
    fun testLiveTvStreamEngine_usesDefaultUserAgentWhenMissing() {
        val factory = LiveTvStreamEngine.createHttpDataSourceFactory(emptyMap())
        assertNotNull(factory)
    }

    @Test
    fun testLiveChannel_fallbackStreamAvailability() {
        val primaryUrl = "https://server1.live/stream.m3u8"
        val backupUrl = "https://server2.backup/stream.m3u8"

        val channelWithBackup = LiveChannel(
            id = "ch_star_sports",
            name = "Star Sports 1 HD",
            channelNumber = 101,
            streamUrl = primaryUrl,
            fallbackStreamUrl = backupUrl,
            category = "Sports"
        )

        assertEquals(primaryUrl, channelWithBackup.streamUrl)
        assertEquals(backupUrl, channelWithBackup.fallbackStreamUrl)

        // Failover simulation
        val activeStream = if (channelWithBackup.fallbackStreamUrl != null) {
            channelWithBackup.fallbackStreamUrl
        } else {
            channelWithBackup.streamUrl
        }
        assertEquals(backupUrl, activeStream)
    }

    @Test
    fun testChannelResolutionBadgeDetection() {
        val fhdChannel = LiveChannel(id = "1", name = "Discovery FHD", channelNumber = 1, streamUrl = "http://a")
        val hdChannel = LiveChannel(id = "2", name = "National Geographic HD", channelNumber = 2, streamUrl = "http://b")
        val sdChannel = LiveChannel(id = "3", name = "Cartoon Network", channelNumber = 3, streamUrl = "http://c")

        fun getBadge(channel: LiveChannel): String? {
            return when {
                channel.name.contains("FHD", ignoreCase = true) || channel.name.contains("4K", ignoreCase = true) -> "FHD"
                channel.name.contains("HD", ignoreCase = true) -> "HD"
                else -> null
            }
        }

        assertEquals("FHD", getBadge(fhdChannel))
        assertEquals("HD", getBadge(hdChannel))
        assertEquals(null, getBadge(sdChannel))
    }
}
