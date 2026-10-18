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
import androidx.tv.material3.CardBorder
import androidx.tv.material3.CardColors
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.CardScale
import androidx.tv.material3.CardShape
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.example.metflix.data.entities.LiveChannel
import kotlinx.coroutines.delay

private val DrawerItemShape = RoundedCornerShape(10.dp)
private val BadgeShape = RoundedCornerShape(6.dp)
private val FocusedBorderStroke = BorderStroke(2.dp, Color.White)
private val FocusedBorder = Border(
    border = FocusedBorderStroke,
    shape = DrawerItemShape
)
private val DrawerBackgroundColor = Color(0xFF0E1013)

@Composable
fun LiveTvChannelDrawer(
    isPresented: Boolean,
    currentChannel: LiveChannel,
    channels: List<LiveChannel>,
    onSelectChannel: (LiveChannel) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    currentTimeMs: Long = remember { System.currentTimeMillis() }
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

    // Pre-allocate styles once for all items in the drawer to eliminate per-card allocations during scrolling
    val cardShape = CardDefaults.shape(DrawerItemShape)
    val cardScale = CardDefaults.scale(scale = 1f, focusedScale = 1f)
    val normalBorder = CardDefaults.border(
        border = Border.None,
        focusedBorder = FocusedBorder
    )
    val currentBorder = CardDefaults.border(
        border = Border(
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
            shape = DrawerItemShape
        ),
        focusedBorder = FocusedBorder
    )
    val normalColors = CardDefaults.colors(
        containerColor = Color.White.copy(alpha = 0.08f),
        focusedContainerColor = MaterialTheme.colorScheme.primary,
        contentColor = Color.White,
        focusedContentColor = MaterialTheme.colorScheme.onPrimary
    )
    val currentColors = CardDefaults.colors(
        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
        focusedContainerColor = MaterialTheme.colorScheme.primary,
        contentColor = Color.White,
        focusedContentColor = MaterialTheme.colorScheme.onPrimary
    )

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
                .background(DrawerBackgroundColor)
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
                            currentTimeMs = currentTimeMs,
                            shape = cardShape,
                            colors = if (isCurrent) currentColors else normalColors,
                            scale = cardScale,
                            border = if (isCurrent) currentBorder else normalBorder,
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
    currentTimeMs: Long,
    shape: CardShape,
    colors: CardColors,
    scale: CardScale,
    border: CardBorder,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isItemFocused by remember { mutableStateOf(false) }

    val (progTitle, progSubtitle) = remember(channel.id, currentTimeMs) {
        val prog = channel.getCurrentEpgProgram(currentTimeMs)
        val subtitle = "${prog.formattedStartTime} • ${prog.getFormattedRemainingText(currentTimeMs)}"
        prog.title to subtitle
    }

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { isItemFocused = it.isFocused },
        shape = shape,
        colors = colors,
        scale = scale,
        border = border
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
                    .clip(BadgeShape)
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
                Text(
                    text = progTitle,
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
                    text = progSubtitle,
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
