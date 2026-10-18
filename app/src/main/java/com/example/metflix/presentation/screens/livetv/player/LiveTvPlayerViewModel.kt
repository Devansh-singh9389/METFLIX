package com.example.metflix.presentation.screens.livetv.player

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.metflix.data.entities.LiveChannel
import com.example.metflix.data.repositories.LiveTvRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class LiveTvPlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: LiveTvRepository
) : ViewModel() {

    private val initialChannelId: String? = savedStateHandle.get<String>(LiveTvPlayerScreen.ChannelIdBundleKey)?.let {
        android.net.Uri.decode(it)
    }
    private val currentChannelIdFlow = MutableStateFlow(initialChannelId)

    val uiState: StateFlow<LiveTvPlayerUiState> = combine(
        repository.getChannels(),
        currentChannelIdFlow
    ) { channels, selectedId ->
        if (channels.isEmpty()) {
            LiveTvPlayerUiState.Loading
        } else {
            val channel = channels.firstOrNull { it.id == selectedId }
                ?: channels.firstOrNull()

            if (channel == null) {
                LiveTvPlayerUiState.Error("No channel found")
            } else {
                val currentIndex = channels.indexOfFirst { it.id == channel.id }.coerceAtLeast(0)
                LiveTvPlayerUiState.Ready(
                    currentChannel = channel,
                    channels = channels,
                    currentIndex = currentIndex
                )
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LiveTvPlayerUiState.Loading
    )

    fun selectChannel(channel: LiveChannel) {
        currentChannelIdFlow.value = channel.id
    }

    fun selectChannelByNumber(channelNumber: Int): Boolean {
        val state = uiState.value as? LiveTvPlayerUiState.Ready ?: return false
        val matchedChannel = state.channels.firstOrNull { it.channelNumber == channelNumber }
        return if (matchedChannel != null) {
            currentChannelIdFlow.value = matchedChannel.id
            true
        } else {
            false
        }
    }

    fun nextChannel() {
        val state = uiState.value as? LiveTvPlayerUiState.Ready ?: return
        if (state.channels.isNotEmpty()) {
            val nextIndex = (state.currentIndex + 1) % state.channels.size
            currentChannelIdFlow.value = state.channels[nextIndex].id
        }
    }

    fun previousChannel() {
        val state = uiState.value as? LiveTvPlayerUiState.Ready ?: return
        if (state.channels.isNotEmpty()) {
            val prevIndex = if (state.currentIndex - 1 < 0) state.channels.size - 1 else state.currentIndex - 1
            currentChannelIdFlow.value = state.channels[prevIndex].id
        }
    }

    fun toggleFavorite() {
        val state = uiState.value as? LiveTvPlayerUiState.Ready ?: return
        viewModelScope.launch {
            repository.toggleFavorite(state.currentChannel.id)
        }
    }
}

@Immutable
sealed interface LiveTvPlayerUiState {
    data object Loading : LiveTvPlayerUiState
    data class Error(val message: String) : LiveTvPlayerUiState
    data class Ready(
        val currentChannel: LiveChannel,
        val channels: List<LiveChannel>,
        val currentIndex: Int
    ) : LiveTvPlayerUiState
}
