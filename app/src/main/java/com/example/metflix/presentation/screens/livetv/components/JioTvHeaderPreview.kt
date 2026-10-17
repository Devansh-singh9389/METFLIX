package com.example.metflix.presentation.screens.livetv.components

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.OutlinedButtonDefaults
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import com.example.metflix.data.entities.LiveChannel
import kotlinx.coroutines.delay

@Composable
fun JioTvHeaderPreview(
    channel: LiveChannel?,
    onWatchClick: (LiveChannel) -> Unit,
    onToggleFavorite: (LiveChannel) -> Unit,
    modifier: Modifier = Modifier
) {
    if (channel == null) {
        Spacer(modifier = modifier.height(200.dp))
        return
    }

    var currentTimeMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000)
            currentTimeMs = System.currentTimeMillis()
        }
    }

    val currentProg = remember(channel, currentTimeMs) {
        channel.getCurrentEpgProgram(currentTimeMs)
    }
    val upcomingProgs = remember(channel, currentTimeMs) {
        channel.getUpcomingSchedule(currentTimeMs, count = 3)
    }
    val progress = currentProg.progress(currentTimeMs)
    val elapsed = currentProg.elapsedMinutes(currentTimeMs)
    val remaining = currentProg.remainingMinutes(currentTimeMs)
    val total = currentProg.totalDurationMinutes

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFF1E2126),
                        Color(0xFF141619)
                    )
                )
            )
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left main info column
            Column(
                modifier = Modifier
                    .weight(1.3f)
                    .padding(end = 24.dp)
            ) {
                // Channel Badges Row
                Row(verticalAlignment = Alignment.CenterVertically) {
                    JioTvLiveBadge()

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = "CH ${channel.channelNumber}",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )

                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "•", color = Color.Gray)
                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = channel.name,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )

                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "•", color = Color.Gray)
                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = channel.category.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )

                    if (!channel.language.isNullOrBlank()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(3.dp))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = channel.language.uppercase(),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    color = Color.LightGray
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Current Program Headline Title
                Text(
                    text = currentProg.title,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp
                    ),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Time slot & Live Progress Details
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = currentProg.formattedTimeSlot,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                        color = Color.LightGray
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "•   ${elapsed}m played   /   ${remaining}m left (${(progress * 100).toInt()}%)",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFFF7043)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // JioTV Red Progress Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.15f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction = progress.coerceIn(0.02f, 1f))
                            .height(4.dp)
                            .background(Color(0xFFE50914))
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Program Description
                Text(
                    text = currentProg.description.ifBlank { channel.programDescription },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Watch Live & Favorite Buttons
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = { onWatchClick(channel) },
                        colors = ButtonDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Watch Live",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    OutlinedButton(
                        onClick = { onToggleFavorite(channel) },
                        colors = OutlinedButtonDefaults.colors(
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Icon(
                            imageVector = if (channel.isFavorite) Icons.Default.Star else Icons.Outlined.StarBorder,
                            contentDescription = null,
                            tint = if (channel.isFavorite) Color(0xFFFFD700) else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (channel.isFavorite) "Favorited" else "Favorite",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }

            // Right side: Upcoming shows timeline preview
            Column(
                modifier = Modifier
                    .weight(0.9f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "UPCOMING SCHEDULE",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 11.sp,
                            letterSpacing = 0.5.sp
                        )
                    )

                    if (!channel.logoUrl.isNullOrBlank()) {
                        val context = LocalContext.current
                        val imageRequest = remember(channel.logoUrl) {
                            ImageRequest.Builder(context)
                                .data(channel.logoUrl)
                                .size(90, 50)
                                .precision(Precision.INEXACT)
                                .bitmapConfig(Bitmap.Config.RGB_565)
                                .allowRgb565(true)
                                .crossfade(false)
                                .build()
                        }
                        AsyncImage(
                            model = imageRequest,
                            contentDescription = channel.name,
                            modifier = Modifier.size(44.dp, 24.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (upcomingProgs.isNotEmpty()) {
                    upcomingProgs.forEachIndexed { index, prog ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Start time pill
                            Box(
                                modifier = Modifier
                                    .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = prog.formattedStartTime,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 10.sp,
                                        color = Color.LightGray
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = prog.title,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${prog.totalDurationMinutes} mins",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = Color.Gray
                                )
                            }
                        }
                        if (index < upcomingProgs.lastIndex) {
                            Spacer(modifier = Modifier.height(2.dp))
                        }
                    }
                } else {
                    Text(
                        text = "24-hour continuous live broadcasting",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}
