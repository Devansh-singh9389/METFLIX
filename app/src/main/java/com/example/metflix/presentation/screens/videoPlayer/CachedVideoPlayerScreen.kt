package com.example.metflix.presentation.screens.videoPlayer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.C
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.activity.compose.BackHandler
import com.example.metflix.data.repositories.CachedPlaybackSession
import com.example.metflix.data.repositories.CachedPlaybackSessionStore
import com.example.metflix.data.network.torbox.TorBoxClient
import com.example.metflix.data.settings.DebridPreferences
import com.example.metflix.data.downloads.DownloadedCatalogRepository
import com.example.metflix.presentation.screens.videoPlayer.components.rememberPlayer
import java.io.File
import android.net.Uri
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.launch

@HiltViewModel
class CachedVideoPlayerViewModel @Inject constructor(
    private val sessionStore: CachedPlaybackSessionStore,
    private val torBoxClient: TorBoxClient,
    private val preferences: DebridPreferences
) : ViewModel() {
    fun get(sessionId: String): CachedPlaybackSession? {
        val torBoxSession = sessionStore.get(sessionId)
        if (torBoxSession != null) return torBoxSession

        val file = File(sessionId)
        if (file.exists()) {
            return CachedPlaybackSession(
                url = Uri.fromFile(file).toString(),
                title = file.name,
                torrentId = 0,
                fileId = null
            )
        }

        val downloaded = DownloadedCatalogRepository.instance?.downloadedItems?.value?.find {
            it.localFilePath == sessionId || it.id == sessionId || it.mediaId == sessionId
        }
        if (downloaded != null) {
            if (downloaded.localFilePath.isNotBlank() && File(downloaded.localFilePath).exists()) {
                val f = File(downloaded.localFilePath)
                return CachedPlaybackSession(
                    url = Uri.fromFile(f).toString(),
                    title = downloaded.title,
                    torrentId = 0,
                    fileId = null
                )
            } else if (downloaded.directUrl.isNotBlank()) {
                return CachedPlaybackSession(
                    url = downloaded.directUrl,
                    title = downloaded.title,
                    torrentId = 0,
                    fileId = null
                )
            }
        }

        return null
    }

    suspend fun refresh(sessionId: String): CachedPlaybackSession? {
        val current = sessionStore.get(sessionId) ?: return get(sessionId)
        val key = preferences.apiKey.value.trim()
        val refreshedUrl = torBoxClient.requestDownloadLink(key, current.torrentId, current.fileId)
            ?: return null
        val refreshed = current.copy(url = refreshedUrl)
        sessionStore.replace(sessionId, refreshed)
        return refreshed
    }

    fun remove(sessionId: String) {
        sessionStore.remove(sessionId)
    }
}

