package com.example.metflix.presentation.screens.livetv

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.CalendarViewMonth
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.example.metflix.data.entities.LiveChannel
import com.example.metflix.presentation.common.Error
import com.example.metflix.presentation.common.Loading
import com.example.metflix.presentation.screens.dashboard.rememberChildPadding
import com.example.metflix.presentation.screens.livetv.components.JioTvChannelCard
import com.example.metflix.presentation.screens.livetv.components.JioTvGuideTimelineView
import com.example.metflix.presentation.screens.livetv.components.JioTvHeaderPreview
import com.example.metflix.presentation.screens.livetv.components.LiveTvPlaylistDialog
import com.example.metflix.presentation.theme.MetFlixBottomListPadding

@Composable
fun LiveTvScreen(
    onChannelClick: (LiveChannel) -> Unit,
    onScroll: (isTopBarVisible: Boolean) -> Unit,
    isTopBarVisible: Boolean,
    modifier: Modifier = Modifier,
    viewModel: LiveTvViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val focusedChannel by viewModel.focusedChannel.collectAsStateWithLifecycle()

    when (val state = uiState) {
        is LiveTvUiState.Loading -> {
            Loading(modifier = modifier.fillMaxSize())
        }

        is LiveTvUiState.Error -> {
            Error(modifier = modifier.fillMaxSize())
        }

        is LiveTvUiState.Ready -> {
            LiveTvCatalog(
                state = state,
                focusedChannel = focusedChannel,
                onCategorySelect = viewModel::onCategorySelected,
                onChannelFocus = viewModel::onChannelFocused,
                onChannelClick = onChannelClick,
                onRefresh = { viewModel.refreshChannels() },
                onSortChange = viewModel::onSortOrderChange,
                onSelectPlaylist = viewModel::selectPlaylistUrl,
                onScroll = onScroll,
                isTopBarVisible = isTopBarVisible,
                modifier = modifier
            )
        }
    }
}

