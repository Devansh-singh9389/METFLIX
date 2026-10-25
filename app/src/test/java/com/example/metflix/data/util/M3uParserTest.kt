package com.example.metflix.data.util

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class M3uParserTest {

    @Test
    fun parsesStandardHeadersAndHlsFallback() {
        val playlist = """
            #EXTM3U
            #EXTINF:-1 tvg-name="Disney" fallback-url="https://provider.example/disney.m3u8",Disney
            #EXTVLCOPT:http-user-agent=ProviderPlayer/1.0
            #EXTVLCOPT:http-referrer=https://provider.example/
            #EXTHTTP:{"Origin":"https://provider.example"}
            https://provider.example/disney.mpd|User-Agent=InlineOverride
        """.trimIndent()

        val channel = M3uParser.parsePlaylist(playlist).channels.single()

        assertEquals("https://provider.example/disney.m3u8", channel.fallbackStreamUrl)
        assertEquals("InlineOverride", channel.requestHeaders["User-Agent"])
        assertEquals("https://provider.example/", channel.requestHeaders["Referer"])
        assertEquals("https://provider.example", channel.requestHeaders["Origin"])
    }

    @Test
    fun doesNotLeakHeadersToNextChannel() {
        val playlist = """
            #EXTM3U
            #EXTINF:-1,First
            #EXTVLCOPT:http-user-agent=FirstAgent
            https://provider.example/first.m3u8
            #EXTINF:-1,Second
            https://provider.example/second.m3u8
        """.trimIndent()

        val channels = M3uParser.parsePlaylist(playlist).channels

        assertEquals("FirstAgent", channels[0].requestHeaders["User-Agent"])
        assertNull(channels[1].requestHeaders["User-Agent"])
    }
}
