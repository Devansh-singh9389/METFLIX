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

package com.example.metflix.presentation.screens.videoPlayer

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.metflix.data.entities.MovieDetails
import com.example.metflix.data.network.resolvers.ResolvedStream
import com.example.metflix.data.network.resolvers.StreamResolverRepository
import com.example.metflix.data.network.resolvers.StreamServer
import com.example.metflix.data.repositories.MovieRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class VideoPlayerScreenViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: MovieRepository,
    private val streamResolverRepository: StreamResolverRepository
) : ViewModel() {

    private val _availableServers = MutableStateFlow<List<StreamServer>>(streamResolverRepository.availableServers)
    val availableServers: List<StreamServer> get() = _availableServers.value

    private val _selectedServer = MutableStateFlow(streamResolverRepository.availableServers.first())
    val selectedServer: StateFlow<StreamServer> = _selectedServer.asStateFlow()

    private val _isResolvingStream = MutableStateFlow(false)
    val isResolvingStream: StateFlow<Boolean> = _isResolvingStream.asStateFlow()

    private val _resolvedStream = MutableStateFlow<ResolvedStream?>(null)
    val resolvedStream: StateFlow<ResolvedStream?> = _resolvedStream.asStateFlow()

    private val movieIdFlow = savedStateHandle.getStateFlow<String?>(VideoPlayerScreen.MovieIdBundleKey, null)
    private val movieDetailsFlow = MutableStateFlow<MovieDetails?>(null)
    private val isUserExplicitSelection = MutableStateFlow(false)
    private var raceJob: Job? = null

    init {
        viewModelScope.launch {
            movieIdFlow.collect { id ->
                if (!id.isNullOrBlank()) {
                    val details = repository.getMovieDetails(movieId = id)
                    movieDetailsFlow.value = details
                    startServerRace(details)
                }
            }
        }
    }

    private fun startServerRace(details: MovieDetails) {
        raceJob?.cancel()
        raceJob = viewModelScope.launch {
            _isResolvingStream.value = true
            isUserExplicitSelection.value = false

            streamResolverRepository.raceAndRankStreams(
                tmdbId = details.id,
                movieTitle = details.name,
                releaseYear = details.releaseDate.take(4),
                originalLanguage = details.originalLanguage
            ).collect { result ->
                _availableServers.value = result.rankedServers

                // If user hasn't explicitly selected another server, pit the fastest/best stream
                if (!isUserExplicitSelection.value && result.bestStream != null) {
                    _selectedServer.value = result.bestStream.server
                    _resolvedStream.value = result.bestStream
                }

                if (result.isFinal) {
                    _isResolvingStream.value = false
                }
            }
        }
    }

    private fun resolveCurrentStream(details: MovieDetails, server: StreamServer) {
        viewModelScope.launch {
            _isResolvingStream.value = true
            val resolved = streamResolverRepository.resolveMovieStream(
                tmdbId = details.id,
                movieTitle = details.name,
                releaseYear = details.releaseDate.take(4),
                server = server
            )
            _resolvedStream.value = resolved
            _isResolvingStream.value = false
        }
    }

    fun selectServer(server: StreamServer) {
        if (_selectedServer.value.id == server.id && _resolvedStream.value != null) return
        isUserExplicitSelection.value = true
        _selectedServer.value = server
        val details = movieDetailsFlow.value ?: return
        resolveCurrentStream(details, server)
    }

    fun retryNextServer(): StreamServer? {
        val currentList = _availableServers.value
        val currentIndex = currentList.indexOfFirst { it.id == _selectedServer.value.id }
        val nextIndex = if (currentIndex >= 0) (currentIndex + 1) % currentList.size else 0
        val nextServer = currentList[nextIndex]
        selectServer(nextServer)
        return nextServer
    }

    fun onCapturedDirectStream(directUrl: String, headers: Map<String, String>) {
        val current = _resolvedStream.value ?: return
        if (current.streamUrl != directUrl) {
            _resolvedStream.value = current.copy(
                streamUrl = directUrl,
                isHls = true,
                headers = headers
            )
        }
    }

    val uiState: StateFlow<VideoPlayerScreenUiState> = combine(
        movieDetailsFlow,
        _resolvedStream,
        _selectedServer,
        _isResolvingStream,
        _availableServers
    ) { details, resolved, currentServer, resolving, servers ->
        if (details == null) {
            VideoPlayerScreenUiState.Loading
        } else {
            VideoPlayerScreenUiState.Done(
                movieDetails = details,
                resolvedStream = resolved,
                currentServer = currentServer,
                availableServers = servers,
                isResolving = resolving
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = VideoPlayerScreenUiState.Loading
    )
}

@Immutable
sealed class VideoPlayerScreenUiState {
    data object Loading : VideoPlayerScreenUiState()
    data object Error : VideoPlayerScreenUiState()
    data class Done(
        val movieDetails: MovieDetails,
        val resolvedStream: ResolvedStream?,
        val currentServer: StreamServer,
        val availableServers: List<StreamServer>,
        val isResolving: Boolean = false
    ) : VideoPlayerScreenUiState()
}
