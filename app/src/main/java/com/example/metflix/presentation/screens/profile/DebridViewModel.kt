package com.example.metflix.presentation.screens.profile

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.metflix.data.network.alldebrid.AllDebridClient
import com.example.metflix.data.network.alldebrid.AllDebridUserStatus
import com.example.metflix.data.network.torbox.TorBoxClient
import com.example.metflix.data.network.torbox.TorBoxDeviceAuthUiState
import com.example.metflix.data.network.torbox.TorBoxTokenResult
import com.example.metflix.data.network.torbox.TorBoxUserStats
import com.example.metflix.data.settings.DebridPreferences
import com.example.metflix.data.settings.DebridProviderType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DebridViewModel @Inject constructor(
    private val preferences: DebridPreferences,
    private val client: AllDebridClient,
    private val torBoxClient: TorBoxClient
) : ViewModel() {

    val selectedProvider: StateFlow<DebridProviderType> = preferences.selectedProvider
    val apiKey: StateFlow<String> = preferences.apiKey
    val isEnabled: StateFlow<Boolean> = preferences.isEnabled
    val userInfo: StateFlow<String> = preferences.userInfo
    val isVerified: StateFlow<Boolean> = preferences.isVerified
    val isPremium: StateFlow<Boolean> = preferences.isPremium
    val autoFallbackToFree: StateFlow<Boolean> = preferences.autoFallbackToFree
    val keystoreError: StateFlow<String?> = preferences.keystoreError

    fun toggleAutoFallback(enabled: Boolean) {
        preferences.setAutoFallbackToFree(enabled)
    }

    private val _userStatus = MutableStateFlow<AllDebridUserStatus?>(null)
    val userStatus: StateFlow<AllDebridUserStatus?> = _userStatus.asStateFlow()

    private val _isValidating = MutableStateFlow(false)
    val isValidating: StateFlow<Boolean> = _isValidating.asStateFlow()

    private val _deviceAuthState = MutableStateFlow<TorBoxDeviceAuthUiState>(TorBoxDeviceAuthUiState.Idle)
    val deviceAuthState: StateFlow<TorBoxDeviceAuthUiState> = _deviceAuthState.asStateFlow()

    private val _torBoxStats = MutableStateFlow<TorBoxUserStats?>(null)
    val torBoxStats: StateFlow<TorBoxUserStats?> = _torBoxStats.asStateFlow()

    private val _cloudDownloads = MutableStateFlow<List<com.example.metflix.data.network.torbox.TorBoxCachedItem>>(emptyList())
    val cloudDownloads: StateFlow<List<com.example.metflix.data.network.torbox.TorBoxCachedItem>> = _cloudDownloads.asStateFlow()

    private val _isLoadingDownloads = MutableStateFlow(false)
    val isLoadingDownloads: StateFlow<Boolean> = _isLoadingDownloads.asStateFlow()

    private var pollingJob: Job? = null
    private var validationJob: Job? = null

    init {
        // Automatically validate account on load if key is present
        if (preferences.apiKey.value.isNotBlank()) {
            validateStatus()
        }
    }

    fun selectProvider(provider: DebridProviderType) {
        cancelTorBoxDeviceAuth()
        validationJob?.cancel()
        preferences.selectProvider(provider)
        _userStatus.value = null
        _torBoxStats.value = null
        _cloudDownloads.value = emptyList()
        if (preferences.apiKey.value.isNotBlank()) {
            validateStatus()
        }
    }

    fun saveApiKey(newKey: String) {
        val cleanKey = preferences.sanitizeApiKey(newKey)
        preferences.saveApiKey(cleanKey)
        if (cleanKey.isNotBlank()) {
            preferences.setEnabled(true)
            validateStatus()
        }
    }

    fun toggleEnabled(enabled: Boolean) {
        preferences.setEnabled(enabled)
    }

    fun disconnect() {
        cancelTorBoxDeviceAuth()
        validationJob?.cancel()
        preferences.clearApiKey()
        _userStatus.value = null
        _torBoxStats.value = null
        _cloudDownloads.value = emptyList()
    }

    /**
     * Starts the TV Device Code authorization flow for TorBox.
     * Displays a 6-digit code and starts polling until authorized on phone/browser.
     */
    fun startTorBoxDeviceAuth() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            _deviceAuthState.value = TorBoxDeviceAuthUiState.Loading
            val auth = try {
                torBoxClient.startDeviceAuth()
            } catch (e: Exception) {
                _deviceAuthState.value = TorBoxDeviceAuthUiState.Error("Failed to start device authorization: ${e.localizedMessage}")
                return@launch
            }
            if (auth == null) {
                _deviceAuthState.value = TorBoxDeviceAuthUiState.Error("Failed to start device authorization with TorBox.")
                return@launch
            }

            var secondsLeft = auth.expiresIn
            _deviceAuthState.value = TorBoxDeviceAuthUiState.WaitingForApproval(
                userCode = auth.userCode,
                verificationUrl = auth.verificationUrl,
                verificationUrlComplete = auth.verificationUrlComplete,
                secondsRemaining = secondsLeft
            )

            val intervalMs = (auth.interval.coerceAtLeast(3)) * 1000L

            while (isActive && secondsLeft > 0) {
                delay(intervalMs)
                secondsLeft -= (intervalMs / 1000).toInt()

                try {
                    when (val result = torBoxClient.pollDeviceToken(auth.deviceCode)) {
                        is TorBoxTokenResult.Success -> {
                            _deviceAuthState.value = TorBoxDeviceAuthUiState.Approved(result.token)
                            saveApiKey(result.token)
                            break
                        }
                        is TorBoxTokenResult.Pending -> {
                            _deviceAuthState.value = TorBoxDeviceAuthUiState.WaitingForApproval(
                                userCode = auth.userCode,
                                verificationUrl = auth.verificationUrl,
                                verificationUrlComplete = auth.verificationUrlComplete,
                                secondsRemaining = secondsLeft.coerceAtLeast(0)
                            )
                        }
                        is TorBoxTokenResult.Expired -> {
                            _deviceAuthState.value = TorBoxDeviceAuthUiState.Error("Code expired. Please request a new code.")
                            break
                        }
                        is TorBoxTokenResult.Error -> {
                            if (result.message.contains("expired", ignoreCase = true)) {
                                _deviceAuthState.value = TorBoxDeviceAuthUiState.Error(result.message)
                                break
                            } else {
                                Log.w("DebridViewModel", "Transient TorBox polling notice: ${result.message}")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w("DebridViewModel", "Transient exception while polling TorBox token: ${e.message}")
                }
            }
        }
    }

    fun cancelTorBoxDeviceAuth() {
        pollingJob?.cancel()
        pollingJob = null
        _deviceAuthState.value = TorBoxDeviceAuthUiState.Idle
    }

    fun validateStatus() {
        validationJob?.cancel()
        val rawKey = preferences.apiKey.value
        val currentKey = preferences.sanitizeApiKey(rawKey)
        if (currentKey != rawKey && currentKey.isNotBlank()) {
            preferences.saveApiKey(currentKey)
        }
        val provider = preferences.selectedProvider.value
        if (currentKey.isBlank()) {
            _userStatus.value = AllDebridUserStatus(
                isValid = false,
                provider = provider,
                message = "No API key configured for ${provider.displayName}"
            )
            return
        }

        validationJob = viewModelScope.launch {
            _isValidating.value = true
            try {
                val status = client.checkStatus(provider, currentKey)
                _userStatus.value = status
                if (status.isValid) {
                    val summary = if (status.isPremium) {
                        "Active (${status.daysRemaining} days left) • @${status.username}"
                    } else {
                        "Free / Expired • @${status.username}"
                    }
                    preferences.saveUserInfo(summary, provider)
                    preferences.saveVerificationStatus(
                        isVerified = true,
                        isPremium = status.isPremium,
                        email = status.username,
                        days = status.daysRemaining,
                        plan = if (status.isPremium) "Premium" else "Free",
                        provider = provider
                    )

                    // Fetch extended stats & cloud downloads for TorBox
                    if (provider == DebridProviderType.TORBOX) {
                        launch {
                            _torBoxStats.value = torBoxClient.getUserStats(currentKey)
                        }
                        loadCloudDownloads()
                    }
                } else {
                    preferences.saveUserInfo("Invalid / Error: ${status.message}", provider)
                    preferences.clearVerification(provider)
                }
            } catch (e: Exception) {
                _userStatus.value = AllDebridUserStatus(
                    isValid = false,
                    provider = provider,
                    message = e.localizedMessage ?: "Connection error"
                )
            } finally {
                _isValidating.value = false
            }
        }
    }

    fun loadCloudDownloads() {
        val currentKey = preferences.apiKey.value.trim()
        val provider = preferences.selectedProvider.value
        if (currentKey.isBlank() || provider != DebridProviderType.TORBOX) return

        viewModelScope.launch {
            _isLoadingDownloads.value = true
            try {
                val list = torBoxClient.getUserTorrents(currentKey)
                _cloudDownloads.value = list
            } catch (e: Exception) {
                // Keep existing list on failure
            } finally {
                _isLoadingDownloads.value = false
            }
        }
    }

    fun deleteCloudTorrent(torrentId: Int, onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        val currentKey = preferences.apiKey.value.trim()
        if (currentKey.isBlank() || torrentId <= 0) {
            onResult(false, "Invalid TorBox configuration or torrent ID")
            return
        }

        viewModelScope.launch {
            val success = torBoxClient.controlTorrent(currentKey, torrentId, "delete")
            if (success) {
                _cloudDownloads.value = _cloudDownloads.value.filter { it.id != torrentId }
                onResult(true, "Torrent successfully deleted from TorBox cloud")
            } else {
                onResult(false, "Could not delete torrent from TorBox cloud")
            }
        }
    }

    suspend fun getStreamUrl(torrentId: Int, fileId: Int? = null): String? {
        val currentKey = preferences.apiKey.value.trim()
        if (currentKey.isBlank() || torrentId <= 0) return null
        return torBoxClient.requestDownloadLink(currentKey, torrentId, fileId)
    }
}
