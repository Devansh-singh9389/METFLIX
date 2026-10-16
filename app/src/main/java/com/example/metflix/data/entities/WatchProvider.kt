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

package com.example.metflix.data.entities

import androidx.compose.runtime.Immutable

@Immutable
data class WatchProvider(
    val id: Int,
    val name: String,
    val logoUri: String,
    val type: WatchOptionType,
    val appPackage: String? = null
)

enum class WatchOptionType {
    Stream,
    Rent,
    Buy,
    Free
}

@Immutable
data class WatchProvidersInfo(
    val tmdbLink: String? = null,
    val countryCode: String = "IN",
    val streamProviders: List<WatchProvider> = emptyList(),
    val rentProviders: List<WatchProvider> = emptyList(),
    val buyProviders: List<WatchProvider> = emptyList(),
    val freeProviders: List<WatchProvider> = emptyList(),
    val attribution: String = "Streaming data powered by JustWatch"
) {
    val hasProviders: Boolean
        get() = streamProviders.isNotEmpty() ||
                rentProviders.isNotEmpty() ||
                buyProviders.isNotEmpty() ||
                freeProviders.isNotEmpty()
}
