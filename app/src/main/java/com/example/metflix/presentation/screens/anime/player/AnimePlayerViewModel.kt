package com.example.metflix.presentation.screens.anime.player

import android.net.Uri
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.metflix.data.network.anime.AnimeStreamExtractor
import com.example.metflix.data.network.anime.AnimeStreamSniffer
import com.example.metflix.data.network.anime.models.AnimeAudioTrack
import com.example.metflix.data.network.anime.models.AnimeExtractedStream
import com.example.metflix.data.network.anime.models.AnimePlaybackServer
import com.example.metflix.data.network.anime.models.AnimePlayerEngineType
import com.example.metflix.data.network.anime.models.AnimeQualityOption
import com.example.metflix.data.network.anime.models.AnimeStreamDiagnostics
import com.example.metflix.data.network.anime.models.AnimeStreamingServer
import com.example.metflix.data.repositories.AnimeDataSource
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AnimePlayerUiState {
    object Loading : AnimePlayerUiState
    data class Error(val message: String) : AnimePlayerUiState
    data class Ready(
        val mediaType: String,
        val slug: String,
        val title: String,
        val subtitle: String,
        val posterUrl: String?,
        val activeServerUrl: String
    ) : AnimePlayerUiState
}

@HiltViewModel
class AnimePlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val animeDataSource: AnimeDataSource,
    private val animeStreamExtractor: AnimeStreamExtractor,
    private val animeStreamSniffer: AnimeStreamSniffer
) : ViewModel() {

    companion object {
        private const val TAG = "AnimePlayerVM"
        private const val AUTO_HIDE_HUD_DELAY_MS = 6000L

        val DEFAULT_AUDIO_TRACKS = listOf(
            AnimeAudioTrack("hin", "Hindi", "HI"),
            AnimeAudioTrack("eng", "English", "EN"),
            AnimeAudioTrack("jpn", "Japanese", "JA"),
            AnimeAudioTrack("tam", "Tamil", "TA"),
            AnimeAudioTrack("tel", "Telugu", "TE")
        )

        val DEFAULT_QUALITIES = listOf(
            AnimeQualityOption("Auto", null),
            AnimeQualityOption("1080p", 1080),
            AnimeQualityOption("720p", 720),
            AnimeQualityOption("480p", 480),
            AnimeQualityOption("360p", 360)
        )
    }

    val mediaType: String = savedStateHandle.get<String>("mediaType") ?: "episode"
    val slug: String = savedStateHandle.get<String>("slug") ?: ""

    private val _uiState = MutableStateFlow<AnimePlayerUiState>(AnimePlayerUiState.Loading)
    val uiState: StateFlow<AnimePlayerUiState> = _uiState.asStateFlow()

    private val _availableServers = MutableStateFlow<List<AnimePlaybackServer>>(emptyList())
    val availableServers: StateFlow<List<AnimePlaybackServer>> = _availableServers.asStateFlow()

    private val _selectedServerNumber = MutableStateFlow(1)
    val selectedServerNumber: StateFlow<Int> = _selectedServerNumber.asStateFlow()

    private val _availableAudioTracks = MutableStateFlow(DEFAULT_AUDIO_TRACKS)
    val availableAudioTracks: StateFlow<List<AnimeAudioTrack>> = _availableAudioTracks.asStateFlow()

    private val _selectedAudioTrack = MutableStateFlow(DEFAULT_AUDIO_TRACKS.first())
    val selectedAudioTrack: StateFlow<AnimeAudioTrack> = _selectedAudioTrack.asStateFlow()

    private val _availableQualities = MutableStateFlow(DEFAULT_QUALITIES)
    val availableQualities: StateFlow<List<AnimeQualityOption>> = _availableQualities.asStateFlow()

    private val _selectedQuality = MutableStateFlow(DEFAULT_QUALITIES.first())
    val selectedQuality: StateFlow<AnimeQualityOption> = _selectedQuality.asStateFlow()

    private val _engineType = MutableStateFlow(AnimePlayerEngineType.EXOPLAYER)
    val engineType: StateFlow<AnimePlayerEngineType> = _engineType.asStateFlow()

    private val _extractedStream = MutableStateFlow<AnimeExtractedStream?>(null)
    val extractedStream: StateFlow<AnimeExtractedStream?> = _extractedStream.asStateFlow()

    private val _isPlaying = MutableStateFlow(true)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _bufferedPositionMs = MutableStateFlow(0L)
    val bufferedPositionMs: StateFlow<Long> = _bufferedPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _diagnostics = MutableStateFlow(AnimeStreamDiagnostics())
    val diagnostics: StateFlow<AnimeStreamDiagnostics> = _diagnostics.asStateFlow()

    private val _isHudVisible = MutableStateFlow(true)
    val isHudVisible: StateFlow<Boolean> = _isHudVisible.asStateFlow()

    private val _isServerDialogOpen = MutableStateFlow(false)
    val isServerDialogOpen: StateFlow<Boolean> = _isServerDialogOpen.asStateFlow()

    private val _isAudioDialogOpen = MutableStateFlow(false)
    val isAudioDialogOpen: StateFlow<Boolean> = _isAudioDialogOpen.asStateFlow()

    private val _isQualityDialogOpen = MutableStateFlow(false)
    val isQualityDialogOpen: StateFlow<Boolean> = _isQualityDialogOpen.asStateFlow()

    private val _isSettingsMenuOpen = MutableStateFlow(false)
    val isSettingsMenuOpen: StateFlow<Boolean> = _isSettingsMenuOpen.asStateFlow()

    private val _seekEvent = MutableStateFlow<Long?>(null)
    val seekEvent: StateFlow<Long?> = _seekEvent.asStateFlow()

    private val _playPauseCommand = MutableStateFlow<Boolean?>(null)
    val playPauseCommand: StateFlow<Boolean?> = _playPauseCommand.asStateFlow()

    private val _webViewCenterClickEvent = MutableStateFlow<Long?>(null)
    val webViewCenterClickEvent: StateFlow<Long?> = _webViewCenterClickEvent.asStateFlow()

    private val _clickFeedbackText = MutableStateFlow<String?>(null)
    val clickFeedbackText: StateFlow<String?> = _clickFeedbackText.asStateFlow()

    private var clickCount = 0
    private var clearFeedbackJob: Job? = null

    private var cachedTitle: String = ""
    private var cachedSubtitle: String = ""
    private var cachedPosterUrl: String? = null

    private var autoHideHudJob: Job? = null

    init {
        loadPlaybackData()
        startAutoHideTimer()
    }

    fun loadPlaybackData() {
        resetClickCount()
        viewModelScope.launch {
            _uiState.value = AnimePlayerUiState.Loading
            try {
                if (mediaType == "movie") loadMoviePlayback() else loadEpisodePlayback()
            } catch (e: Exception) {
                Log.e(TAG, "Error loading anime stream: ${e.message}", e)
                _uiState.value = AnimePlayerUiState.Error(
                    e.message ?: "Failed to connect to anime stream provider."
                )
            }
        }
    }

    private suspend fun loadEpisodePlayback() {
        val result = animeDataSource.getEpisodeData(slug)
        result.fold(
            onSuccess = { ep ->
                val servers = buildNormalizedServers(ep.streamingServers)
                _availableServers.value = servers

                val activeServer = servers.firstOrNull { it.serverNumber == _selectedServerNumber.value }
                    ?: servers.firstOrNull()

                val title = ep.episodeTitle?.ifBlank { "Episode ${ep.episode ?: 1}" } ?: "Episode 1"
                val subtitle = buildString {
                    ep.seriesTitle?.let { append(it) }
                    if (ep.season != null && ep.episode != null) {
                        if (isNotEmpty()) append(" • ")
                        append("S${ep.season} E${ep.episode}")
                    }
                }

                cachedTitle = title
                cachedSubtitle = subtitle
                cachedPosterUrl = ep.poster

                if (activeServer != null) {
                    _uiState.value = AnimePlayerUiState.Ready(
                        mediaType = "episode",
                        slug = slug,
                        title = title,
                        subtitle = subtitle,
                        posterUrl = ep.poster,
                        activeServerUrl = activeServer.embedUrl
                    )
                    resolveStreamForServer(activeServer)
                } else {
                    _uiState.value = AnimePlayerUiState.Error("No streaming servers available for this episode.")
                }
            },
            onFailure = { err ->
                _uiState.value = AnimePlayerUiState.Error(
                    err.message ?: "Could not load episode streaming data."
                )
            }
        )
    }

    private suspend fun loadMoviePlayback() {
        val result = animeDataSource.getMovieData(slug)
        result.fold(
            onSuccess = { movie ->
                val servers = buildNormalizedServers(movie.streamingServers)
                _availableServers.value = servers

                val activeServer = servers.firstOrNull { it.serverNumber == _selectedServerNumber.value }
                    ?: servers.firstOrNull()

                val title = movie.title ?: "Anime Movie"
                val subtitle = buildString {
                    movie.releaseYear?.let { append(it) }
                    if (movie.duration != null) {
                        if (isNotEmpty()) append(" • ")
                        append(movie.duration)
                    }
                }

                cachedTitle = title
                cachedSubtitle = subtitle
                cachedPosterUrl = movie.poster

                if (activeServer != null) {
                    _uiState.value = AnimePlayerUiState.Ready(
                        mediaType = "movie",
                        slug = slug,
                        title = title,
                        subtitle = subtitle,
                        posterUrl = movie.poster,
                        activeServerUrl = activeServer.embedUrl
                    )
                    resolveStreamForServer(activeServer)
                } else {
                    _uiState.value = AnimePlayerUiState.Error("No streaming servers available for this movie.")
                }
            },
            onFailure = { err ->
                _uiState.value = AnimePlayerUiState.Error(
                    err.message ?: "Could not load movie streaming data."
                )
            }
        )
    }

    private fun buildNormalizedServers(rawServers: List<AnimeStreamingServer>): List<AnimePlaybackServer> {
        if (rawServers.isEmpty()) {
            return listOf(
                AnimePlaybackServer(
                    serverNumber = 1,
                    title = "Server 1",
                    subtitle = "Play (Direct)",
                    embedUrl = "https://play.zephyrix.org",
                    rawId = "server-1"
                ),
                AnimePlaybackServer(
                    serverNumber = 2,
                    title = "Server 2",
                    subtitle = "Abyss (Multi-Audio)",
                    embedUrl = "https://player.abyssplayer.com",
                    rawId = "server-2"
                )
            )
        }

        return rawServers.take(2).mapIndexed { index, raw ->
            val num = index + 1
            val rawName = raw.serverName?.trim().orEmpty()
            val cleanSubtitle = if (rawName.isNotBlank()) {
                rawName.replace(Regex("^Server\\s*\\d+\\s*[-–:]*\\s*", RegexOption.IGNORE_CASE), "").trim()
            } else if (num == 1) "Fast Direct" else "Alternative"

            AnimePlaybackServer(
                serverNumber = num,
                title = "Server $num",
                subtitle = cleanSubtitle.ifBlank { rawName.ifBlank { "Stream $num" } },
                embedUrl = raw.embedUrl.orEmpty(),
                rawId = raw.serverId.orEmpty().ifBlank { "server-$num" },
                directStreamUrl = raw.directStreamUrl,
                directStreamType = raw.directStreamType,
                decodedStreams = raw.decodedStreams
            )
        }
    }

    fun selectEngine(engine: AnimePlayerEngineType) {
        if (_engineType.value == engine) return
        _engineType.value = engine
        resetClickCount()
        userInteracted()
    }

    /**
     * Resolves a playable stream URL for the given server.
     *
     * NOTE: WebView playback is intentionally NOT used as an automatic fallback.
     * On this MediaTek TV, Chromium's GPU renderer collides with the DirectFB
     * framebuffer (0x2062b000–0x20a2a000) causing a permanent black screen even
     * when the video decodes (see logcat "WebView video decoding OK: 842x481" +
     * DFB overlap warnings). The user can still explicitly switch to WebView via
     * the error screen "Play in WebView" button if they want to try.
     */
    private fun resolveStreamForServer(server: AnimePlaybackServer) {
        viewModelScope.launch {
            _diagnostics.value = _diagnostics.value.copy(resolution = "Resolving stream...")
            Log.i(TAG, "resolveStreamForServer: ${server.embedUrl}")

            // 1. Backend-provided direct stream URL (may be a pointer file like master.txt)
            val directUrl = server.directStreamUrl?.trim().orEmpty()
            if (directUrl.isNotBlank()) {
                Log.i(TAG, "Direct stream URL from backend: $directUrl")

                val origin = runCatching {
                    val uri = Uri.parse(server.embedUrl.ifBlank { directUrl })
                    "${uri.scheme}://${uri.host}"
                }.getOrNull() ?: "https://play.zephyrix.org"

                // ⭐ Use the FULL server.embedUrl (e.g. https://play.zephyrix.org/video/...) as Referer, plus required Accept: */*
                val headersForPointer = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
                    "Referer" to server.embedUrl.ifBlank { "https://play.zephyrix.org/video/" },
                    "Origin" to origin,
                    "Accept" to "*/*",
                    "Accept-Language" to "en-US,en;q=0.9",
                    "Sec-Fetch-Site" to "same-origin",
                    "Sec-Fetch-Mode" to "cors",
                    "Sec-Fetch-Dest" to "empty",
                    "Sec-CH-UA-Platform" to "\"Windows\"",
                    "Sec-CH-UA" to "\"Chromium\";v=\"124\", \"Not:A-Brand\";v=\"8\", \"Google Chrome\";v=\"124\"",
                    "Sec-CH-UA-Mobile" to "?0"
                )

                val lower = directUrl.lowercase()
                val looksLikeMedia = lower.contains(".m3u8") || lower.contains(".mp4")

                if (looksLikeMedia) {
                    _extractedStream.value = AnimeExtractedStream(directUrl, headersForPointer, true)
                    _engineType.value = AnimePlayerEngineType.EXOPLAYER
                    _diagnostics.value = _diagnostics.value.copy(resolution = "Direct Stream Ready")
                    return@launch
                }

                // It's a pointer file (.txt). Resolve it with the FULL page URL as Referer.
                Log.i(TAG, "Direct URL is a pointer file. Resolving with full page Referer: ${server.embedUrl}")
                val finalUrl = animeStreamExtractor.resolvePointerUrl(directUrl, headersForPointer)
                if (finalUrl != null) {
                    Log.i(TAG, "Pointer resolved to: $finalUrl")
                    _extractedStream.value = AnimeExtractedStream(finalUrl, headersForPointer, true)
                    if (finalUrl.contains(".m3u8", ignoreCase = true) || finalUrl.contains(".txt", ignoreCase = true)) {
                        viewModelScope.launch {
                            val dump = animeStreamExtractor.fetchPlaylistText(finalUrl, headersForPointer)
                            Log.i(TAG, "=== M3U8 DUMP ===\n$dump\n=== END M3U8 ===")
                        }
                    }
                    _engineType.value = AnimePlayerEngineType.EXOPLAYER
                    _diagnostics.value = _diagnostics.value.copy(resolution = "Direct Stream Ready")
                    return@launch
                } else {
                    Log.e(TAG, "Pointer resolution failed. Referer was: ${server.embedUrl}")
                }
            }

            // 2. Decoded streams (multi-audio short.icu links)
            val decoded = server.decodedStreams
            if (!decoded.isNullOrEmpty()) {
                val currentLang = _selectedAudioTrack.value.displayName
                val langCode = _selectedAudioTrack.value.code
                val matchedStream = decoded.firstOrNull { d ->
                    val lang = d.language.orEmpty()
                    lang.equals(currentLang, ignoreCase = true) ||
                            lang.startsWith(langCode, ignoreCase = true)
                } ?: decoded.firstOrNull()

                val targetUrl = matchedStream?.link?.ifBlank { null }
                if (!targetUrl.isNullOrBlank()) {
                    Log.i(TAG, "Decoded stream for $currentLang: $targetUrl")

                    val staticExtracted = animeStreamExtractor.extractStream(targetUrl, langCode)
                    if (staticExtracted.isDirectStream) {
                        _extractedStream.value = staticExtracted
                        _engineType.value = AnimePlayerEngineType.EXOPLAYER
                        return@launch
                    }

                    val sniffedStream = animeStreamSniffer.sniffStream(targetUrl, langCode)
                    if (sniffedStream != null && sniffedStream.isDirectStream) {
                        Log.i(TAG, "Decoded+sniffed stream: ${sniffedStream.streamUrl}")
                        _extractedStream.value = sniffedStream
                        _engineType.value = AnimePlayerEngineType.EXOPLAYER
                        return@launch
                    }
                }
                // If decoded stream failed (e.g. net::ERR_NAME_NOT_RESOLVED), continue to server.embedUrl
            }

            // 3. Static HTTP extraction fallback for raw embed URLs
            val staticExtracted = animeStreamExtractor.extractStream(server.embedUrl, _selectedAudioTrack.value.code)
            if (staticExtracted.isDirectStream) {
                Log.d(TAG, "Direct stream found via static extractor: ${staticExtracted.streamUrl}")
                _extractedStream.value = staticExtracted
                _engineType.value = AnimePlayerEngineType.EXOPLAYER
                return@launch
            }

            // 4. Background headless sniffer (25s budget)
            Log.d(TAG, "Starting background stream sniffer for: ${server.embedUrl}")
            val sniffedStream = animeStreamSniffer.sniffStream(server.embedUrl, _selectedAudioTrack.value.code)
            if (sniffedStream != null && sniffedStream.isDirectStream) {
                Log.i(TAG, "Direct stream captured by sniffer: ${sniffedStream.streamUrl}")
                _extractedStream.value = sniffedStream
                _engineType.value = AnimePlayerEngineType.EXOPLAYER
                return@launch
            }

            // 5. Automatic fallback to next server (e.g., Server 0 -> Server 1) if direct stream extraction fails
            val servers = _availableServers.value
            val currentIndex = servers.indexOfFirst { it.serverNumber == server.serverNumber }
            if (currentIndex in 0 until servers.lastIndex) {
                val nextServer = servers[currentIndex + 1]
                Log.w(TAG, "Server ${server.serverNumber} (${server.subtitle}) failed to resolve direct stream. Automatically falling back to Server ${nextServer.serverNumber} (${nextServer.subtitle})...")
                _selectedServerNumber.value = nextServer.serverNumber
                resolveStreamForServer(nextServer)
                return@launch
            }

            Log.e(TAG, "All servers failed to resolve stream for ${server.embedUrl}")
            _uiState.value = AnimePlayerUiState.Error(
                "Could not extract direct video stream from available servers.\n\n" +
                "Try selecting another audio track or refresh."
            )
        }
    }

    fun selectServer(serverNumber: Int) {
        if (_selectedServerNumber.value == serverNumber) return
        _selectedServerNumber.value = serverNumber
        _isServerDialogOpen.value = false
        resetClickCount()
        userInteracted()

        val active = _availableServers.value.firstOrNull { it.serverNumber == serverNumber }
        if (active != null) {
            _extractedStream.value = null
            _uiState.value = AnimePlayerUiState.Ready(
                mediaType = mediaType,
                slug = slug,
                title = cachedTitle.ifBlank { "Anime Stream" },
                subtitle = cachedSubtitle,
                posterUrl = cachedPosterUrl,
                activeServerUrl = active.embedUrl
            )
            resolveStreamForServer(active)
        }
    }

    fun dispatchCenterClick() {
        clickCount++
        _webViewCenterClickEvent.value = System.currentTimeMillis()
        val text = when (clickCount) {
            1 -> "Click 1/3 • Dismissing Ad 1"
            2 -> "Click 2/3 • Dismissing Ad 2"
            3 -> "Click 3/3 • Starting Playback"
            else -> "Center Click #$clickCount"
        }
        _clickFeedbackText.value = text
        clearFeedbackJob?.cancel()
        clearFeedbackJob = viewModelScope.launch {
            delay(2500L); _clickFeedbackText.value = null
        }
    }

    fun onCenterClickConsumed() { _webViewCenterClickEvent.value = null }

    fun resetClickCount() {
        clickCount = 0
        _clickFeedbackText.value = null
        clearFeedbackJob?.cancel()
        clearFeedbackJob = null
    }

    fun onExoPlayerPlaybackError(errorMessage: String) {
        Log.e(TAG, "ExoPlayer playback error: $errorMessage")
        _uiState.value = AnimePlayerUiState.Error(errorMessage)
    }

    fun switchToNextServer() {
        val nextServer = if (_selectedServerNumber.value == 1) 2 else 1
        selectServer(nextServer)
    }

    fun selectAudioTrack(track: AnimeAudioTrack) {
        _selectedAudioTrack.value = track
        _isAudioDialogOpen.value = false
        userInteracted()
        val active = _availableServers.value.firstOrNull { it.serverNumber == _selectedServerNumber.value }
        if (active != null && !active.decodedStreams.isNullOrEmpty()) {
            resolveStreamForServer(active)
        }
    }

    fun selectQuality(quality: AnimeQualityOption) {
        _selectedQuality.value = quality
        _isQualityDialogOpen.value = false
        userInteracted()
    }

    fun togglePlayPause() {
        val target = !_isPlaying.value
        _isPlaying.value = target
        _playPauseCommand.value = target
        userInteracted()
    }

    fun seekBy(offsetMs: Long) {
        val cur = _currentPositionMs.value
        val dur = _durationMs.value
        val target = (cur + offsetMs).coerceIn(0L, dur.coerceAtLeast(0L))
        _currentPositionMs.value = target
        _seekEvent.value = target
        userInteracted()
    }

    fun onSeekEventConsumed() { _seekEvent.value = null }
    fun onPlayPauseCommandConsumed() { _playPauseCommand.value = null }

    fun updatePlaybackProgress(currentMs: Long, totalMs: Long, bufferedMs: Long) {
        _currentPositionMs.value = currentMs
        if (totalMs > 0L) _durationMs.value = totalMs
        _bufferedPositionMs.value = bufferedMs
        val bufferHealth = (bufferedMs - currentMs).coerceAtLeast(0L)
        _diagnostics.value = _diagnostics.value.copy(bufferHealthMs = bufferHealth)
    }

    fun updateDownloadMetrics(speedBps: Long, totalBytes: Long) {
        _diagnostics.value = _diagnostics.value.copy(
            downloadSpeedBps = speedBps,
            totalBytesDownloaded = totalBytes
        )
    }

    fun updateVideoFormat(resolution: String, bitrate: String) {
        _diagnostics.value = _diagnostics.value.copy(resolution = resolution, bitrate = bitrate)
    }

    fun updateAvailableQualities(heights: List<Int>) {
        if (heights.isEmpty()) return
        val currentSelected = _selectedQuality.value
        val qualities = mutableListOf(AnimeQualityOption("Auto", null))
        heights.sortedDescending().distinct().forEach { h ->
            qualities.add(AnimeQualityOption("${h}p", h))
        }
        _availableQualities.value = qualities
        // Keep current quality selection if valid, otherwise keep Auto
        if (currentSelected.height != null && heights.contains(currentSelected.height)) {
            _selectedQuality.value = currentSelected
        }
    }

    fun openServerDialog() {
        _isServerDialogOpen.value = true
        _isAudioDialogOpen.value = false
        _isQualityDialogOpen.value = false
        cancelAutoHideTimer()
    }

    fun openAudioDialog() {
        _isAudioDialogOpen.value = true
        _isServerDialogOpen.value = false
        _isQualityDialogOpen.value = false
        cancelAutoHideTimer()
    }

    fun openQualityDialog() {
        _isQualityDialogOpen.value = true
        _isServerDialogOpen.value = false
        _isAudioDialogOpen.value = false
        cancelAutoHideTimer()
    }

    fun openSettingsMenu() {
        _isSettingsMenuOpen.value = true
        _isServerDialogOpen.value = false
        _isAudioDialogOpen.value = false
        _isQualityDialogOpen.value = false
        cancelAutoHideTimer()
    }

    fun closeSettingsMenu() {
        _isSettingsMenuOpen.value = false
        userInteracted()
    }

    fun showHud() {
        _isHudVisible.value = true
        startAutoHideTimer()
    }

    fun hideHud() {
        _isHudVisible.value = false
        cancelAutoHideTimer()
    }

    fun closeAllDialogs() {
        _isServerDialogOpen.value = false
        _isAudioDialogOpen.value = false
        _isQualityDialogOpen.value = false
        _isSettingsMenuOpen.value = false
        userInteracted()
    }

    fun toggleHud() {
        if (_isServerDialogOpen.value || _isAudioDialogOpen.value || _isQualityDialogOpen.value || _isSettingsMenuOpen.value) {
            closeAllDialogs(); return
        }
        val next = !_isHudVisible.value
        _isHudVisible.value = next
        if (next) startAutoHideTimer() else cancelAutoHideTimer()
    }

    fun userInteracted() {
        if (!_isSettingsMenuOpen.value && !_isServerDialogOpen.value &&
            !_isAudioDialogOpen.value && !_isQualityDialogOpen.value) {
            _isHudVisible.value = true
            startAutoHideTimer()
        }
    }

    private fun startAutoHideTimer() {
        cancelAutoHideTimer()
        autoHideHudJob = viewModelScope.launch {
            delay(AUTO_HIDE_HUD_DELAY_MS)
            if (_isPlaying.value && !_isServerDialogOpen.value && !_isAudioDialogOpen.value &&
                !_isQualityDialogOpen.value && !_isSettingsMenuOpen.value) {
                _isHudVisible.value = false
            }
        }
    }

    private fun cancelAutoHideTimer() {
        autoHideHudJob?.cancel()
        autoHideHudJob = null
    }
}