@Composable
private fun LiveTvCatalog(
    state: LiveTvUiState.Ready,
    focusedChannel: LiveChannel?,
    onCategorySelect: (String) -> Unit,
    onChannelFocus: (LiveChannel) -> Unit,
    onChannelClick: (LiveChannel) -> Unit,
    onRefresh: () -> Unit,
    onSortChange: (LiveTvSortOrder) -> Unit,
    onSelectPlaylist: (String) -> Unit,
    onScroll: (isTopBarVisible: Boolean) -> Unit,
    isTopBarVisible: Boolean,
    modifier: Modifier = Modifier
) {
    val childPadding = rememberChildPadding()
    val gridState = rememberLazyGridState()
    var isPlaylistDialogOpen by remember { mutableStateOf(false) }
    var isTimelineGuideMode by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    var focusDebounceJob by remember { mutableStateOf<Job?>(null) }
    val debouncedOnChannelFocus: (LiveChannel) -> Unit = remember(onChannelFocus) {
        { channel ->
            focusDebounceJob?.cancel()
            focusDebounceJob = coroutineScope.launch {
                delay(150)
                onChannelFocus(channel)
            }
        }
    }

    LaunchedEffect(Unit) {
        onScroll(true)
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = childPadding.start)
        ) {
            // JioTV Hero Banner: Focused Channel, Now Airing show title, red progress bar & schedule
            JioTvHeaderPreview(
                channel = focusedChannel,
                modifier = Modifier.padding(top = 6.dp, bottom = 8.dp)
            )

            // Source & Regional Switcher Toolbar Row (100% TV Remote Reachable)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Quick Source Selector: Hindi, World, English, All Sources
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val isHindi = state.currentPlaylistUrl.contains("hin.m3u")
                    val isWorld = state.currentPlaylistUrl.contains("index.m3u")
                    val isEnglish = state.currentPlaylistUrl.contains("eng.m3u")

                    Button(
                        onClick = { onSelectPlaylist("https://iptv-org.github.io/iptv/languages/hin.m3u") },
                        colors = ButtonDefaults.colors(
                            containerColor = if (isHindi) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (isHindi) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text(
                            text = "🇮🇳 Hindi",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isHindi) FontWeight.Bold else FontWeight.Normal
                            )
                        )
                    }

                    Button(
                        onClick = { onSelectPlaylist("https://iptv-org.github.io/iptv/index.m3u") },
                        colors = ButtonDefaults.colors(
                            containerColor = if (isWorld) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (isWorld) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text(
                            text = "🌍 World",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isWorld) FontWeight.Bold else FontWeight.Normal
                            )
                        )
                    }

                    Button(
                        onClick = { onSelectPlaylist("https://iptv-org.github.io/iptv/languages/eng.m3u") },
                        colors = ButtonDefaults.colors(
                            containerColor = if (isEnglish) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (isEnglish) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text(
                            text = "🌐 English",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isEnglish) FontWeight.Bold else FontWeight.Normal
                            )
                        )
                    }

                    // IPTV Sources Dialog Button
                    Button(
                        onClick = { isPlaylistDialogOpen = true },
                        colors = ButtonDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.List,
                            contentDescription = "IPTV Sources",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "IPTV Sources", style = MaterialTheme.typography.labelMedium)
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Tools: View Mode Toggle, Sort, Refresh, Total CH
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // View Mode Toggle (Cards vs JioTV EPG Guide Timeline)
                    Button(
                        onClick = { isTimelineGuideMode = !isTimelineGuideMode },
                        colors = ButtonDefaults.colors(
                            containerColor = if (isTimelineGuideMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (isTimelineGuideMode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Icon(
                            imageVector = if (isTimelineGuideMode) Icons.Default.GridView else Icons.Default.CalendarViewMonth,
                            contentDescription = "Toggle Guide View",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isTimelineGuideMode) "Cards" else "EPG Guide",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    // Sort Order Toggle Button
                    Button(
                        onClick = {
                            val next = if (state.sortOrder == LiveTvSortOrder.ChannelNumber) {
                                LiveTvSortOrder.NameAsc
                            } else {
                                LiveTvSortOrder.ChannelNumber
                            }
                            onSortChange(next)
                        },
                        colors = ButtonDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Sort,
                            contentDescription = "Sort",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = state.sortOrder.title,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }

                    // Refresh Button
                    Button(
                        onClick = onRefresh,
                        colors = ButtonDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Sync",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (state.isRefreshing) "Syncing…" else "Sync",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }

                    // Total Filtered Count Badge
                    Text(
                        text = "${state.filteredChannels.size} CH",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Main Channel Content: Left Vertical Category Rail + Right Channel Grid / Guide
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                // Vertical Category Rail (Set-Top Box Sidebar for smooth 20-50+ category surfing)
                LazyColumn(
                    modifier = Modifier
                        .width(200.dp)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(end = 12.dp, bottom = MetFlixBottomListPadding)
                ) {
                    items(
                        items = state.categories,
                        key = { it }
                    ) { cat ->
                        val isSelected = cat.equals(state.selectedCategory, ignoreCase = true)
                        Card(
                            onClick = { onCategorySelect(cat) },
                            colors = CardDefaults.colors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                focusedContainerColor = MaterialTheme.colorScheme.primary,
                                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                focusedContentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = CardDefaults.shape(RoundedCornerShape(8.dp)),
                            scale = CardDefaults.scale(scale = 1.0f, focusedScale = 1.02f),
                            border = CardDefaults.border(
                                border = if (isSelected) Border(border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary), shape = RoundedCornerShape(8.dp)) else Border.None,
                                focusedBorder = Border(border = BorderStroke(2.dp, Color.White), shape = RoundedCornerShape(8.dp))
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val icon = when {
                                    cat.equals("Favorites", ignoreCase = true) -> Icons.Default.Star
                                    cat.equals("All", ignoreCase = true) -> Icons.Default.Tv
                                    else -> null
                                }
                                if (icon != null) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text(
                                    text = cat,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Right: Channel Cards Grid or Full EPG Timeline Guide
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    if (state.filteredChannels.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Tv,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = if (state.selectedCategory.equals("Favorites", ignoreCase = true)) {
                                        "No favorite channels yet. Add channels to Favorites!"
                                    } else {
                                        "No channels found in this category."
                                    },
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else if (isTimelineGuideMode) {
                        // JioTV Full Electronic Program Guide (EPG Timeline Mode)
                        JioTvGuideTimelineView(
                            channels = state.filteredChannels,
                            onChannelClick = onChannelClick,
                            onChannelFocus = debouncedOnChannelFocus,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        // JioTV Channel Cards Grid (with live program, progress bar & remaining time)
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            state = gridState,
                            contentPadding = PaddingValues(bottom = MetFlixBottomListPadding),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(
                                items = state.filteredChannels,
                                key = { it.id },
                                contentType = { "channel_card" }
                            ) { channel ->
                                JioTvChannelCard(
                                    channel = channel,
                                    onClick = { onChannelClick(channel) },
                                    onFocus = { debouncedOnChannelFocus(channel) },
                                    currentTimeMs = state.currentTimeMs,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        }

        // IPTV Sources / Playlist Selection Dialog
        LiveTvPlaylistDialog(
            isPresented = isPlaylistDialogOpen,
            currentPlaylistUrl = state.currentPlaylistUrl,
            onSelectPlaylist = onSelectPlaylist,
            onDismiss = { isPlaylistDialogOpen = false }
        )
    }
}
