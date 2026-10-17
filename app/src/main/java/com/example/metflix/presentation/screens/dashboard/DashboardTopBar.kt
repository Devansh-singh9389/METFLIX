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

package com.example.metflix.presentation.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Tab
import androidx.tv.material3.TabRow
import androidx.tv.material3.Text
import com.example.metflix.R
import com.example.metflix.data.util.StringConstants
import com.example.metflix.presentation.screens.Screens
import com.example.metflix.presentation.theme.IconSize
import com.example.metflix.presentation.theme.MetFlixCardShape
import com.example.metflix.presentation.theme.LexendExa
import com.example.metflix.presentation.utils.occupyScreenSize

val TopBarTabs = Screens.entries.toList().filter { it.isTabItem }

// +1 for ProfileTab
val TopBarFocusRequesters = List(size = TopBarTabs.size + 1) { FocusRequester() }

private const val PROFILE_SCREEN_INDEX = -1

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DashboardTopBar(
    modifier: Modifier = Modifier,
    selectedTabIndex: Int,
    screens: List<Screens> = TopBarTabs,
    focusRequesters: List<FocusRequester> = remember { TopBarFocusRequesters },
    onScreenSelection: (screen: Screens) -> Unit
) {
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()
    var pendingTabSwitchJob by remember { mutableStateOf<Job?>(null) }
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
                .background(MaterialTheme.colorScheme.surface)
                .focusRestorer(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            UserAvatar(
                modifier = Modifier
                    .size(32.dp)
                    .focusRequester(focusRequesters.getOrNull(0) ?: remember { FocusRequester() })
                    .semantics {
                        contentDescription =
                            StringConstants.Composable.ContentDescription.UserAvatar
                    },
                selected = selectedTabIndex == PROFILE_SCREEN_INDEX,
                onClick = {
                    onScreenSelection(Screens.Profile)
                }
            )
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                var isTabRowFocused by remember { mutableStateOf(false) }
                var focusedTabIndex by remember(selectedTabIndex) { mutableIntStateOf(selectedTabIndex) }

                Spacer(modifier = Modifier.width(20.dp))
                TabRow(
                    modifier = Modifier
                        .onFocusChanged {
                            isTabRowFocused = it.isFocused || it.hasFocus
                        },
                    selectedTabIndex = if (isTabRowFocused) focusedTabIndex else selectedTabIndex,
                    indicator = { tabPositions, _ ->
                        val activeIndex = if (isTabRowFocused) focusedTabIndex else selectedTabIndex
                        if (activeIndex in tabPositions.indices) {
                            DashboardTopBarItemIndicator(
                                currentTabPosition = tabPositions[activeIndex],
                                anyTabFocused = isTabRowFocused,
                                shape = MetFlixCardShape
                            )
                        }
                    },
                    separator = { Spacer(modifier = Modifier) }
                ) {
                    screens.forEachIndexed { index, screen ->
                        key(index) {
                            Tab(
                                modifier = Modifier
                                    .height(32.dp)
                                    .focusRequester(focusRequesters.getOrNull(index + 1) ?: remember { FocusRequester() }),
                                selected = index == selectedTabIndex,
                                onFocus = {
                                    focusedTabIndex = index
                                    if (index != selectedTabIndex) {
                                        pendingTabSwitchJob?.cancel()
                                        pendingTabSwitchJob = coroutineScope.launch {
                                            delay(900)
                                            runCatching { onScreenSelection(screen) }
                                        }
                                    }
                                },
                                onClick = {
                                    pendingTabSwitchJob?.cancel()
                                    if (index != selectedTabIndex) {
                                        onScreenSelection(screen)
                                    }
                                    focusManager.moveFocus(FocusDirection.Down)
                                },
                            ) {
                                if (screen.tabIcon != null) {
                                    Icon(
                                        screen.tabIcon,
                                        modifier = Modifier.padding(4.dp),
                                        contentDescription = StringConstants.Composable
                                            .ContentDescription.DashboardSearchButton,
                                        tint = LocalContentColor.current
                                    )
                                } else {
                                    Text(
                                        modifier = Modifier
                                            .occupyScreenSize()
                                            .padding(horizontal = 16.dp),
                                        text = screen.tabTitle ?: screen(),
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            color = LocalContentColor.current
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            MetFlixLogo(
                modifier = Modifier
                    .alpha(0.75f)
                    .padding(end = 8.dp),
            )
        }
    }
}

@Composable
private fun MetFlixLogo(
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.PlayCircle,
            contentDescription = StringConstants.Composable
                .ContentDescription.BrandLogoImage,
            modifier = Modifier
                .padding(end = 4.dp)
                .size(IconSize)
        )
        Text(
            text = stringResource(R.string.brand_logo_text),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            fontFamily = LexendExa
        )
    }
}
