package com.example.metflix.presentation.screens.livetv.player.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
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
import com.example.metflix.data.entities.LiveChannel
import kotlinx.coroutines.delay

@Composable
fun LiveTvChannelDrawer(
    isPresented: Boolean,
    currentChannel: LiveChannel,
    channels: List<LiveChannel>,
    onSelectChannel: (LiveChannel) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isPresented) return

    BackHandler(onBack = onDismiss)

    val currentChannelIndex = remember(channels, currentChannel.id) {
        channels.indexOfFirst { it.id == currentChannel.id }.coerceAtLeast(0)
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = currentChannelIndex)
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isPresented) {
        if (isPresented) {
            delay(50)
            listState.scrollToItem(currentChannelIndex)
            delay(50)
            runCatching { focusRequester.requestFocus() }
        }
    }

    AnimatedVisibility(
        visible = isPresented,
        enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(),
        exit = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(380.dp)
                .background(Color.Black.copy(alpha = 0.95f))
                .padding(top = 24.dp, start = 20.dp, end = 20.dp, bottom = 20.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header: Title with count & navigation hint
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Live Channels",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                        Text(
                            text = "${channels.size} Available Streams",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = "▶ / BACK to Close",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.Gray
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Scrollable List of Channels
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(
                        items = channels,
                        key = { _, ch -> ch.id }
                    ) { index, ch ->
                        val isCurrent = ch.id == currentChannel.id
                        val isFocusTarget = isCurrent || (currentChannelIndex == 0 && index == 0)

                        ChannelDrawerCard(
                            channel = ch,
                            isCurrent = isCurrent,
                            onClick = {
                                onSelectChannel(ch)
                                onDismiss()
                            },
                            modifier = if (isFocusTarget) Modifier.focusRequester(focusRequester) else Modifier
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelDrawerCard(
    channel: LiveChannel,
    isCurrent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isItemFocused by remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { isItemFocused = it.isFocused },
        shape = CardDefaults.shape(RoundedCornerShape(10.dp)),
        colors = CardDefaults.colors(
            containerColor = if (isCurrent) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            } else {
                Color.White.copy(alpha = 0.08f)
            },
            focusedContainerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.White,
            focusedContentColor = MaterialTheme.colorScheme.onPrimary
        ),
        scale = CardDefaults.scale(
            scale = 1f,
            focusedScale = 1f
        ),
        border = CardDefaults.border(
            border = if (isCurrent) {
                Border(
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(10.dp)
                )
            } else {
                Border.None
            },
            focusedBorder = Border(
                border = BorderStroke(2.dp, Color.White),
                shape = RoundedCornerShape(10.dp)
            )
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Channel Number Badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        if (isItemFocused) {
                            Color.Black.copy(alpha = 0.25f)
                        } else if (isCurrent) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            Color.White.copy(alpha = 0.12f)
                        }
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${channel.channelNumber}",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (isItemFocused) {
                        MaterialTheme.colorScheme.onPrimary
                    } else if (isCurrent) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        Color.LightGray
                    }
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Channel Title & Show/Category
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = channel.name,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (isCurrent || isItemFocused) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = if (isItemFocused) MaterialTheme.colorScheme.onPrimary else Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val prog = channel.getCurrentEpgProgram()
                Text(
                    text = prog.title,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal
                    ),
                    color = if (isItemFocused) {
                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
                    } else {
                        Color(0xFFFFD54F)
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${prog.formattedStartTime} • ${prog.getFormattedRemainingText()}",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = if (isItemFocused) {
                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f)
                    } else {
                        Color.Gray
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Status Indicator: Live Playing or Favorite
            if (isCurrent) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isItemFocused) Color.White else Color(0xFF00E676))
                    )
                    Text(
                        text = "LIVE",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 10.sp
                        ),
                        color = if (isItemFocused) MaterialTheme.colorScheme.onPrimary else Color(0xFF00E676)
                    )
                }
            } else if (channel.isFavorite) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = "Favorite",
                    tint = if (isItemFocused) Color.White else Color(0xFFFFD700),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
