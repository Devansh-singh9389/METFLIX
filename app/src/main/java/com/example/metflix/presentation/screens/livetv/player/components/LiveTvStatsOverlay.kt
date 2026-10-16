package com.example.metflix.presentation.screens.livetv.player.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.example.metflix.data.entities.LiveChannel

@Composable
fun LiveTvStatsOverlay(
    channel: LiveChannel,
    videoResolution: String,
    videoBitrate: String,
    audioInfo: String,
    bufferHealthMs: Long,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .padding(24.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.82f))
            .padding(14.dp)
            .widthIn(max = 380.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "STREAM DIAGNOSTICS (STATS FOR NERDS)",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 10.sp
                )
            )

            StatRow("Channel", "${channel.channelNumber} - ${channel.name}")
            StatRow("Category", channel.category)
            StatRow("Resolution", videoResolution.ifBlank { "Detecting…" })
            StatRow("Bitrate", videoBitrate.ifBlank { "Adaptive Auto" })
            StatRow("Audio", audioInfo.ifBlank { "Standard Stereo" })
            StatRow("Buffer Health", "${bufferHealthMs} ms")
            StatRow("Protocol", if (channel.streamUrl.contains(".m3u8")) "HLS Live Stream" else "Live Stream")
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "$label:",
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = Color.LightGray,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = value,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = Color.White
        )
    }
}
