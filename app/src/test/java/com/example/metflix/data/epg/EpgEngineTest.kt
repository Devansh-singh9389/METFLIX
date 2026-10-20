package com.example.metflix.data.epg

import com.example.metflix.data.entities.EpgProgram
import com.example.metflix.data.entities.LiveChannel
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class EpgEngineTest {

    private val engine = EpgEngine()

    @Test
    fun channelWithoutVerifiedScheduleDoesNotInventProgram() {
        val channel = channel()

        val enriched = engine.enrichChannelWithSchedule(channel, nowMs = 1_000_000L)

        assertTrue(enriched.schedule.isEmpty())
        assertEquals(LiveChannel.PROGRAM_UNAVAILABLE, enriched.currentProgram)
        assertEquals(
            LiveChannel.PROGRAM_UNAVAILABLE,
            enriched.getCurrentEpgProgram(1_000_000L).title
        )
    }

    @Test
    fun appliesScheduleUsingNormalizedChannelName() {
        val now = System.currentTimeMillis()
        val program = EpgProgram(
            id = "sonic_program",
            title = "Verified Sonic Show",
            startEpochMs = now - 60_000L,
            endEpochMs = now + 60_000L
        )
        val channel = channel().copy(name = "Sonic India", epgId = null)

        val updated = engine.applyRealEpg(
            channels = listOf(channel),
            epgMap = mapOf("sonic.in" to listOf(program)),
            nowMs = now
        ).single()

        assertEquals("Verified Sonic Show", updated.currentProgram)
        assertEquals(program, updated.getCurrentEpgProgram(now))
    }

    private fun channel() = LiveChannel(
        id = "sonic",
        name = "Sonic",
        channelNumber = 1,
        streamUrl = "https://example.com/sonic.m3u8",
        category = "Kids"
    )
}
