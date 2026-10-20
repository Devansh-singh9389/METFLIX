package com.example.metflix.presentation.screens.livetv.player

import com.example.metflix.data.drm.WidevineLevel
import com.example.metflix.data.entities.LiveChannel

data class LiveTvStreamSelection(
    val url: String,
    val useDrm: Boolean,
    val blockedReason: String? = null
)

fun selectLiveTvStream(
    channel: LiveChannel,
    widevineLevel: WidevineLevel
): LiveTvStreamSelection {
    val isWidevine = channel.drmScheme.equals("widevine", ignoreCase = true)
    if (isWidevine && !channel.fallbackStreamUrl.isNullOrBlank()) {
        return LiveTvStreamSelection(
            url = channel.fallbackStreamUrl,
            useDrm = false
        )
    }

    if (isWidevine && widevineLevel == WidevineLevel.L3) {
        return LiveTvStreamSelection(
            url = channel.streamUrl,
            useDrm = false,
            blockedReason = "Widevine is unavailable on this TV. This channel needs an HLS AES-128 fallback."
        )
    }

    return LiveTvStreamSelection(
        url = channel.streamUrl,
        useDrm = isWidevine || !channel.drmScheme.isNullOrBlank()
    )
}
