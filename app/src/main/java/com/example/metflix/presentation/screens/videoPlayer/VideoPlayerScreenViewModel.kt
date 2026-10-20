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
import com.example.metflix.data.network.alldebrid.AllDebridClient
import com.example.metflix.data.network.resolvers.ResolvedStream
import com.example.metflix.data.network.resolvers.StreamResolverRepository
import com.example.metflix.data.network.resolvers.StreamServer
import com.example.metflix.data.repositories.MovieRepository
import com.example.metflix.data.settings.DebridPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
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
    private val streamResolverRepository: StreamResolverRepository,
    private val debridPreferences: DebridPreferences,
    private val allDebridClient: AllDebridClient
) : ViewModel() {

    private val _availableServers = MutableStateFlow(
        streamResolverRepository.availableServers.filter(::isServerSelectable)
    )
    val availableServers: List<StreamServer> get() = _availableServers.value

    private val _selectedServer = MutableStateFlow(
        streamResolverRepository.availableServers.first(::isServerSelectable)
    )
    val selectedServer: StateFlow<StreamServer> = _selectedServer.asStateFlow()

    private val _isResolvingStream = MutableStateFlow(false)
    val isResolvingStream: StateFlow<Boolean> = _isResolvingStream.asStateFlow()

    private val _resolutionMessage = MutableStateFlow("Connecting to stream...")
    val resolutionMessage: StateFlow<String> = _resolutionMessage.asStateFlow()

    private val _resolvedStream = MutableStateFlow<ResolvedStream?>(null)
    val resolvedStream: StateFlow<ResolvedStream?> = _resolvedStream.asStateFlow()

    private val _isTvShow = MutableStateFlow(false)
    val isTvShow: StateFlow<Boolean> = _isTvShow.asStateFlow()

    private val _seasonNumber = MutableStateFlow(1)
    val seasonNumber: StateFlow<Int> = _seasonNumber.asStateFlow()

    private val _episodeNumber = MutableStateFlow(1)
    val episodeNumber: StateFlow<Int> = _episodeNumber.asStateFlow()

    private val _episodeTitle = MutableStateFlow("")
    val episodeTitle: StateFlow<String> = _episodeTitle.asStateFlow()

    private val movieIdFlow = savedStateHandle.getStateFlow<String?>(VideoPlayerScreen.MovieIdBundleKey, null)
    private val movieDetailsFlow = MutableStateFlow<MovieDetails?>(null)
    private val _showPlaybackSetupDialog = MutableStateFlow(false)
    val showPlaybackSetupDialog: StateFlow<Boolean> = _showPlaybackSetupDialog.asStateFlow()

    val playbackSource: StateFlow<String> = debridPreferences.playbackSource
    val playbackQuality: StateFlow<String> = debridPreferences.playbackQuality
    val playbackPreferHindi: StateFlow<Boolean> = debridPreferences.playbackPreferHindi

    private val isUserExplicitSelection = MutableStateFlow(false)
    private var raceJob: Job? = null
    private var manualResolveJob: Job? = null
    private val resolutionRequests = ResolutionRequestTracker()

    private fun isServerSelectable(server: StreamServer): Boolean =
        server.id != "alldebrid" || debridPreferences.hasVerifiedPremiumDebrid()

    init {
        viewModelScope.launch {
            movieIdFlow.collect { rawId ->
                if (!rawId.isNullOrBlank()) {
                    val isTvPrefix = rawId.startsWith("tv:")
                    val stripped = if (isTvPrefix) rawId.removePrefix("tv:") else rawId
                    val parts = stripped.split(":")
                    val mediaId = parts[0]
                    val parsedSeason = parts.getOrNull(1)?.toIntOrNull() ?: 1
                    val parsedEpisode = parts.getOrNull(2)?.toIntOrNull() ?: 1

                    var details = repository.getMovieDetails(movieId = mediaId, isTvShow = if (isTvPrefix) true else null)
                    val isTv = details.isTvShow || isTvPrefix

                    _isTvShow.value = isTv
                    _seasonNumber.value = parsedSeason
                    _episodeNumber.value = parsedEpisode

                    if (isTv) {
                        // If requested season is not season 1 or episodes aren't loaded, fetch season episodes
                        val currentSeason = details.seasons.firstOrNull { it.seasonNumber == parsedSeason }
                        if (currentSeason == null || currentSeason.episodes.isEmpty()) {
                            val epList = repository.getTvSeasonEpisodes(details.id, parsedSeason)
                            val updatedSeasons = details.seasons.map { s ->
                                if (s.seasonNumber == parsedSeason) s.copy(episodes = epList) else s
                            }
                            details = details.copy(seasons = updatedSeasons)
                        }

                        val foundEpisode = details.seasons
                            .firstOrNull { it.seasonNumber == parsedSeason }
                            ?.episodes
                            ?.firstOrNull { it.episodeNumber == parsedEpisode }

                        _episodeTitle.value = foundEpisode?.name.orEmpty()
                    } else {
                        _episodeTitle.value = ""
                    }

                    movieDetailsFlow.value = details

                    val hasDebridKey = debridPreferences.hasValidKey()
                    if (hasDebridKey) {
                        // Present Playback Setup dialog to let user choose Debrid vs Free, Quality & Hindi
                        _showPlaybackSetupDialog.value = true
                    } else {
                        _showPlaybackSetupDialog.value = false
                        startServerRace(
                            details = details,
                            isTv = isTv,
                            season = parsedSeason,
                            episode = parsedEpisode,
                            forceNonDebrid = true,
                            preferHindi = debridPreferences.playbackPreferHindi.value,
                            preferredQuality = debridPreferences.playbackQuality.value
                        )
                    }
                }
            }
        }
    }

    fun confirmPlaybackSetup(source: String, quality: String, preferHindi: Boolean) {
        _showPlaybackSetupDialog.value = false
        debridPreferences.savePlaybackPreferences(source, quality, preferHindi)
        val details = movieDetailsFlow.value ?: return
        startServerRace(
            details = details,
            isTv = _isTvShow.value,
            season = _seasonNumber.value,
            episode = _episodeNumber.value,
            forceNonDebrid = (source == "free"),
            preferHindi = preferHindi,
            preferredQuality = quality
        )
    }

    fun dismissPlaybackSetup() {
        _showPlaybackSetupDialog.value = false
    }

    private fun startServerRace(
        details: MovieDetails,
        isTv: Boolean,
        season: Int,
        episode: Int,
        forceNonDebrid: Boolean = (debridPreferences.playbackSource.value == "free"),
        preferHindi: Boolean = debridPreferences.playbackPreferHindi.value,
        preferredQuality: String = debridPreferences.playbackQuality.value
    ) {
        raceJob?.cancel()
        val requestId = resolutionRequests.begin()
        raceJob = viewModelScope.launch {
            _isResolvingStream.value = true
            isUserExplicitSelection.value = false
            _resolutionMessage.value = if (!forceNonDebrid && debridPreferences.hasVerifiedPremiumDebrid()) {
                "Checking Premium ${debridPreferences.selectedProvider.value.displayName} cache..."
            } else {
                "Connecting to stream..."
            }

            streamResolverRepository.raceAndRankStreams(
                tmdbId = details.id,
                movieTitle = details.name,
                releaseYear = details.releaseDate.take(4),
                originalLanguage = details.originalLanguage,
                isTvShow = isTv,
                season = season,
                episode = episode,
                imdbId = details.imdbId,
                forceNonDebrid = forceNonDebrid,
                preferHindi = preferHindi,
                preferredQuality = preferredQuality
            ).collect { result ->
                if (!resolutionRequests.isCurrent(requestId)) return@collect
                _availableServers.value = result.rankedServers.filter(::isServerSelectable)

                // If user hasn't explicitly selected another server, pick the fastest/best stream
                // Do NOT repeatedly overwrite and restart playback if a stream is already active
                if (!isUserExplicitSelection.value && result.bestStream != null) {
                    val currentStream = _resolvedStream.value
                    if (currentStream == null) {
                        if (isServerSelectable(result.bestStream.server)) {
                            _selectedServer.value = result.bestStream.server
                        }
                        _resolvedStream.value = result.bestStream
                        _resolutionMessage.value = if (result.bestStream.server.id == "alldebrid") {
                            "Connected · Premium Stream"
                        } else {
                            "Playing · ${result.bestStream.server.name}"
                        }
                    }
                } else if (!isUserExplicitSelection.value && result.bestStream == null && result.isFinal) {
                    _resolutionMessage.value = "Premium stream uncached. Please select an alternative source."
                }

                if (result.isFinal) {
                    _isResolvingStream.value = false
                }
            }
        }
    }

    private fun resolveCurrentStream(details: MovieDetails, server: StreamServer) {
        manualResolveJob?.cancel()
        val requestId = resolutionRequests.begin()
        _resolvedStream.value = null
        manualResolveJob = viewModelScope.launch {
            _isResolvingStream.value = true
            _resolutionMessage.value = "Switching to ${server.name}..."
            try {
                val resolved = streamResolverRepository.resolveMovieStream(
                    tmdbId = details.id,
                    movieTitle = details.name,
                    releaseYear = details.releaseDate.take(4),
                    server = server,
                    isTvShow = _isTvShow.value,
                    season = _seasonNumber.value,
                    episode = _episodeNumber.value,
                    imdbId = details.imdbId
                )
                // Only apply if the server is still selected
                if (resolutionRequests.isCurrent(requestId) && _selectedServer.value.id == server.id) {
                    _resolvedStream.value = resolved
                    _resolutionMessage.value = "Playing · ${server.name}"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (resolutionRequests.isCurrent(requestId) && _selectedServer.value.id == server.id) {
                    _resolutionMessage.value = "Failed to connect to ${server.name}"
                }
            } finally {
                if (resolutionRequests.isCurrent(requestId) && _selectedServer.value.id == server.id) {
                    _isResolvingStream.value = false
                }
            }

        }
    }

    fun selectServer(server: StreamServer) {
        if (!isServerSelectable(server)) {
            _resolutionMessage.value = "Premium provider is disabled or not verified"
            return
        }
        if (_selectedServer.value.id == server.id && _resolvedStream.value != null && !_isResolvingStream.value) return
        isUserExplicitSelection.value = true
        _selectedServer.value = server
        val details = movieDetailsFlow.value ?: return
        resolveCurrentStream(details, server)
    }

    fun retryNextServer(): StreamServer? {
        val currentList = _availableServers.value
        if (currentList.isEmpty()) return null
        val currentIndex = currentList.indexOfFirst { it.id == _selectedServer.value.id }
        val nextIndex = if (currentIndex >= 0) (currentIndex + 1) % currentList.size else 0
        val nextServer = currentList[nextIndex]
        selectServer(nextServer)
        return nextServer
    }

    fun onCapturedDirectStream(directUrl: String, headers: Map<String, String>) {
        val current = _resolvedStream.value ?: return
        // Ignore duplicate stream URLs or subtitle/audio-only tracks
        if (current.streamUrl != directUrl &&
            !directUrl.contains("subtitle", ignoreCase = true) &&
            !directUrl.contains(".vtt", ignoreCase = true)
        ) {
            _resolvedStream.value = current.copy(
                streamUrl = directUrl,
                isHls = true,
                headers = headers
            )
        }
    }

    val debridApiKey: StateFlow<String> = debridPreferences.apiKey
    val isDebridEnabled: StateFlow<Boolean> = debridPreferences.isEnabled
    val debridUserInfo: StateFlow<String> = debridPreferences.userInfo
    val debridProvider: StateFlow<com.example.metflix.data.settings.DebridProviderType> = debridPreferences.selectedProvider

    fun selectDebridProvider(provider: com.example.metflix.data.settings.DebridProviderType) {
        debridPreferences.selectProvider(provider)
        viewModelScope.launch {
            if (debridPreferences.apiKey.value.isNotBlank()) {
                val status = allDebridClient.checkStatus(provider, debridPreferences.apiKey.value)
                debridPreferences.saveUserInfo(status.message)
                if (status.isValid) {
                    debridPreferences.saveVerificationStatus(
                        isVerified = true,
                        isPremium = status.isPremium,
                        email = status.username,
                        days = status.daysRemaining,
                        plan = if (status.isPremium) "Premium" else "Free"
                    )
                } else {
                    debridPreferences.clearVerification()
                }
            }
            selectServer(_selectedServer.value)
        }
    }

    fun saveDebridApiKey(key: String) {
        debridPreferences.saveApiKey(key)
        viewModelScope.launch {
            if (key.isBlank()) {
                debridPreferences.saveUserInfo("")
                debridPreferences.clearVerification()
                return@launch
            }
            val status = allDebridClient.checkStatus(debridPreferences.selectedProvider.value, key)
            debridPreferences.saveUserInfo(status.message)
            if (status.isValid) {
                debridPreferences.saveVerificationStatus(
                    isVerified = true,
                    isPremium = status.isPremium,
                    email = status.username,
                    days = status.daysRemaining,
                    plan = if (status.isPremium) "Premium" else "Free"
                )
            } else {
                debridPreferences.clearVerification()
            }
            selectServer(_selectedServer.value)
        }
    }

    fun toggleDebrid(enabled: Boolean) {
        debridPreferences.setEnabled(enabled)
        if (!enabled && _selectedServer.value.id == "alldebrid") {
            val fallback = _availableServers.value.firstOrNull(::isServerSelectable)
            if (fallback != null) {
                _selectedServer.value = fallback
                movieDetailsFlow.value?.let { details -> resolveCurrentStream(details, fallback) }
            }
        } else {
            _availableServers.value = _availableServers.value.filter(::isServerSelectable)
            selectServer(_selectedServer.value)
        }
    }

    val uiState: StateFlow<VideoPlayerScreenUiState> = combine(
        movieDetailsFlow,
        _resolvedStream,
        _selectedServer,
        _isResolvingStream,
        _availableServers,
        _resolutionMessage
    ) { details, resolved, currentServer, resolving, servers, resMsg ->
        if (details == null) {
            VideoPlayerScreenUiState.Loading
        } else {
            val isVerified = debridPreferences.hasVerifiedPremiumDebrid()
            val debridStatus = if (isVerified) {
                val provider = debridPreferences.selectedProvider.value.displayName
                val plan = debridPreferences.planName.value.ifBlank { "Premium" }
                "Connected · $provider ($plan)"
            } else if (debridPreferences.hasValidKey()) {
                "Pending Verification"
            } else {
                "Not Connected"
            }

            VideoPlayerScreenUiState.Done(
                movieDetails = details,
                resolvedStream = resolved,
                currentServer = currentServer,
                availableServers = servers,
                isResolving = resolving,
                isTvShow = _isTvShow.value,
                seasonNumber = _seasonNumber.value,
                episodeNumber = _episodeNumber.value,
                episodeTitle = _episodeTitle.value,
                debridStatus = debridStatus,
                isDebridActive = isVerified,
                resolutionMessage = resMsg
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = VideoPlayerScreenUiState.Loading
    )
}

internal class ResolutionRequestTracker {
    private var currentRequestId = 0L

    fun begin(): Long {
        currentRequestId += 1
        return currentRequestId
    }

    fun isCurrent(requestId: Long): Boolean = requestId == currentRequestId
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
        val isResolving: Boolean = false,
        val isTvShow: Boolean = false,
        val seasonNumber: Int = 1,
        val episodeNumber: Int = 1,
        val episodeTitle: String = "",
        val debridStatus: String = "",
        val isDebridActive: Boolean = false,
        val resolutionMessage: String = ""
    ) : VideoPlayerScreenUiState()
}

private fun <T1, T2, T3, T4, T5, T6, R> combine(
    flow1: Flow<T1>,
    flow2: Flow<T2>,
    flow3: Flow<T3>,
    flow4: Flow<T4>,
    flow5: Flow<T5>,
    flow6: Flow<T6>,
    transform: suspend (T1, T2, T3, T4, T5, T6) -> R
): Flow<R> = combine(
    flow1,
    flow2,
    flow3,
    flow4,
    combine(flow5, flow6) { f5, f6 -> Pair(f5, f6) }
) { f1, f2, f3, f4, f56 ->
    transform(f1, f2, f3, f4, f56.first, f56.second)
}
