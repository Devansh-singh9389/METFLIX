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

import com.example.metflix.data.entities.Movie
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Thread-safe in-memory cache wrapper for data loaders.
 */
internal class CachedDataReader<T>(private val reader: suspend () -> List<T>) {
    private val mutex = Mutex()
    private var cache: List<T>? = null

    suspend fun read(forceRefresh: Boolean = false): List<T> {
        mutex.withLock {
            if (cache == null || forceRefresh) {
                cache = reader()
            }
        }
        return cache ?: emptyList()
    }

    suspend fun update(newData: List<T>) {
        mutex.withLock {
            cache = newData
        }
    }

    suspend fun clear() {
        mutex.withLock {
            cache = null
        }
    }
}

internal typealias MovieDataReader = CachedDataReader<Movie>
