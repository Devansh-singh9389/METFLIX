/*
 * Copyright 2023 Google LLC
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

package com.example.metflix.presentation.screens.profile

import android.widget.Toast
import androidx.annotation.FloatRange
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.example.metflix.R
import com.example.metflix.presentation.screens.dashboard.rememberChildPadding
import com.example.metflix.presentation.theme.MetFlixTheme

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import com.example.metflix.data.entities.Movie
import com.example.metflix.data.settings.DownloadPreferences
import com.example.metflix.data.settings.LanguagePreferences
import com.example.metflix.data.settings.PerformancePreferences
import com.example.metflix.data.settings.SearchHistoryPreferences
import com.example.metflix.data.settings.SubtitlePreferences

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ProfileScreen(
    @FloatRange(from = 0.0, to = 1.0)
    sidebarWidthFraction: Float = 0.32f,
    onPlayMovie: (Movie) -> Unit = {},
    onCachedPlaybackRequested: (String) -> Unit = {},
    onSearchQuerySelected: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val languagePreferences = remember { LanguagePreferences(context) }
    val subtitlePreferences = remember { SubtitlePreferences(context) }
    val performancePreferences = remember { PerformancePreferences(context) }
    val searchHistoryPreferences = remember { SearchHistoryPreferences(context) }
    val downloadPreferences = remember { DownloadPreferences(context) }

    val reduceAnimations by performancePreferences.reduceAnimations.collectAsStateWithLifecycle()
    val currentLanguageCode by languagePreferences.currentLanguage.collectAsStateWithLifecycle()
    val isSubtitlesChecked by subtitlePreferences.isSubtitlesEnabled.collectAsStateWithLifecycle()
    val preferredSubLanguage by subtitlePreferences.preferredLanguage.collectAsStateWithLifecycle()
    val searchHistory by searchHistoryPreferences.history.collectAsStateWithLifecycle()

    val childPadding = rememberChildPadding()
    var selectedScreen by rememberSaveable { mutableStateOf(ProfileScreens.Accounts) }

    val focusRequesters = remember { ProfileScreens.entries.associateWith { FocusRequester() } }
    val focusManager = LocalFocusManager.current
    var isLeftColumnFocused by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { focusRequesters[ProfileScreens.Accounts]?.requestFocus() }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = childPadding.start, vertical = childPadding.top)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(fraction = sidebarWidthFraction)
                .verticalScroll(rememberScrollState())
                .fillMaxHeight()
                .onFocusChanged {
                    isLeftColumnFocused = it.hasFocus
                }
                .focusRestorer()
                .focusGroup(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ProfileScreens.entries.forEach { profileScreen ->
                key(profileScreen) {
                    val requester = focusRequesters.getValue(profileScreen)
                    ListItem(
                        trailingContent = {
                            Icon(
                                profileScreen.icon,
                                modifier = Modifier
                                    .padding(vertical = 2.dp)
                                    .padding(start = 4.dp)
                                    .size(20.dp),
                                contentDescription = stringResource(
                                    id = R.string.profile_screen_listItem_icon_content_description,
                                    profileScreen.tabTitle
                                )
                            )
                        },
                        headlineContent = {
                            Text(
                                text = profileScreen.tabTitle,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Medium
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        },
                        selected = selectedScreen == profileScreen,
                        onClick = {
                            selectedScreen = profileScreen
                            focusManager.moveFocus(FocusDirection.Right)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(requester)
                            .onFocusChanged {
                                if (it.isFocused) {
                                    selectedScreen = profileScreen
                                }
                            },
                        scale = ListItemDefaults.scale(focusedScale = 1f),
                        colors = ListItemDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.inverseSurface,
                            selectedContainerColor = MaterialTheme.colorScheme.inverseSurface
                                .copy(alpha = 0.4f),
                            selectedContentColor = MaterialTheme.colorScheme.surface,
                        ),
                        shape = ListItemDefaults.shape(shape = MaterialTheme.shapes.extraSmall)
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .onPreviewKeyEvent {
                    if (it.key == Key.Back && it.type == KeyEventType.KeyUp) {
                        if (!isLeftColumnFocused) {
                            focusRequesters[selectedScreen]?.requestFocus()
                            return@onPreviewKeyEvent true
                        }
                    }
                    false
                }
        ) {
            Crossfade(
                targetState = selectedScreen,
                animationSpec = if (reduceAnimations) snap() else tween(durationMillis = 150),
                label = "ProfilePaneCrossfade"
            ) { screen ->
                when (screen) {
                    ProfileScreens.Accounts -> AccountsSection(
                        onOpenPerformanceSettings = { selectedScreen = ProfileScreens.Performance },
                        onOpenDebridSettings = { selectedScreen = ProfileScreens.Debrid }
                    )
                    ProfileScreens.Debrid -> DebridSection(
                        onCachedPlaybackRequested = onCachedPlaybackRequested
                    )
                    ProfileScreens.Performance -> PerformanceSection()
                    ProfileScreens.Downloads -> DownloadsSection(downloadPreferences = downloadPreferences)
                    ProfileScreens.About -> AboutSection()
                    ProfileScreens.Subtitles -> SubtitlesSection(
                        isSubtitlesChecked = isSubtitlesChecked,
                        onSubtitleCheckChange = { subtitlePreferences.setSubtitlesEnabled(it) },
                        preferredLanguage = preferredSubLanguage,
                        onPreferredLanguageChange = { subtitlePreferences.setPreferredLanguage(it) }
                    )
                    ProfileScreens.Language -> LanguageSection(
                        currentLanguageCode = currentLanguageCode,
                        onLanguageSelected = { languagePreferences.setLanguage(it) }
                    )
                    ProfileScreens.SearchHistory -> SearchHistorySection(
                        history = searchHistory,
                        onClearAll = { searchHistoryPreferences.clearHistory() },
                        onRemoveQuery = { searchHistoryPreferences.removeQuery(it) },
                        onSelectQuery = { query ->
                            onSearchQuerySelected(query)
                            Toast.makeText(context, "Searching: $query", Toast.LENGTH_SHORT).show()
                        }
                    )
                    ProfileScreens.HelpAndSupport -> HelpAndSupportSection()
                }
            }
        }
    }
}

@Preview(device = Devices.TV_1080p)
@Composable
fun ProfileScreenPreview() {
    MetFlixTheme {
        Box(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
            ProfileScreen()
        }
    }
}
