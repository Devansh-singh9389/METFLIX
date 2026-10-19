package com.example.metflix.presentation.screens.livetv.components

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import com.example.metflix.data.entities.EpgProgram
import com.example.metflix.data.entities.LiveChannel
import com.example.metflix.presentation.theme.MetFlixCardShape
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun JioTvGuideTimelineView(
    channels: List<LiveChannel>,
    onChannelClick: (LiveChannel) -> Unit,
    onChannelFocus: (LiveChannel) -> Unit,
    modifier: Modifier = Modifier
) {
    var currentTimeMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            currentTimeMs = System.currentTimeMillis()
        }
    }

    val timeSlots = remember(currentTimeMs) {
        generateTimeHeaders(currentTimeMs)
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Timeline Header: Time columns
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF16181B))
                .padding(vertical = 8.dp, horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Channel Column Label
            Box(
                modifier = Modifier.width(200.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = "CHANNELS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    )
                )
            }

            // Time intervals
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                items(timeSlots) { slot ->
                    Box(
                        modifier = Modifier
                            .width(220.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (slot.isNow) Color(0xFFE50914).copy(alpha = 0.25f)
                                else Color.White.copy(alpha = 0.06f)
                            )
                            .padding(vertical = 4.dp, horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = slot.label,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (slot.isNow) FontWeight.Bold else FontWeight.Medium,
                                color = if (slot.isNow) Color(0xFFFF5252) else Color.LightGray
                            )
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Vertical List of Channel Rows
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            items(channels, key = { it.id }) { channel ->
                JioTvChannelTimelineRow(
                    channel = channel,
                    currentTimeMs = currentTimeMs,
                    onChannelClick = { onChannelClick(channel) },
                    onChannelFocus = { onChannelFocus(channel) }
                )
            }
        }
    }
}

@Composable
private fun JioTvChannelTimelineRow(
    channel: LiveChannel,
    currentTimeMs: Long,
    onChannelClick: () -> Unit,
    onChannelFocus: () -> Unit
) {
    val currentProg = remember(channel, currentTimeMs) {
        channel.getCurrentEpgProgram(currentTimeMs)
    }
    val upcoming = remember(channel, currentTimeMs) {
        channel.getUpcomingSchedule(currentTimeMs, count = 4)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(82.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Channel ID / Header Card
        Card(
            onClick = onChannelClick,
            modifier = Modifier
                .width(200.dp)
                .height(82.dp)
                .onFocusChanged {
                    if (it.isFocused) onChannelFocus()
                },
            shape = CardDefaults.shape(MetFlixCardShape),
            border = CardDefaults.border(
                focusedBorder = Border(
                    border = BorderStroke(2.5.dp, Color.White),
                    shape = MetFlixCardShape
                )
            ),
            scale = CardDefaults.scale(focusedScale = 1.0f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF202328))
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Channel Logo
                Box(
                    modifier = Modifier
                        .size(46.dp, 36.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (!channel.logoUrl.isNullOrBlank()) {
                        val context = LocalContext.current
                        val imageRequest = remember(channel.logoUrl) {
                            ImageRequest.Builder(context)
                                .data(channel.logoUrl)
                                .size(92, 72)
                                .precision(Precision.INEXACT)
                                .bitmapConfig(Bitmap.Config.RGB_565)
                                .allowRgb565(true)
                                .crossfade(false)
                                .build()
                        }
                        AsyncImage(
                            model = imageRequest,
                            contentDescription = channel.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Tv,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "CH ${channel.channelNumber}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 10.sp
                        )
                    )
                    Text(
                        text = channel.name,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        ),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Horizontal Program Blocks
        LazyRow(
            modifier = Modifier
                .weight(1f)
                .height(82.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. Current Live Show Block
            item {
                JioTvProgramBlock(
                    program = currentProg,
                    currentTimeMs = currentTimeMs,
                    isLive = true,
                    onClick = onChannelClick
                )
            }

            // 2. Upcoming Program Blocks
            items(upcoming) { prog ->
                JioTvProgramBlock(
                    program = prog,
                    currentTimeMs = currentTimeMs,
                    isLive = false,
                    onClick = onChannelClick
                )
            }
        }
    }
}

@Composable
private fun JioTvProgramBlock(
    program: EpgProgram,
    currentTimeMs: Long,
    isLive: Boolean,
    onClick: () -> Unit
) {
    val progress = program.progress(currentTimeMs)
    val remaining = program.remainingMinutes(currentTimeMs)

    Card(
        onClick = onClick,
        modifier = Modifier
            .width(220.dp)
            .height(82.dp),
        shape = CardDefaults.shape(RoundedCornerShape(6.dp)),
        border = CardDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(2.5.dp, if (isLive) Color(0xFFE50914) else Color.White),
                shape = RoundedCornerShape(6.dp)
            )
        ),
        scale = CardDefaults.scale(focusedScale = 1.0f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (isLive) Color(0xFF281F22)
                    else Color(0xFF1D2024)
                )
                .padding(8.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Program Title and LIVE indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = program.title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (isLive) FontWeight.Bold else FontWeight.Medium,
                        color = Color.White,
                        fontSize = 13.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (isLive) {
                    Spacer(modifier = Modifier.width(6.dp))
                    JioTvLiveBadge()
                }
            }

            // Timing Info
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = program.formattedTimeSlot,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = Color.LightGray
                )

                if (isLive) {
                    Text(
                        text = "${remaining}m left",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFF5252),
                            fontSize = 10.sp
                        )
                    )
                } else {
                    Text(
                        text = "${program.totalDurationMinutes}m",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = Color.Gray
                    )
                }
            }

            // Progress bar for currently live show
            if (isLive) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.15f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction = progress.coerceIn(0.02f, 1f))
                            .height(3.dp)
                            .background(Color(0xFFE50914))
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(3.dp))
            }
        }
    }
}

private data class TimeHeaderSlot(
    val label: String,
    val isNow: Boolean
)

private fun generateTimeHeaders(nowMs: Long): List<TimeHeaderSlot> {
    val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
    val list = mutableListOf<TimeHeaderSlot>()
    list.add(TimeHeaderSlot("NOW AIRING (${sdf.format(Date(nowMs))})", isNow = true))

    // Generate upcoming 30m slots for next 3 hours
    for (i in 1..6) {
        val slotMs = nowMs + (i * 30 * 60 * 1000L)
        list.add(TimeHeaderSlot(sdf.format(Date(slotMs)), isNow = false))
    }
    return list
}