@Composable
@androidx.annotation.OptIn(UnstableApi::class)
fun CachedVideoPlayerScreen(
    sessionId: String,
    onBackPressed: () -> Unit,
    viewModel: CachedVideoPlayerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var session by remember(sessionId) { mutableStateOf(viewModel.get(sessionId)) }
    val player = rememberPlayer(context)
    var hasError by remember(sessionId) { mutableStateOf(session == null) }
    var errorMessage by remember(sessionId) {
        mutableStateOf(
            if (sessionId.startsWith("/") || sessionId.contains(".")) {
                "Unable to open the local video file"
            } else {
                "Unable to open the cached TorBox file"
            }
        )
    }
    var retryUsed by remember(sessionId) { mutableStateOf(false) }
    var isRefreshing by remember(sessionId) { mutableStateOf(false) }
    var refreshAttempted by remember(sessionId) { mutableStateOf(false) }
    var bytesLoaded by remember(sessionId) { mutableStateOf(0L) }
    var bufferMs by remember(sessionId) { mutableStateOf(0L) }
    var isPlaying by remember(player) { mutableStateOf(player.isPlaying) }
    var audioTracks by remember(player) { mutableStateOf<List<Pair<Tracks.Group, Int>>>(emptyList()) }
    var selectedAudioIndex by remember(player) { mutableStateOf(0) }
    var videoQuality by remember(player) { mutableStateOf("Original") }

    fun isExpiredLinkError(error: androidx.media3.common.PlaybackException): Boolean {
        val details = buildString {
            append(error.message.orEmpty())
            append(' ')
            append(error.cause?.message.orEmpty())
            append(' ')
            append(error.cause?.javaClass?.name.orEmpty())
        }.lowercase()
        return listOf(
            "401", "403", "404", "unauthorized", "forbidden", "expired",
            "invalidresponsecodeexception"
        ).any(details::contains)
    }

    BackHandler(onBack = onBackPressed)

    DisposableEffect(sessionId) {
        onDispose { viewModel.remove(sessionId) }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                hasError = true
                errorMessage = error.localizedMessage ?: "The cached TorBox link could not be played"
                if (!refreshAttempted && session != null && session?.torrentId != 0 && !isRefreshing && isExpiredLinkError(error)) {
                    refreshAttempted = true
                    isRefreshing = true
                    coroutineScope.launch {
                        val refreshed = viewModel.refresh(sessionId)
                        isRefreshing = false
                        if (refreshed != null) {
                            session = refreshed
                            hasError = false
                            errorMessage = ""
                        } else {
                            errorMessage = "The cached TorBox link expired and could not be refreshed"
                        }
                    }
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onTracksChanged(tracks: Tracks) {
                val audios = mutableListOf<Pair<Tracks.Group, Int>>()
                var quality = 0
                tracks.groups.forEach { group ->
                    when (group.type) {
                        C.TRACK_TYPE_AUDIO -> {
                            for (index in 0 until group.length) {
                                audios += group to index
                                if (group.isTrackSelected(index)) {
                                    selectedAudioIndex = audios.lastIndex
                                }
                            }
                        }
                        C.TRACK_TYPE_VIDEO -> {
                            for (index in 0 until group.length) {
                                quality = maxOf(quality, group.getTrackFormat(index).height)
                            }
                        }
                    }
                }
                audioTracks = audios
                if (quality > 0) videoQuality = "${quality}p"
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    DisposableEffect(player) {
        val analyticsListener = object : androidx.media3.exoplayer.analytics.AnalyticsListener {
            override fun onLoadCompleted(
                eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                loadEventInfo: androidx.media3.exoplayer.source.LoadEventInfo,
                mediaLoadData: androidx.media3.exoplayer.source.MediaLoadData
            ) {
                bytesLoaded += loadEventInfo.bytesLoaded.coerceAtLeast(0L)
            }
        }
        player.addAnalyticsListener(analyticsListener)
        onDispose { player.removeAnalyticsListener(analyticsListener) }
    }

    LaunchedEffect(hasError, session) {
        while (!hasError && session != null) {
            bufferMs = player.totalBufferedDuration.coerceAtLeast(0L)
            kotlinx.coroutines.delay(1000)
        }
    }

    LaunchedEffect(session) {
        session?.let {
            player.setMediaItem(MediaItem.fromUri(it.url))
            player.prepare()
            player.playWhenReady = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        if (session == null || hasError) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = Color.White)
                Text(errorMessage, color = Color.White)
                if (isRefreshing) {
                    Text("Refreshing the expired TorBox link…", color = Color.White)
                } else if (!retryUsed && session != null) {
                    Button(
                        onClick = {
                            retryUsed = true
                            bytesLoaded = 0L
                            coroutineScope.launch {
                                isRefreshing = true
                                val refreshed = viewModel.refresh(sessionId)
                                isRefreshing = false
                                if (refreshed == null) {
                                    hasError = true
                                    errorMessage = "TorBox could not refresh this cached link"
                                } else {
                                    session = refreshed
                                    refreshAttempted = true
                                    hasError = false
                                }
                            }
                        },
                        colors = ButtonDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("Retry cached link")
                    }
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                PlayerSurface(
                    player = player,
                    surfaceType = SURFACE_TYPE_SURFACE_VIEW,
                    modifier = Modifier.fillMaxSize()
                )
                Column(modifier = Modifier.align(Alignment.BottomStart)) {
                    Text(
                        text = "${session?.title.orEmpty()}  •  Quality $videoQuality  •  Data ${formatBytes(bytesLoaded)}  •  Buffer ${bufferMs} ms",
                        color = Color.White
                    )
                    Row {
                        Button(onClick = {
                            player.seekTo((player.currentPosition - 10_000L).coerceAtLeast(0L))
                        }) { Text("-10s") }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = {
                            if (player.isPlaying) player.pause() else player.play()
                        }) { Text(if (isPlaying) "Pause" else "Play") }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = {
                            player.seekTo((player.currentPosition + 10_000L).coerceAtMost(player.duration.coerceAtLeast(0L)))
                        }) { Text("+10s") }
                        if (audioTracks.size > 1) {
                            Spacer(Modifier.width(8.dp))
                            Button(onClick = {
                                val nextIndex = (selectedAudioIndex + 1) % audioTracks.size
                                val (group, trackIndex) = audioTracks[nextIndex]
                                player.trackSelectionParameters = player.trackSelectionParameters
                                    .buildUpon()
                                    .setOverrideForType(
                                        TrackSelectionOverride(group.mediaTrackGroup, listOf(trackIndex))
                                    )
                                    .build()
                                selectedAudioIndex = nextIndex
                            }) {
                                Text("Audio ${selectedAudioIndex + 1}/${audioTracks.size}")
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var index = -1
    while (value >= 1024 && index < units.lastIndex) {
        value /= 1024
        index++
    }
    return "%.2f %s".format(java.util.Locale.US, value, units[index])
}
