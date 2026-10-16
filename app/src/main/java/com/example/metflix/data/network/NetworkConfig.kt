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

package com.example.metflix.data.network

/**
 * Global Network Configuration for MetFlix backend endpoints.
 *
 * You can configure [BASE_URL] and any of the endpoint paths below to point
 * to your backend server (e.g., https://your-api.com/ or http://10.0.2.2:8000/).
 */
object NetworkConfig {
    /**
     * Set to true to fetch data dynamically from your remote [BASE_URL].
     * Set to false to use bundled local asset data.
     */
    var USE_REMOTE_BACKEND: Boolean = false

    /**
     * Your backend base URL. Include trailing slash (e.g. "https://api.example.com/").
     */
    var BASE_URL: String = "https://api.example.com/"

    /**
     * Custom endpoint paths. You can modify these to match your backend API structure.
     */
    var MOVIES_ENDPOINT: String = "movies"
    var FEATURED_MOVIES_ENDPOINT: String = "movies/featured"
    var TRENDING_MOVIES_ENDPOINT: String = "movies/trending"
    var TOP10_MOVIES_ENDPOINT: String = "movies/top10"
    var NOW_PLAYING_ENDPOINT: String = "movies/now_playing"
    var TV_SHOWS_ENDPOINT: String = "shows"
    var CATEGORIES_ENDPOINT: String = "categories"
    var CAST_ENDPOINT: String = "cast"
    var SEARCH_ENDPOINT: String = "search" // query appended as ?q={query}

    /**
     * Custom headers to send with requests (e.g., Authorization tokens, API keys).
     */
    val CUSTOM_HEADERS: MutableMap<String, String> = mutableMapOf(
        "Accept" to "application/json",
        "User-Agent" to "MetFlix-AndroidTV/1.0"
    )
}
