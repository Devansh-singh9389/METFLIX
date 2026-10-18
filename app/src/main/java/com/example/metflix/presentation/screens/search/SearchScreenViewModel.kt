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

package com.example.metflix.presentation.screens.search

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.metflix.data.entities.MovieList
import com.example.metflix.data.repositories.MovieRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class SearchUiState(
    val query: String = "",
    val isSearching: Boolean = false,
    val searchResults: MovieList = emptyList(),
    val popularMovies: MovieList = emptyList()
)

@Immutable
sealed interface SearchState {
    data object Searching : SearchState
    @Immutable
    data class Done(val movieList: MovieList) : SearchState
}

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchScreenViewModel @Inject constructor(
    private val movieRepository: MovieRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    val searchState: StateFlow<SearchState> = _uiState.map { state ->
        if (state.isSearching) SearchState.Searching
        else SearchState.Done(state.searchResults)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = SearchState.Done(emptyList())
    )

    init {
        // Load popular movies for recommendations when query is blank
        viewModelScope.launch {
            movieRepository.getPopularFilmsThisWeek()
                .catch { emit(emptyList()) }
                .collect { list ->
                    _uiState.value = _uiState.value.copy(popularMovies = list)
                }
        }

        // Live debounced search
        viewModelScope.launch {
            _searchQuery
                .debounce(350)
                .distinctUntilChanged()
                .collectLatest { query ->
                    executeSearch(query)
                }
        }
    }

    fun onQueryChange(newQuery: String) {
        _searchQuery.value = newQuery
        _uiState.value = _uiState.value.copy(query = newQuery)
        if (newQuery.isBlank()) {
            _uiState.value = _uiState.value.copy(
                isSearching = false,
                searchResults = emptyList()
            )
        }
    }

    fun submitQuery(queryString: String) {
        _searchQuery.value = queryString
        _uiState.value = _uiState.value.copy(query = queryString)
        viewModelScope.launch {
            executeSearch(queryString)
        }
    }

    fun query(queryString: String) {
        submitQuery(queryString)
    }

    private suspend fun executeSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                isSearching = false,
                searchResults = emptyList()
            )
            return
        }

        _uiState.value = _uiState.value.copy(isSearching = true)
        val results = try {
            movieRepository.searchMovies(query = trimmed)
        } catch (e: Exception) {
            emptyList()
        }
        _uiState.value = _uiState.value.copy(
            isSearching = false,
            searchResults = results
        )
    }
}
