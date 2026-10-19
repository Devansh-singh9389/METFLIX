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

package com.example.metflix.presentation.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.metflix.presentation.screens.categories.CategoryMovieListScreen
import com.example.metflix.presentation.screens.livetv.player.LiveTvPlayerScreen
import com.example.metflix.presentation.screens.movies.MovieDetailsScreen
import com.example.metflix.presentation.screens.videoPlayer.VideoPlayerScreen

enum class Screens(
    private val args: List<String>? = null,
    val isTabItem: Boolean = false,
    val tabIcon: ImageVector? = null,
    val tabTitle: String? = null
) {
    Profile(isTabItem = true, tabTitle = "Settings"),
    Home(isTabItem = true),
    Movies(isTabItem = true),
    Shows(isTabItem = true),
    Anime(isTabItem = true, tabTitle = "Anime"),
    LiveTv(isTabItem = true, tabTitle = "Live TV"),
    Categories(isTabItem = true),
    Favourites(isTabItem = true),
    Search(isTabItem = true, tabIcon = Icons.Default.Search),
    CategoryMovieList(listOf(CategoryMovieListScreen.CategoryIdBundleKey)),
    MovieDetails(listOf(MovieDetailsScreen.MovieIdBundleKey)),
    Dashboard,
    VideoPlayer(listOf(VideoPlayerScreen.MovieIdBundleKey)),
    CachedVideoPlayer(listOf("sessionId")),
    LiveTvPlayer(listOf(LiveTvPlayerScreen.ChannelIdBundleKey));

    operator fun invoke(): String {
        val argList = StringBuilder()
        args?.let { nnArgs ->
            nnArgs.forEach { arg -> argList.append("/{$arg}") }
        }
        return name + argList
    }

    fun withArgs(vararg args: Any): String {
        val destination = StringBuilder()
        args.forEach { arg -> destination.append("/$arg") }
        return name + destination
    }
}
