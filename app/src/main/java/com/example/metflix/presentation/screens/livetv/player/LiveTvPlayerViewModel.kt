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
    private val activeCategoryFlow = MutableStateFlow<String?>(null)

    val uiState: StateFlow<LiveTvPlayerUiState> = combine(
        repository.getChannels(),
        currentChannelIdFlow,
        activeCategoryFlow
    ) { channels, selectedId, category ->
        if (channels.isEmpty()) {
            LiveTvPlayerUiState.Loading
        } else {
            val channel = channels.firstOrNull { it.id == selectedId }
                ?: channels.firstOrNull()

            if (channel == null) {
                LiveTvPlayerUiState.Error("No channel found")
            } else {
                val effectiveCategory = category ?: channel.category
                val filtered = if (effectiveCategory.isBlank() || effectiveCategory.equals("All", ignoreCase = true)) {
                    channels
                } else {
                    val matching = channels.filter { it.category.equals(effectiveCategory, ignoreCase = true) }
                    if (matching.isNotEmpty()) matching else channels
                }

                val currentIndex = filtered.indexOfFirst { it.id == channel.id }
                    .let { if (it >= 0) it else channels.indexOfFirst { ch -> ch.id == channel.id }.coerceAtLeast(0) }

                LiveTvPlayerUiState.Ready(
                    currentChannel = channel,
                    channels = channels,
                    filteredChannels = filtered,
                    currentCategory = effectiveCategory,
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
        activeCategoryFlow.value = channel.category
        currentChannelIdFlow.value = channel.id
    }

    fun selectChannelByNumber(channelNumber: Int): Boolean {
        val state = uiState.value as? LiveTvPlayerUiState.Ready ?: return false
        val matchedChannel = state.channels.firstOrNull { it.channelNumber == channelNumber }
        return if (matchedChannel != null) {
            activeCategoryFlow.value = matchedChannel.category
            currentChannelIdFlow.value = matchedChannel.id
            true
        } else {
            false
        }
    }

    fun nextChannel() {
        val state = uiState.value as? LiveTvPlayerUiState.Ready ?: return
        val list = if (state.filteredChannels.isNotEmpty()) state.filteredChannels else state.channels
        if (list.isNotEmpty()) {
            val currIdx = list.indexOfFirst { it.id == state.currentChannel.id }
            val nextIndex = if (currIdx != -1) (currIdx + 1) % list.size else 0
            currentChannelIdFlow.value = list[nextIndex].id
        }
    }

    fun previousChannel() {
        val state = uiState.value as? LiveTvPlayerUiState.Ready ?: return
        val list = if (state.filteredChannels.isNotEmpty()) state.filteredChannels else state.channels
        if (list.isNotEmpty()) {
            val currIdx = list.indexOfFirst { it.id == state.currentChannel.id }
            val prevIndex = if (currIdx != -1) {
                if (currIdx - 1 < 0) list.size - 1 else currIdx - 1
            } else 0
            currentChannelIdFlow.value = list[prevIndex].id
        }
    }

    fun setCategory(category: String) {
        activeCategoryFlow.value = category
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
        val filteredChannels: List<LiveChannel> = channels,
        val currentCategory: String = currentChannel.category,
        val currentIndex: Int
    ) : LiveTvPlayerUiState
}
