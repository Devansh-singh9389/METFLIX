package com.example.metflix.data.entities

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Immutable
@Serializable
data class EpgProgram(
    val id: String,
    val title: String,
    val description: String = "",
    val category: String = "",
    val startEpochMs: Long,
    val endEpochMs: Long,
    val posterUrl: String? = null
) {
    fun isLive(nowMs: Long = System.currentTimeMillis()): Boolean {
        return nowMs in startEpochMs until endEpochMs
    }

    fun progress(nowMs: Long = System.currentTimeMillis()): Float {
        val total = endEpochMs - startEpochMs
        if (total <= 0L) return 0f
        val elapsed = nowMs - startEpochMs
        return (elapsed.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    }

    fun remainingMinutes(nowMs: Long = System.currentTimeMillis()): Long {
        val diff = endEpochMs - nowMs
        return (diff / 60000L).coerceAtLeast(0L)
    }

    fun elapsedMinutes(nowMs: Long = System.currentTimeMillis()): Long {
        val diff = nowMs - startEpochMs
        return (diff / 60000L).coerceAtLeast(0L)
    }

    val totalDurationMinutes: Long
        get() = ((endEpochMs - startEpochMs) / 60000L).coerceAtLeast(1L)

    val formattedTimeSlot: String
        get() {
            val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
            return "${sdf.format(Date(startEpochMs))} - ${sdf.format(Date(endEpochMs))}"
        }

    val formattedStartTime: String
        get() {
            val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
            return sdf.format(Date(startEpochMs))
        }

    val formattedEndTime: String
        get() {
            val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
            return sdf.format(Date(endEpochMs))
        }

    fun getFormattedRemainingText(nowMs: Long = System.currentTimeMillis()): String {
        val rem = remainingMinutes(nowMs)
        return when {
            rem <= 1L -> "Ending now"
            rem < 60L -> "${rem}m left"
            else -> {
                val hours = rem / 60L
                val mins = rem % 60L
                if (mins == 0L) "${hours}h left" else "${hours}h ${mins}m left"
            }
        }
    }
}
