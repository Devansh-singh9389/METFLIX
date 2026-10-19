package com.example.metflix.presentation.screens.livetv.components

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.outlined.Upcoming
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.example.metflix.data.entities.LiveChannel
import com.example.metflix.presentation.theme.MetFlixCardShape
import kotlinx.coroutines.delay

private val CardBackgroundBrush = Brush.verticalGradient(
    colors = listOf(
        Color(0xFF222428),
        Color(0xFF16181B)
    )
)

@Composable
fun JioTvChannelCard(
    channel: LiveChannel,
    onClick: () -> Unit,
    onFocus: () -> Unit,
    modifier: Modifier = Modifier,
    currentTimeMs: Long = System.currentTimeMillis()
) {
    val currentProg = remember(channel, currentTimeMs) {
        channel.getCurrentEpgProgram(currentTimeMs)
    }
    val nextProg = remember(channel, currentTimeMs) {
        channel.getNextEpgProgram(currentTimeMs)
    }
    val progress = currentProg.progress(currentTimeMs)
    val remainingText = currentProg.getFormattedRemainingText(currentTimeMs)

    Card(
        onClick = onClick,
        modifier = modifier
            .onFocusChanged {
                if (it.isFocused) onFocus()
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardBackgroundBrush)
                .padding(14.dp)
        ) {
            // Header Row: Channel Badge, LIVE indicator, Category and Favorite
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Channel number pill
                Box(
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.primaryContainer,
                            RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "CH ${channel.channelNumber}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontSize = 11.sp
                        )
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                JioTvLiveBadge()

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = channel.category,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.weight(1f))

                if (channel.isFavorite) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Favorite",
                        tint = Color(0xFFFFD700),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Channel Brand & Logo Area
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Logo Container
                Box(
                    modifier = Modifier
                        .size(68.dp, 48.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (!channel.logoUrl.isNullOrBlank()) {
                        val context = LocalContext.current
                        val imageRequest = remember(channel.logoUrl) {
                            ImageRequest.Builder(context)
                                .data(channel.logoUrl)
                                .size(140, 96)
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
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Channel Name & Language
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = channel.name,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!channel.language.isNullOrBlank()) {
                        Text(
                            text = channel.language.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Current Show Section (JioTV highlight)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.35f))
                    .padding(8.dp)
            ) {
                // Current Program Title
                Text(
                    text = currentProg.title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Time Slot & Remaining Countdown
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = Color.LightGray,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = currentProg.formattedTimeSlot,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = Color.LightGray
                        )
                    }

                    Box(
                        modifier = Modifier
                            .background(
                                Color(0xFFE50914).copy(alpha = 0.25f),
                                RoundedCornerShape(3.dp)
                            )
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = remainingText,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFF5252),
                                fontSize = 10.sp
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // JioTV Signature Red Linear Progress Bar
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
            }

            // Up Next Preview
            if (nextProg != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Upcoming,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Next: ${nextProg.formattedStartTime} • ${nextProg.title}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color.Gray,
                            fontSize = 10.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun JioTvLiveBadge(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(Color(0xFFE50914).copy(alpha = 0.2f), RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(Color(0xFFE50914), CircleShape)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "LIVE",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Black,
                color = Color(0xFFE50914),
                fontSize = 9.sp,
                letterSpacing = 0.5.sp
            )
        )
    }
}
