package com.example.metflix.data.entities

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class LiveChannel(
    val id: String,
    val name: String,
    val channelNumber: Int,
    val streamUrl: String,
    val logoUrl: String? = null,
    val category: String = "General",
    val country: String? = null,
    val language: String? = null,
    val currentProgram: String = "Live Broadcast",
    val programDescription: String = "",
    val isFavorite: Boolean = false,
    val epgId: String? = null,
    val schedule: List<EpgProgram> = emptyList()
) {
    fun getCurrentEpgProgram(nowMs: Long = System.currentTimeMillis()): EpgProgram {
        return schedule.firstOrNull { it.isLive(nowMs) }
            ?: EpgProgram(
                id = "${id}_current",
                title = currentProgram.ifBlank { "Live Broadcast" },
                description = programDescription.ifBlank { "Streaming live now on $name ($category)" },
                category = category,
                startEpochMs = nowMs - (25 * 60 * 1000L),
                endEpochMs = nowMs + (35 * 60 * 1000L)
            )
    }

    fun getNextEpgProgram(nowMs: Long = System.currentTimeMillis()): EpgProgram? {
        val current = getCurrentEpgProgram(nowMs)
        return schedule.firstOrNull { it.startEpochMs >= current.endEpochMs }
            ?: schedule.filter { it.startEpochMs > nowMs }.minByOrNull { it.startEpochMs }
    }

    fun getUpcomingSchedule(nowMs: Long = System.currentTimeMillis(), count: Int = 4): List<EpgProgram> {
        val current = getCurrentEpgProgram(nowMs)
        return schedule.filter { it.startEpochMs >= current.endEpochMs }
            .sortedBy { it.startEpochMs }
            .take(count)
    }
}
