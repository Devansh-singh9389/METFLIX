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

package com.example.metflix.presentation.screens.shows

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.metflix.data.entities.MovieList
import com.example.metflix.data.repositories.MovieRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

@HiltViewModel
class ShowScreenViewModel @Inject constructor(
    private val movieRepository: MovieRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ShowScreenUiState>(ShowScreenUiState.Loading)
    val uiState: StateFlow<ShowScreenUiState> = _uiState.asStateFlow()

    private var bingeWatchDramaList: MovieList = emptyList()
    private var tvShowList: MovieList = emptyList()

    init {
        loadData()
    }

    private fun emitState() {
        _uiState.value = ShowScreenUiState.Ready(
            bingeWatchDramaList = bingeWatchDramaList,
            tvShowList = tvShowList
        )
    }

    private fun loadData() {
        viewModelScope.launch(Dispatchers.IO) {
            launch {
                movieRepository.getTVShows()
                    .catch { emit(emptyList()) }
                    .collect { list ->
                        tvShowList = list
                        emitState()
                    }
            }

            launch {
                movieRepository.getBingeWatchDramas()
                    .catch { emit(emptyList()) }
                    .collect { list ->
                        bingeWatchDramaList = list
                        emitState()
                    }
            }
        }
    }
}

@Immutable
sealed interface ShowScreenUiState {
    data object Loading : ShowScreenUiState
    @Immutable
    data class Ready(
        val bingeWatchDramaList: MovieList,
        val tvShowList: MovieList
    ) : ShowScreenUiState
}
