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

package com.example.metflix.data.repositories

import com.example.metflix.data.entities.MovieCast
import com.example.metflix.data.entities.toMovieCast
import com.example.metflix.data.network.MetFlixApiClient
import com.example.metflix.data.network.NetworkConfig
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MovieCastDataSource @Inject constructor(
    private val apiClient: MetFlixApiClient
) {

    private val movieCastDataReader = CachedDataReader {
        if (NetworkConfig.USE_REMOTE_BACKEND) {
            val remote = apiClient.getCast(NetworkConfig.CAST_ENDPOINT).getOrNull()
            if (!remote.isNullOrEmpty()) return@CachedDataReader remote.map { it.toMovieCast() }
        }
        emptyList()
    }

    suspend fun getMovieCastList(): List<MovieCast> = movieCastDataReader.read()
}
