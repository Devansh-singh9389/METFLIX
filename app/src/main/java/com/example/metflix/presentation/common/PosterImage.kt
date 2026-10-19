/*
 * Copyright 2024 Google LLC
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

package com.example.metflix.presentation.common

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import com.example.metflix.data.entities.Movie
import com.example.metflix.data.settings.PerformancePreferences
import com.example.metflix.data.util.StringConstants

@Composable
fun PosterImage(
    movie: Movie,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val isHighQuality = PerformancePreferences.isHighQualityImagesActive
    val imageRequest = remember(movie.posterUri, isHighQuality) {
        ImageRequest.Builder(context)
            .data(movie.posterUri)
            .bitmapConfig(if (isHighQuality) Bitmap.Config.ARGB_8888 else Bitmap.Config.RGB_565)
            .allowRgb565(!isHighQuality)
            .size(if (isHighQuality) 400 else 180, if (isHighQuality) 600 else 270)
            .precision(Precision.INEXACT)
            .crossfade(false)
            .build()
    }

    AsyncImage(
        modifier = modifier,
        model = imageRequest,
        contentDescription = StringConstants.Composable.ContentDescription.moviePoster(movie.name),
        contentScale = ContentScale.Crop,
        clipToBounds = true
    )
}
