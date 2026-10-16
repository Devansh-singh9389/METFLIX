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

package com.example.metflix.data.network.tmdb

/**
 * Configuration for The Movie Database (TMDB) API integration.
 *
 * To get a free API key:
 * 1. Sign up at https://www.themoviedb.org/signup
 * 2. Go to Settings -> API and generate a free API Key.
 * 3. Paste your key in [API_KEY] below.
 */
object TmdbConfig {
    /**
     * Set to true to activate live TMDB fetching.
     * When true and [API_KEY] is configured, MetFlix pulls real-time movies, TV shows,
     * categories, posters, and cast from TMDB.
     * If the API key is empty or network fails, it automatically falls back to bundled asset data.
     */
    var USE_TMDB: Boolean = true

    /**
     * Paste your TMDB v3 API Key here.
     */
    var API_KEY: String = "6eba1b274252e8a563ebdaf9abacbf36"

    /**
     * TMDB Base URL
     */
    const val BASE_URL: String = "https://api.themoviedb.org/3"

    /**
     * Image CDN paths
     */
    const val IMAGE_BASE_URL_POSTER: String = "https://image.tmdb.org/t/p/w500"
    const val IMAGE_BASE_URL_BACKDROP: String = "https://image.tmdb.org/t/p/w780"

    /**
     * Default video and subtitle stream URLs for playback in ExoPlayer.
     */
    var DEFAULT_STREAM_URL: String =
        "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"

    var DEFAULT_SUBTITLE_URL: String? =
        "https://thepaciellogroup.github.io/AT-browser-tests/video/subtitles-en.vtt"
}
