package com.example.metflix.presentation.screens.livetv

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.CalendarViewMonth
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Tab
import androidx.tv.material3.TabRow
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
                onCategorySelect = viewModel::onCategorySelected,
                onChannelFocus = viewModel::onChannelFocused,
                onChannelClick = onChannelClick,
                onToggleFavorite = { viewModel.toggleFavorite(it.id) },
                onRefresh = { viewModel.refreshChannels() },
                onSearchChange = viewModel::onSearchQueryChange,
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
    onCategorySelect: (String) -> Unit,
    onChannelFocus: (LiveChannel) -> Unit,
    onChannelClick: (LiveChannel) -> Unit,
    onToggleFavorite: (LiveChannel) -> Unit,
    onRefresh: () -> Unit,
    onSearchChange: (String) -> Unit,
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

    val shouldShowTopBar by remember {
        derivedStateOf {
            if (isTimelineGuideMode) true
            else gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset < 100
        }
    }

    LaunchedEffect(shouldShowTopBar) {
        onScroll(shouldShowTopBar)
    }

    val selectedCategoryIndex = state.categories.indexOf(state.selectedCategory).coerceAtLeast(0)

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = childPadding.start)
        ) {
            // JioTV Hero Banner: Focused Channel, Now Airing show title, red progress bar & schedule
            JioTvHeaderPreview(
                channel = state.focusedChannel,
                onWatchClick = onChannelClick,
                onToggleFavorite = onToggleFavorite,
                modifier = Modifier.padding(top = 8.dp, bottom = 10.dp)
            )

            // Category Filter Row & Action Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TabRow(
                    selectedTabIndex = selectedCategoryIndex,
                    modifier = Modifier.weight(1f)
                ) {
                    state.categories.forEachIndexed { index, cat ->
                        Tab(
                            selected = index == selectedCategoryIndex,
                            onFocus = { onCategorySelect(cat) },
                            onClick = { onCategorySelect(cat) }
                        ) {
                            Text(
                                text = cat,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

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
                        text = if (isTimelineGuideMode) "Cards View" else "EPG Guide",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

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
                    Text(text = "IPTV Sources", style = MaterialTheme.typography.labelSmall)
                }

                Spacer(modifier = Modifier.width(8.dp))

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
            }

            // Search Bar & Sort Order Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Search Input Box
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = Color.LightGray,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    BasicTextField(
                        value = state.searchQuery,
                        onValueChange = onSearchChange,
                        textStyle = TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.weight(1f),
                        decorationBox = { innerTextField ->
                            if (state.searchQuery.isEmpty()) {
                                Text(
                                    text = "Filter channels by name, category, or number…",
                                    color = Color.Gray,
                                    fontSize = 12.sp
                                )
                            }
                            innerTextField()
                        }
                    )
                    if (state.searchQuery.isNotEmpty()) {
                        Button(
                            onClick = { onSearchChange("") },
                            shape = ButtonDefaults.shape(RoundedCornerShape(50)),
                            colors = ButtonDefaults.colors(containerColor = Color.Transparent)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear",
                                tint = Color.LightGray,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

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
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Sort: ${state.sortOrder.title}",
                        style = MaterialTheme.typography.labelSmall
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Total Filtered Count Badge
                Text(
                    text = "${state.filteredChannels.size} of ${state.totalCount} channels",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Main Channel Content: EPG Timeline Guide or JioTV Channel Cards Grid
            if (state.filteredChannels.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
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
                            text = if (state.searchQuery.isNotBlank()) {
                                "No channels matching \"${state.searchQuery}\""
                            } else if (state.selectedCategory.equals("Favorites", ignoreCase = true)) {
                                "No favorite channels yet. Add channels to Favorites by clicking 'Favorite'!"
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
                    modifier = Modifier.weight(1f)
                )
            } else {
                // JioTV Channel Cards Mode (with live program, progress bar & remaining time)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    state = gridState,
                    contentPadding = PaddingValues(bottom = MetFlixBottomListPadding),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(
                        items = state.filteredChannels,
                        key = { it.id }
                    ) { channel ->
                        JioTvChannelCard(
                            channel = channel,
                            onClick = { onChannelClick(channel) },
                            onFocus = { debouncedOnChannelFocus(channel) },
                            modifier = Modifier.fillMaxWidth()
                        )
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
