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

package com.example.metflix.presentation.screens.anime.category

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.metflix.data.entities.Movie
import com.example.metflix.data.repositories.AnimeDataSource
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Immutable
sealed interface AnimeCategoryUiState {
    data object Loading : AnimeCategoryUiState
    data object Empty : AnimeCategoryUiState
    @Immutable
    data class Error(val message: String) : AnimeCategoryUiState
    @Immutable
    data class Ready(
        val title: String,
        val categoryType: String,
        val slug: String,
        val movies: List<Movie>
    ) : AnimeCategoryUiState
}

@HiltViewModel
class AnimeCategoryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val animeDataSource: AnimeDataSource
) : ViewModel() {

    private val categoryType: String = savedStateHandle.get<String>("categoryType").orEmpty()
    private val slug: String = savedStateHandle.get<String>("slug").orEmpty()

    private val _uiState = MutableStateFlow<AnimeCategoryUiState>(AnimeCategoryUiState.Loading)
    val uiState: StateFlow<AnimeCategoryUiState> = _uiState.asStateFlow()

    init {
        loadCategory()
    }

    fun retry() {
        _uiState.value = AnimeCategoryUiState.Loading
        loadCategory(forceRefresh = true)
    }

    fun loadCategory(forceRefresh: Boolean = false) {
        if (categoryType.isBlank() || slug.isBlank()) {
            _uiState.value = AnimeCategoryUiState.Error("Invalid category parameter")
            return
        }

        viewModelScope.launch {
            val result = animeDataSource.getCategoryData(categoryType, slug, page = 1, forceRefresh = forceRefresh)
            result.onSuccess { response ->
                val movies = response.items.map { it.toMovie() }
                val displayTitle = response.title ?: response.category ?: slug.replace("-", " ")
                    .split(" ")
                    .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }

                if (movies.isEmpty()) {
                    _uiState.value = AnimeCategoryUiState.Empty
                } else {
                    _uiState.value = AnimeCategoryUiState.Ready(
                        title = displayTitle,
                        categoryType = categoryType,
                        slug = slug,
                        movies = movies
                    )
                }
            }.onFailure { err ->
                _uiState.value = AnimeCategoryUiState.Error(err.message ?: "Failed to load category titles")
            }
        }
    }
}
