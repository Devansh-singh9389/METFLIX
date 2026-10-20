package com.example.metflix.data.entities

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import java.util.Calendar

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
        if (schedule.isEmpty()) {
            val slotDurationMs = 60 * 60 * 1000L
            val slotStart = (nowMs / slotDurationMs) * slotDurationMs
            val slotEnd = slotStart + slotDurationMs

            return EpgProgram(
                id = "${id}_current",
                title = currentProgram.ifBlank { "$name Live" },
                description = programDescription.ifBlank { "Streaming live now on $name ($category)" },
                category = category,
                startEpochMs = slotStart,
                endEpochMs = slotEnd
            )
        }

        val live = schedule.firstOrNull { it.isLive(nowMs) }
        if (live != null) return live

        // If schedule has items from a previous day, roll them over to today's clock
        val startOfTodayMs = getStartOfDay(nowMs)
        val rolled = schedule.map { prog ->
            val startMin = getMinuteOfDay(prog.startEpochMs)
            val endMin = getMinuteOfDay(prog.endEpochMs)
            val durationMin = if (endMin > startMin) (endMin - startMin) else 60
            prog.copy(
                startEpochMs = startOfTodayMs + (startMin * 60 * 1000L),
                endEpochMs = startOfTodayMs + ((startMin + durationMin) * 60 * 1000L)
            )
        }
        val rolledLive = rolled.firstOrNull { it.isLive(nowMs) }
        if (rolledLive != null) return rolledLive

        // Clock-aligned slot fallback (e.g., 2:00 PM - 3:00 PM) based on real clock
        val slotDurationMs = 60 * 60 * 1000L
        val slotStart = (nowMs / slotDurationMs) * slotDurationMs
        val slotEnd = slotStart + slotDurationMs

        return EpgProgram(
            id = "${id}_current",
            title = currentProgram.ifBlank { "$name Live" },
            description = programDescription.ifBlank { "Streaming live now on $name ($category)" },
            category = category,
            startEpochMs = slotStart,
            endEpochMs = slotEnd
        )
    }

    fun getNextEpgProgram(nowMs: Long = System.currentTimeMillis()): EpgProgram? {
        val current = getCurrentEpgProgram(nowMs)
        val fromSchedule = schedule.firstOrNull { it.startEpochMs >= current.endEpochMs }
            ?: schedule.filter { it.startEpochMs > nowMs }.minByOrNull { it.startEpochMs }
        if (fromSchedule != null) return fromSchedule

        return EpgProgram(
            id = "${id}_next",
            title = "Next: $name Broadcast",
            description = "Upcoming live programming on $name",
            category = category,
            startEpochMs = current.endEpochMs,
            endEpochMs = current.endEpochMs + (60 * 60 * 1000L)
        )
    }

    fun getUpcomingSchedule(nowMs: Long = System.currentTimeMillis(), count: Int = 4): List<EpgProgram> {
        val current = getCurrentEpgProgram(nowMs)
        val upcoming = schedule.filter { it.startEpochMs >= current.endEpochMs }
            .sortedBy { it.startEpochMs }
            .take(count)
        if (upcoming.isNotEmpty()) return upcoming

        var nextStart = current.endEpochMs
        return (1..count).map { idx ->
            val prog = EpgProgram(
                id = "${id}_upcoming_$idx",
                title = "$name Schedule Slot #$idx",
                description = "Upcoming programming on $name ($category)",
                category = category,
                startEpochMs = nextStart,
                endEpochMs = nextStart + (60 * 60 * 1000L)
            )
            nextStart += (60 * 60 * 1000L)
            prog
        }
    }

    private fun getStartOfDay(nowMs: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = nowMs
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    private fun getMinuteOfDay(epochMs: Long): Int {
        val cal = Calendar.getInstance().apply { timeInMillis = epochMs }
        return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    }
}
