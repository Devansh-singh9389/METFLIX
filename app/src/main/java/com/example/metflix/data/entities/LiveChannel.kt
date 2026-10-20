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
    val fallbackStreamUrl: String? = null,
    val logoUrl: String? = null,
    val category: String = "General",
    val country: String? = null,
    val language: String? = null,
    val currentProgram: String = "Live Broadcast",
    val programDescription: String = "",
    val isFavorite: Boolean = false,
    val epgId: String? = null,
    val drmScheme: String? = null,
    val drmLicenseUrl: String? = null,
    val drmLicenseRequestHeaders: Map<String, String> = emptyMap(),
    val requestHeaders: Map<String, String> = emptyMap(),
    val requiredSecurityLevel: String = "NONE", // "NONE", "L3", "L1"
    val videoCodec: String? = null,
    val audioCodec: String? = null,
    val isFta: Boolean = true,
    val schedule: List<EpgProgram> = emptyList()
) {
    fun getCurrentEpgProgram(nowMs: Long = System.currentTimeMillis()): EpgProgram {
        val live = schedule.firstOrNull { it.isLive(nowMs) }
        if (live != null) return live

        return unavailableProgram()
    }

    fun getNextEpgProgram(nowMs: Long = System.currentTimeMillis()): EpgProgram? {
        return schedule
            .filter { it.startEpochMs > nowMs }
            .minByOrNull { it.startEpochMs }
    }

    fun getUpcomingSchedule(nowMs: Long = System.currentTimeMillis(), count: Int = 4): List<EpgProgram> {
        val upcoming = schedule.filter { it.startEpochMs > nowMs }
            .sortedBy { it.startEpochMs }
            .take(count)
        return upcoming
    }

    private fun unavailableProgram(): EpgProgram {
        return EpgProgram(
            id = "${id}_unavailable",
            title = PROGRAM_UNAVAILABLE,
            description = "The channel provider did not provide verified program information.",
            category = category,
            startEpochMs = 0L,
            endEpochMs = 1L
        )
    }

    companion object {
        const val PROGRAM_UNAVAILABLE = "Program information unavailable"
    }
}
