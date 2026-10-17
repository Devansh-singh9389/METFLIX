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

package com.example.metflix.presentation.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import android.graphics.Bitmap
import androidx.compose.ui.platform.LocalContext
import androidx.tv.material3.Carousel
import androidx.tv.material3.CarouselDefaults
import androidx.tv.material3.CarouselState
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.ShapeDefaults
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import com.example.metflix.R
import com.example.metflix.data.entities.Movie
import com.example.metflix.data.util.StringConstants
import com.example.metflix.presentation.theme.MetFlixBorderWidth
import com.example.metflix.presentation.theme.MetFlixButtonShape
import com.example.metflix.presentation.utils.Padding
import com.example.metflix.presentation.utils.handleDPadKeyEvents

@OptIn(ExperimentalTvMaterial3Api::class)
val CarouselSaver = Saver<CarouselState, Int>(
    save = { it.activeItemIndex },
    restore = { CarouselState(it) }
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun FeaturedMoviesCarousel(
    movies: List<Movie>,
    padding: Padding,
    goToVideoPlayer: (movie: Movie) -> Unit,
    modifier: Modifier = Modifier
) {
    val carouselState = rememberSaveable(saver = CarouselSaver) { CarouselState(0) }
    var isCarouselFocused by remember { mutableStateOf(false) }
    val alpha = if (isCarouselFocused) {
        1f
    } else {
        0f
    }

    Carousel(
        modifier = modifier
            .padding(start = padding.start, end = padding.start, top = padding.top)
            .border(
                width = MetFlixBorderWidth,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                shape = ShapeDefaults.Medium,
            )
            .clip(ShapeDefaults.Medium)
            .onFocusChanged {
                // Because the carousel itself never gets the focus
                isCarouselFocused = it.hasFocus
            }
            .semantics {
                contentDescription =
                    StringConstants.Composable.ContentDescription.MoviesCarousel
            }
            .handleDPadKeyEvents(onEnter = {
                goToVideoPlayer(movies[carouselState.activeItemIndex])
            }),
        itemCount = movies.size,
        carouselState = carouselState,
        autoScrollDurationMillis = if (isCarouselFocused && !com.example.metflix.data.settings.PerformancePreferences.isLowSpecModeActive) 8000L else Long.MAX_VALUE,
        carouselIndicator = {
            CarouselIndicator(
                itemCount = movies.size,
                activeItemIndex = carouselState.activeItemIndex
            )
        },
        contentTransformStartToEnd = fadeIn(tween(durationMillis = 350))
            .togetherWith(fadeOut(tween(durationMillis = 350))),
        contentTransformEndToStart = fadeIn(tween(durationMillis = 350))
            .togetherWith(fadeOut(tween(durationMillis = 350))),
        content = { index ->
            val movie = movies[index]
            // background
            CarouselItemBackground(movie = movie, modifier = Modifier.fillMaxSize())
            // foreground
            CarouselItemForeground(
                movie = movie,
                isCarouselFocused = isCarouselFocused,
                modifier = Modifier.fillMaxSize()
            )
        }
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun BoxScope.CarouselIndicator(
    itemCount: Int,
    activeItemIndex: Int,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .padding(32.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
            .graphicsLayer {
                clip = true
                shape = ShapeDefaults.ExtraSmall
            }
            .align(Alignment.BottomEnd)
    ) {
        CarouselDefaults.IndicatorRow(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp),
            itemCount = itemCount,
            activeItemIndex = activeItemIndex,
        )
    }
}

@Composable
private fun CarouselItemForeground(
    movie: Movie,
    modifier: Modifier = Modifier,
    isCarouselFocused: Boolean = false
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.BottomStart
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Bottom
        ) {
            Text(
                text = movie.name,
                style = MaterialTheme.typography.displayMedium.copy(
                    shadow = Shadow(
                        color = Color.Black.copy(alpha = 0.5f),
                        offset = Offset(x = 2f, y = 4f),
                        blurRadius = 2f
                    )
                ),
                maxLines = 1
            )
            Text(
                text = movie.description,
                style = MaterialTheme.typography.titleMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface.copy(
                        alpha = 0.65f
                    ),
                    shadow = Shadow(
                        color = Color.Black.copy(alpha = 0.5f),
                        offset = Offset(x = 2f, y = 4f),
                        blurRadius = 2f
                    )
                ),
                maxLines = 1,
                modifier = Modifier.padding(top = 8.dp)
            )
            AnimatedVisibility(
                visible = isCarouselFocused,
                content = {
                    WatchNowButton()
                }
            )
        }
    }
}

private val carouselBackgroundGradient = Brush.verticalGradient(
    colors = listOf(
        Color.Transparent,
        Color.Black.copy(alpha = 0.5f)
    )
)

@Composable
private fun CarouselItemBackground(movie: Movie, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val isHighQuality = com.example.metflix.data.settings.PerformancePreferences.isHighQualityImagesActive
    val imageRequest = remember(movie.posterUri, isHighQuality) {
        ImageRequest.Builder(context)
            .data(movie.posterUri)
            .bitmapConfig(if (isHighQuality) Bitmap.Config.ARGB_8888 else Bitmap.Config.RGB_565)
            .allowRgb565(!isHighQuality)
            .size(if (isHighQuality) 1920 else 960, if (isHighQuality) 1080 else 540)
            .precision(Precision.INEXACT)
            .crossfade(false)
            .build()
    }
    AsyncImage(
        model = imageRequest,
        contentDescription = StringConstants
            .Composable
            .ContentDescription
            .moviePoster(movie.name),
        modifier = modifier
            .drawWithContent {
                drawContent()
                drawRect(carouselBackgroundGradient)
            },
        contentScale = ContentScale.Crop
    )
}

@Composable
private fun WatchNowButton() {
    Button(
        onClick = {},
        modifier = Modifier.padding(top = 8.dp),
        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
        shape = ButtonDefaults.shape(shape = MetFlixButtonShape),
        colors = ButtonDefaults.colors(
            containerColor = MaterialTheme.colorScheme.onSurface,
            contentColor = MaterialTheme.colorScheme.surface,
            focusedContentColor = MaterialTheme.colorScheme.surface,
        ),
        scale = ButtonDefaults.scale(scale = 1f)
    ) {
        Icon(
            imageVector = Icons.Outlined.PlayArrow,
            contentDescription = null,
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = stringResource(R.string.watch_now),
            style = MaterialTheme.typography.titleSmall
        )
    }
}
