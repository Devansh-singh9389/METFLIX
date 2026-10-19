package com.example.metflix.data.network.torbox

/**
 * Data structures for TorBox API responses and OAuth device authentication flow.
 */

data class TorBoxDeviceAuth(
    val deviceCode: String,
    val userCode: String,
    val interval: Int = 5,
    val expiresIn: Int = 600,
    val verificationUrl: String,
    val verificationUrlComplete: String
)

sealed class TorBoxTokenResult {
    data class Success(val token: String) : TorBoxTokenResult()
    object Pending : TorBoxTokenResult()
    object Expired : TorBoxTokenResult()
    data class Error(val message: String) : TorBoxTokenResult()
}

data class TorBoxUser(
    val id: String,
    val email: String,
    val plan: Int, // 0 = Free, 1 = Essential, 2 = Pro, 3 = Standard
    val planName: String,
    val isPremium: Boolean,
    val daysRemaining: Int,
    val customerId: String = ""
)

data class TorBoxUserStats(
    val totalDownloadedBytes: Long = 0L,
    val totalDownloadedFormatted: String = "",
    val totalEgressBytes: Long = 0L,
    val totalEgressFormatted: String = "",
    val activeSlots: Int = 0
)

data class TorBoxCachedFile(
    val id: Int,
    val name: String,
    val size: Long
)

data class TorBoxCachedItem(
    val id: Int = 0,
    val hash: String,
    val isCached: Boolean,
    val name: String = "",
    val size: Long = 0L,
    val progress: Float = 1.0f,
    val downloadState: String = "cached",
    val files: List<TorBoxCachedFile> = emptyList()
) {
    val formattedSize: String
        get() {
            if (size <= 0) return "0 MB"
            val gb = size.toDouble() / (1024.0 * 1024.0 * 1024.0)
            return if (gb >= 1.0) {
                String.format(java.util.Locale.US, "%.1f GB", gb)
            } else {
                val mb = size.toDouble() / (1024.0 * 1024.0)
                String.format(java.util.Locale.US, "%.1f MB", mb)
            }
        }
}

data class TorBoxStreamResponse(
    val streamUrl: String,
    val isHls: Boolean = false,
    val quality: String = "1080p"
)

sealed class TorBoxDeviceAuthUiState {
    object Idle : TorBoxDeviceAuthUiState()
    object Loading : TorBoxDeviceAuthUiState()
    data class WaitingForApproval(
        val userCode: String,
        val verificationUrl: String,
        val verificationUrlComplete: String,
        val secondsRemaining: Int
    ) : TorBoxDeviceAuthUiState()
    data class Approved(val token: String) : TorBoxDeviceAuthUiState()
    data class Error(val message: String) : TorBoxDeviceAuthUiState()
}
