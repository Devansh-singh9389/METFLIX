/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.metflix.presentation.screens.videoPlayer.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesomeMotion
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.example.metflix.data.entities.MovieDetails
import com.example.metflix.data.util.StringConstants

@Composable
fun VideoPlayerControls(
    player: Player,
    movieDetails: MovieDetails,
    focusRequester: FocusRequester,
    isSubtitlesEnabled: Boolean = false,
    onShowSettings: () -> Unit = {},
    onShowPlaylist: () -> Unit = {},
    onToggleSubtitles: () -> Unit = {},
    onShowControls: () -> Unit = {},
) {
    var isPlaying by remember(player) { mutableStateOf(player.isPlaying) }
    var currentMediaIndex by remember(player) { mutableIntStateOf(player.currentMediaItemIndex) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                currentMediaIndex = player.currentMediaItemIndex
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
        }
    }

    val actionButtonsFocusRequester = remember { FocusRequester() }

    // Dynamic title and metadata based on playlist index
    val (displayTitle, displaySecondary, displayTertiary) = remember(currentMediaIndex, movieDetails) {
        if (currentMediaIndex == 0 || movieDetails.similarMovies.isEmpty()) {
            Triple(movieDetails.name, movieDetails.releaseDate, movieDetails.director)
        } else {
            val similar = movieDetails.similarMovies.getOrNull(currentMediaIndex - 1)
            Triple(
                similar?.name ?: movieDetails.name,
                movieDetails.releaseDate,
                "More Like This"
            )
        }
    }

    VideoPlayerMainFrame(
        mediaTitle = {
            VideoPlayerMediaTitle(
                title = displayTitle,
                secondaryText = displaySecondary,
                tertiaryText = displayTertiary,
                type = VideoPlayerMediaTitleType.DEFAULT
            )
        },
        mediaActions = {
            Row(
                modifier = Modifier
                    .padding(bottom = 16.dp)
                    .focusProperties {
                        down = focusRequester
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PreviousButton(
                    player = player,
                    onShowControls = onShowControls,
                    modifier = Modifier.focusRequester(actionButtonsFocusRequester)
                )
                NextButton(
                    player = player,
                    onShowControls = onShowControls,
                    onClick = {
                        if (player.hasNextMediaItem()) {
                            player.seekToNextMediaItem()
                            player.play()
                        } else {
                            onShowPlaylist()
                        }
                    }
                )
                RepeatButton(
                    player = player,
                    onShowControls = onShowControls,
                )
                VideoPlayerControlsIcon(
                    icon = Icons.Default.AutoAwesomeMotion,
                    isPlaying = isPlaying,
                    contentDescription =
                    StringConstants.Composable.VideoPlayerControlPlaylistButton,
                    onShowControls = onShowControls,
                    onClick = onShowPlaylist
                )
                VideoPlayerControlsIcon(
                    icon = Icons.Default.ClosedCaption,
                    isPlaying = isPlaying,
                    isActive = isSubtitlesEnabled,
                    contentDescription =
                    StringConstants.Composable.VideoPlayerControlClosedCaptionsButton,
                    onShowControls = onShowControls,
                    onClick = onToggleSubtitles
                )
                VideoPlayerControlsIcon(
                    icon = Icons.Default.Settings,
                    isPlaying = isPlaying,
                    contentDescription =
                    StringConstants.Composable.VideoPlayerControlSettingsButton,
                    onShowControls = onShowControls,
                    onClick = onShowSettings
                )
            }
        },
        seeker = {
            VideoPlayerSeeker(
                player = player,
                focusRequester = focusRequester,
                actionButtonsFocusRequester = actionButtonsFocusRequester,
                onShowControls = onShowControls,
            )
        },
        more = null
    )
}
