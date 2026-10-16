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

package com.example.metflix.data.models

import kotlinx.serialization.Serializable

@Serializable
data class MoviesResponseItem(
    val id: String = "",
    val videoUri: String = "",
    val subtitleUri: String? = null,
    val rank: Int = 0,
    val rankUpDown: String = "",
    val title: String = "",
    val fullTitle: String = "",
    val year: Int = 0,
    val releaseDate: String = "",
    val image_16_9: String = "",
    val image_2_3: String = "",
    val runtimeMins: Int = 0,
    val runtimeStr: String = "",
    val plot: String = "",
    val contentRating: String = "",
    val rating: Float = 0f,
    val ratingCount: Int = 0,
    val metaCriticRating: Int = 0,
    val genres: String = "",
    val directors: String = "",
    val stars: String = "",
)
