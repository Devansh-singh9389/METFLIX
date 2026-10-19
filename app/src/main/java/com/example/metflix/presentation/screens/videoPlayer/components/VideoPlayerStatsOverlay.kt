package com.example.metflix.presentation.screens.videoPlayer.components

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

@Composable
fun VideoPlayerStatsOverlay(
    movieTitle: String,
    videoResolution: String,
    videoBitrate: String,
    audioInfo: String,
    subtitleInfo: String,
    playbackSpeed: String,
    bufferHealthMs: Long,
    currentPositionText: String,
    durationText: String,
    modifier: Modifier = Modifier,
    serverName: String = "",
    streamFormat: String = "",
    audioBadge: String = ""
) {
    Box(
        modifier = modifier
            .padding(24.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.82f))
            .padding(14.dp)
            .widthIn(max = 420.dp)
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

            StatRow("Title", movieTitle)
            if (serverName.isNotBlank()) StatRow("Server", serverName)
            if (streamFormat.isNotBlank()) StatRow("Format", streamFormat)
            if (audioBadge.isNotBlank()) StatRow("Audio Status", audioBadge)
            StatRow("Resolution", videoResolution.ifBlank { "Detecting…" })
            StatRow("Bitrate", videoBitrate.ifBlank { "Adaptive Auto" })
            StatRow("Audio Track", audioInfo.ifBlank { "Standard Stereo" })
            StatRow("Subtitles", subtitleInfo.ifBlank { "Off" })
            StatRow("Buffer Health", "${bufferHealthMs} ms")
            StatRow("Speed", playbackSpeed)
            StatRow("Position", "$currentPositionText / $durationText")
            StatRow("Player", "ExoPlayer (Media3 SurfaceView)")
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
