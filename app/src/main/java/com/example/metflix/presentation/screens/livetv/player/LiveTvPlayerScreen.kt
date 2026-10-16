package com.example.metflix.presentation.screens.livetv.player

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import androidx.media3.ui.compose.modifiers.resizeWithContentScale
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.example.metflix.data.entities.LiveChannel
import com.example.metflix.presentation.common.Error
import com.example.metflix.presentation.common.Loading
import com.example.metflix.presentation.screens.livetv.components.LiveBadge
import com.example.metflix.presentation.screens.livetv.player.components.LiveTvAspectRatio
import com.example.metflix.presentation.screens.livetv.player.components.LiveTvSettingsOverlay
import com.example.metflix.presentation.screens.livetv.player.components.LiveTvStatsOverlay
import com.example.metflix.presentation.screens.videoPlayer.components.rememberPlayer
import com.example.metflix.presentation.theme.MetFlixCardShape
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object LiveTvPlayerScreen {
    const val ChannelIdBundleKey = "channelId"
}

@Composable
fun LiveTvPlayerScreen(
    onBackPressed: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LiveTvPlayerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when (val state = uiState) {
        is LiveTvPlayerUiState.Loading -> {
            Loading(modifier = modifier.fillMaxSize())
        }

        is LiveTvPlayerUiState.Error -> {
            Error(modifier = modifier.fillMaxSize())
        }

        is LiveTvPlayerUiState.Ready -> {
            LiveTvPlayerContent(
                state = state,
                onNextChannel = viewModel::nextChannel,
                onPreviousChannel = viewModel::previousChannel,
                onSelectChannel = viewModel::selectChannel,
                onToggleFavorite = viewModel::toggleFavorite,
                onBackPressed = onBackPressed,
                modifier = modifier
            )
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun LiveTvPlayerContent(
    state: LiveTvPlayerUiState.Ready,
    onNextChannel: () -> Unit,
    onPreviousChannel: () -> Unit,
    onSelectChannel: (LiveChannel) -> Unit,
    onToggleFavorite: () -> Unit,
    onBackPressed: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val exoPlayer = rememberPlayer(context)

    var isDrawerOpen by remember { mutableStateOf(false) }
    var isSettingsOpen by remember { mutableStateOf(false) }
    var isStatsVisible by remember { mutableStateOf(false) }
    var isOsdVisible by remember { mutableStateOf(true) }
    var playbackError by remember { mutableStateOf<String?>(null) }

    // Settings States
    var currentAspectRatio by remember { mutableStateOf(LiveTvAspectRatio.Fit) }
    var currentQualityMaxHeight by remember { mutableIntStateOf(0) }
    var currentSleepTimerMinutes by remember { mutableIntStateOf(0) }

    // Stream Diagnostics States
    var videoResolution by remember { mutableStateOf("") }
    var videoBitrate by remember { mutableStateOf("") }
    var audioInfo by remember { mutableStateOf("") }
    var bufferHealthMs by remember { mutableLongStateOf(0L) }
    var availableAudioTracks by remember { mutableStateOf(listOf("Default Audio")) }
    var selectedAudioTrackIndex by remember { mutableIntStateOf(0) }
    var availableVideoHeights by remember { mutableStateOf<List<Int>>(emptyList()) }
    var sourceResolutionLabel by remember { mutableStateOf("") }

    val rootFocusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    // Auto-hide OSD banner after 5 seconds if not inside settings or drawer
    LaunchedEffect(state.currentChannel.id, isOsdVisible, isSettingsOpen, isDrawerOpen) {
        if (isOsdVisible && !isDrawerOpen && !isSettingsOpen) {
            delay(5000)
            isOsdVisible = false
        }
    }

    // Sleep Timer countdown
    LaunchedEffect(currentSleepTimerMinutes) {
        if (currentSleepTimerMinutes > 0) {
            delay(currentSleepTimerMinutes * 60 * 1000L)
            exoPlayer.stop()
            onBackPressed()
        }
    }

    // Diagnostics periodic polling
    LaunchedEffect(isStatsVisible, state.currentChannel.id) {
        while (isStatsVisible) {
            bufferHealthMs = exoPlayer.totalBufferedDuration.coerceAtLeast(0)
            val format = exoPlayer.videoFormat
            if (format != null) {
                if (format.bitrate > 0) {
                    videoBitrate = "${format.bitrate / 1000} kbps"
                }
                if (format.width > 0 && format.height > 0) {
                    videoResolution = "${format.width}x${format.height}"
                }
            }
            delay(1000)
        }
    }

    fun loadCurrentStream() {
        playbackError = null
        isOsdVisible = true

        try {
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            val mediaItem = MediaItem.Builder()
                .setUri(Uri.parse(state.currentChannel.streamUrl))
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(state.currentChannel.name)
                        .setArtist(state.currentChannel.currentProgram)
                        .build()
                )
                .build()

            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        } catch (e: Exception) {
            playbackError = e.localizedMessage ?: "Failed to load channel"
        }
    }

    // Set up ExoPlayer for current live channel
    LaunchedEffect(state.currentChannel.id) {
        loadCurrentStream()
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                playbackError = error.localizedMessage ?: "Stream unavailable"
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    videoResolution = "${videoSize.width}x${videoSize.height}"
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                val audioNames = mutableListOf<String>()
                val vHeights = mutableSetOf<Int>()
                var highestFormat: Format? = null
                for (group in tracks.groups) {
                    if (group.type == C.TRACK_TYPE_AUDIO) {
                        for (i in 0 until group.length) {
                            val format = group.getTrackFormat(i)
                            val lang = format.language?.uppercase() ?: "AUDIO ${audioNames.size + 1}"
                            val chCount = if (format.channelCount > 0) " (${format.channelCount}ch)" else ""
                            audioNames.add("$lang$chCount")
                        }
                    } else if (group.type == C.TRACK_TYPE_VIDEO) {
                        for (i in 0 until group.length) {
                            val format = group.getTrackFormat(i)
                            if (format.height > 0) {
                                vHeights.add(format.height)
                            }
                            if (highestFormat == null || format.height > (highestFormat?.height ?: 0)) {
                                highestFormat = format
                            }
                        }
                    }
                }
                if (audioNames.isNotEmpty()) {
                    availableAudioTracks = audioNames
                }
                availableVideoHeights = vHeights.sortedDescending()
                sourceResolutionLabel = highestFormat?.let { fmt ->
                    val p = if (fmt.height > 0) "${fmt.height}p" else ""
                    val fps = if (fmt.frameRate > 0) " @ ${fmt.frameRate.toInt()}fps" else ""
                    "$p$fps".trim()
                } ?: videoResolution
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
        }
    }

    BackHandler {
        if (isSettingsOpen) {
            isSettingsOpen = false
            runCatching { rootFocusRequester.requestFocus() }
        } else if (isDrawerOpen) {
            isDrawerOpen = false
            runCatching { rootFocusRequester.requestFocus() }
        } else {
            onBackPressed()
        }
    }

    LaunchedEffect(isDrawerOpen, isSettingsOpen) {
        if (!isDrawerOpen && !isSettingsOpen) {
            runCatching { rootFocusRequester.requestFocus() }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp) {
                    when (event.key) {
                        Key.DirectionUp -> {
                            if (!isDrawerOpen && !isSettingsOpen) {
                                isOsdVisible = true
                                onNextChannel()
                                true
                            } else false
                        }

                        Key.DirectionDown -> {
                            if (!isDrawerOpen && !isSettingsOpen) {
                                isOsdVisible = true
                                onPreviousChannel()
                                true
                            } else false
                        }

                        Key.DirectionLeft, Key.DirectionCenter, Key.Enter -> {
                            if (!isDrawerOpen && !isSettingsOpen) {
                                isDrawerOpen = true
                                isOsdVisible = false
                                true
                            } else false
                        }

                        Key.DirectionRight -> {
                            if (isDrawerOpen) {
                                isDrawerOpen = false
                                runCatching { rootFocusRequester.requestFocus() }
                                true
                            } else if (!isSettingsOpen) {
                                onToggleFavorite()
                                isOsdVisible = true
                                true
                            } else false
                        }

                        Key.Menu, Key.Settings -> {
                            isSettingsOpen = !isSettingsOpen
                            isOsdVisible = false
                            true
                        }

                        else -> false
                    }
                } else {
                    false
                }
            }
    ) {
        // Video Surface with dynamic Aspect Ratio
        PlayerSurface(
            player = exoPlayer,
            surfaceType = SURFACE_TYPE_SURFACE_VIEW,
            modifier = Modifier
                .fillMaxSize()
                .resizeWithContentScale(
                    contentScale = currentAspectRatio.contentScale,
                    sourceSizeDp = null
                )
        )

        // Real-Time Stream Diagnostics (Stats for Nerds)
        if (isStatsVisible && !isDrawerOpen && !isSettingsOpen) {
            LiveTvStatsOverlay(
                channel = state.currentChannel,
                videoResolution = videoResolution,
                videoBitrate = videoBitrate,
                audioInfo = audioInfo,
                bufferHealthMs = bufferHealthMs,
                modifier = Modifier.align(Alignment.TopEnd)
            )
        }

        // Error message if stream fails
        if (playbackError != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.65f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Tv,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Unable to connect to live stream",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Press D-Pad UP or DOWN to zap, or click Reconnect",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.LightGray
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { loadCurrentStream() }) {
                        Text("Reconnect Stream")
                    }
                }
            }
        }

        // Bottom OSD Channel Banner & Interactive Action Controls
        AnimatedVisibility(
            visible = isOsdVisible && !isDrawerOpen && !isSettingsOpen,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.8f),
                                Color.Black.copy(alpha = 0.98f)
                            )
                        )
                    )
                    .padding(horizontal = 40.dp, vertical = 22.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Channel Logo
                        if (!state.currentChannel.logoUrl.isNullOrBlank()) {
                            Box(
                                modifier = Modifier
                                    .size(80.dp, 56.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.Black.copy(alpha = 0.6f))
                                    .padding(6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = state.currentChannel.logoUrl,
                                    contentDescription = state.currentChannel.name,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "CH ${state.currentChannel.channelNumber}",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(18.dp))

                        // Channel info & current show
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                LiveBadge()
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "CH ${state.currentChannel.channelNumber}",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = "•", color = Color.Gray)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = state.currentChannel.category.uppercase(),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = Color.LightGray
                                )
                                if (videoResolution.isNotBlank()) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(text = "•", color = Color.Gray)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = videoResolution,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(2.dp))

                            Text(
                                text = state.currentChannel.name,
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )

                            Text(
                                text = "Now: ${state.currentChannel.currentProgram}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.85f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Navigation Zapping Hints
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "▲ / ▼ Zap Channel",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.LightGray
                            )
                            Text(
                                text = "◀ Guide  •  ▶ Favorite",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.LightGray
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Interactive Action & Settings Button Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Channel Guide button
                        Button(
                            onClick = {
                                isDrawerOpen = true
                                isOsdVisible = false
                            },
                            colors = ButtonDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                contentColor = MaterialTheme.colorScheme.onSurface
                            )
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Menu, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Channels", style = MaterialTheme.typography.labelSmall)
                            }
                        }

                        // Favorite toggle button
                        Button(
                            onClick = onToggleFavorite,
                            colors = ButtonDefaults.colors(
                                containerColor = if (state.currentChannel.isFavorite) Color(0xFFFFD700).copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                contentColor = if (state.currentChannel.isFavorite) Color(0xFFFFD700) else MaterialTheme.colorScheme.onSurface
                            )
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (state.currentChannel.isFavorite) Icons.Default.Star else Icons.Outlined.StarBorder,
                                    contentDescription = null,
                                    tint = if (state.currentChannel.isFavorite) Color(0xFFFFD700) else Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    if (state.currentChannel.isFavorite) "Favorited" else "Favorite",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }

                        // Reconnect / Reload button
                        Button(
                            onClick = { loadCurrentStream() },
                            colors = ButtonDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                contentColor = MaterialTheme.colorScheme.onSurface
                            )
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Reload", style = MaterialTheme.typography.labelSmall)
                            }
                        }

                        // Stats toggle button
                        Button(
                            onClick = { isStatsVisible = !isStatsVisible },
                            colors = ButtonDefaults.colors(
                                containerColor = if (isStatsVisible) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                contentColor = if (isStatsVisible) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurface
                            )
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Stats", style = MaterialTheme.typography.labelSmall)
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        // Settings Button
                        Button(
                            onClick = {
                                isSettingsOpen = true
                                isOsdVisible = false
                            },
                            colors = ButtonDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Settings", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                            }
                        }
                    }
                }
            }
        }

        // Side Mini-Guide / Channel Switcher Drawer
        AnimatedVisibility(
            visible = isDrawerOpen,
            enter = slideInHorizontally(initialOffsetX = { -it }),
            exit = slideOutHorizontally(targetOffsetX = { -it }),
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(360.dp)
                    .background(Color.Black.copy(alpha = 0.94f))
                    .padding(20.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Live Channels (${state.channels.size})",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                        Text(
                            text = "▶ Close",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    val listState = rememberLazyListState(
                        initialFirstVisibleItemIndex = state.currentIndex.coerceAtLeast(0)
                    )

                    LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(
                            items = state.channels,
                            key = { it.id }
                        ) { ch ->
                            val isCurrent = ch.id == state.currentChannel.id

                            Card(
                                onClick = {
                                    onSelectChannel(ch)
                                    isDrawerOpen = false
                                    runCatching { rootFocusRequester.requestFocus() }
                                },
                                shape = CardDefaults.shape(MetFlixCardShape),
                                colors = CardDefaults.colors(
                                    containerColor = if (isCurrent) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                    } else {
                                        Color.White.copy(alpha = 0.08f)
                                    }
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (isCurrent) {
                                            Modifier.border(
                                                1.dp,
                                                MaterialTheme.colorScheme.primary,
                                                MetFlixCardShape
                                            )
                                        } else Modifier
                                    )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${ch.channelNumber}",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = if (isCurrent) MaterialTheme.colorScheme.primary else Color.LightGray
                                        ),
                                        modifier = Modifier.width(36.dp)
                                    )

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = ch.name,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                                            ),
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = ch.category,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.Gray
                                        )
                                    }

                                    if (isCurrent) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Currently Playing",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    } else if (ch.isFavorite) {
                                        Icon(
                                            imageVector = Icons.Default.Star,
                                            contentDescription = "Favorite",
                                            tint = Color(0xFFFFD700),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Live TV Settings Modal
        LiveTvSettingsOverlay(
            isPresented = isSettingsOpen,
            currentAspectRatio = currentAspectRatio,
            currentQualityMaxHeight = currentQualityMaxHeight,
            currentSleepTimerMinutes = currentSleepTimerMinutes,
            isStatsVisible = isStatsVisible,
            availableAudioTracks = availableAudioTracks,
            selectedAudioTrackIndex = selectedAudioTrackIndex,
            availableVideoHeights = availableVideoHeights,
            sourceResolutionLabel = sourceResolutionLabel,
            onSelectAspectRatio = { currentAspectRatio = it },
            onSelectQuality = { height ->
                currentQualityMaxHeight = height
                val builder = exoPlayer.trackSelectionParameters.buildUpon()
                if (height > 0) {
                    builder.setMaxVideoSize(Int.MAX_VALUE, height)
                } else {
                    builder.clearVideoSizeConstraints()
                }
                exoPlayer.trackSelectionParameters = builder.build()
            },
            onSelectSleepTimer = { currentSleepTimerMinutes = it },
            onSelectAudioTrack = { index ->
                selectedAudioTrackIndex = index
                if (index < availableAudioTracks.size) {
                    val trackName = availableAudioTracks[index]
                    val lang = trackName.split(" ").firstOrNull()?.lowercase()
                    if (!lang.isNullOrBlank()) {
                        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                            .buildUpon()
                            .setPreferredAudioLanguage(lang)
                            .build()
                    }
                }
            },
            onToggleStats = { isStatsVisible = !isStatsVisible },
            onReloadStream = { loadCurrentStream() },
            onDismiss = {
                isSettingsOpen = false
                runCatching { rootFocusRequester.requestFocus() }
            }
        )
    }
}
