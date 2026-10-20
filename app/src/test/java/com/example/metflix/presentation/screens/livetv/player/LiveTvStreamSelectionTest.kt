package com.example.metflix.presentation.screens.livetv.player

import com.example.metflix.data.drm.WidevineLevel
import com.example.metflix.data.entities.LiveChannel
import org.junit.Test
import kotlin.test.assertEquals

class LiveTvStreamSelectionTest {

    @Test
    fun prefersHlsFallbackForL3Widevine() {
        val channel = LiveChannel(
            id = "disney",
            name = "Disney",
            channelNumber = 1,
            streamUrl = "https://provider.example/disney.mpd",
            fallbackStreamUrl = "https://provider.example/disney.m3u8",
            drmScheme = "widevine",
            drmLicenseUrl = "https://provider.example/license"
        )

        val selection = selectLiveTvStream(channel, WidevineLevel.L3)

        assertEquals("https://provider.example/disney.m3u8", selection.url)
        assertEquals(false, selection.useDrm)
    }

    @Test
    fun blocksL3WidevineWithoutFallback() {
        val channel = LiveChannel(
            id = "disney",
            name = "Disney",
            channelNumber = 1,
            streamUrl = "https://provider.example/disney.mpd",
            drmScheme = "widevine",
            drmLicenseUrl = "https://provider.example/license"
        )

        val selection = selectLiveTvStream(channel, WidevineLevel.L3)

        assertEquals(false, selection.blockedReason.isNullOrBlank())
    }
}
