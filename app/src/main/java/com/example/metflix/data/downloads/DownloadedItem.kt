/*
 * Copyright 2026 Google LLC
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

package com.example.metflix.data.downloads

import androidx.annotation.Keep

@Keep
data class DownloadedItem(
    val id: String,
    val title: String,
    val mediaType: String,
    val mediaId: String,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val localFilePath: String,
    val downloadId: Long = -1L,
    val quality: String = "1080p",
    val languages: List<String> = emptyList(),
    val timestamp: Long = System.currentTimeMillis(),
    val posterUrl: String = "",
    val catalogJson: String = ""
)
