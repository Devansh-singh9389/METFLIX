package com.example.metflix.presentation.screens.livetv

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.metflix.data.entities.LiveChannel
import com.example.metflix.data.repositories.LiveTvRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class LiveTvSortOrder(val title: String) {
    ChannelNumber("Channel No."),
    NameAsc("Name (A-Z)")
}

@HiltViewModel
class LiveTvViewModel @Inject constructor(
    private val repository: LiveTvRepository
) : ViewModel() {

    private val selectedCategory = MutableStateFlow("All")
    private val _focusedChannel = MutableStateFlow<LiveChannel?>(null)
    val focusedChannel: StateFlow<LiveChannel?> = _focusedChannel.asStateFlow()

    private val isRefreshing = MutableStateFlow(false)
    private val statusMessage = MutableStateFlow<String?>(null)
    private val sortOrder = MutableStateFlow(LiveTvSortOrder.ChannelNumber)

    // Ticks every 30s to smoothly update live progress bars without full-screen reloads
    private val currentTimeFlow = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(30_000)
        }
    }

    val currentPlaylistUrl: StateFlow<String> = repository.getCurrentPlaylistUrl()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ""
        )

    private val filteredChannelsFlow = combine(
        repository.getChannels(),
        selectedCategory,
        sortOrder
    ) { channels, category, sort ->
        val categoryFiltered = when {
            category.equals("All", ignoreCase = true) -> channels
            category.equals("Favorites", ignoreCase = true) -> channels.filter { it.isFavorite }
            else -> channels.filter { it.category.equals(category, ignoreCase = true) }
        }

        when (sort) {
            LiveTvSortOrder.ChannelNumber -> categoryFiltered.sortedBy { it.channelNumber }
            LiveTvSortOrder.NameAsc -> categoryFiltered.sortedBy { it.name.lowercase() }
        }
    }

    val uiState: StateFlow<LiveTvUiState> = combine(
        repository.getChannels(),
        filteredChannelsFlow,
        repository.getCategories(),
        selectedCategory,
        isRefreshing,
        sortOrder,
        repository.getCurrentPlaylistUrl(),
        currentTimeFlow
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        val channels = args[0] as List<LiveChannel>
        @Suppress("UNCHECKED_CAST")
        val sorted = args[1] as List<LiveChannel>
        @Suppress("UNCHECKED_CAST")
        val categories = args[2] as List<String>
        val category = args[3] as String
        val refreshing = args[4] as Boolean
        val sort = args[5] as LiveTvSortOrder
        val playlistUrl = args[6] as String
        val nowMs = args[7] as Long

        if (channels.isEmpty() && refreshing) {
            LiveTvUiState.Loading
        } else {
            // Keep focusedChannel in sync if current is null or not in category
            if (_focusedChannel.value == null || sorted.none { it.id == _focusedChannel.value?.id }) {
                _focusedChannel.value = sorted.firstOrNull()
            }

            LiveTvUiState.Ready(
                allChannels = channels,
                filteredChannels = sorted,
                categories = categories,
                selectedCategory = category,
                focusedChannel = _focusedChannel.value,
                isRefreshing = refreshing,
                totalCount = channels.size,
                searchQuery = "",
                sortOrder = sort,
                currentPlaylistUrl = playlistUrl,
                currentTimeMs = nowMs
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Lazily,
        initialValue = LiveTvUiState.Loading
    )

    fun onCategorySelected(category: String) {
        selectedCategory.value = category
    }

    fun onChannelFocused(channel: LiveChannel) {
        _focusedChannel.value = channel
    }

    fun onSearchQueryChange(query: String) {
        // No-op (search bar removed for minimal TV UI)
    }

    fun onSortOrderChange(order: LiveTvSortOrder) {
        sortOrder.value = order
    }

    fun toggleFavorite(channelId: String) {
        viewModelScope.launch {
            repository.toggleFavorite(channelId)
        }
    }

    fun selectPlaylistUrl(url: String) {
        viewModelScope.launch {
            isRefreshing.value = true
            repository.setPlaylistUrl(url)
            isRefreshing.value = false
        }
    }

    fun refreshChannels() {
        viewModelScope.launch {
            isRefreshing.value = true
            val result = repository.refreshChannels()
            isRefreshing.value = false
            result.onSuccess { count ->
                statusMessage.value = "Loaded $count channels"
            }.onFailure { err ->
                statusMessage.value = "Refresh failed: ${err.message}"
            }
        }
    }
}

@Immutable
sealed interface LiveTvUiState {
    data object Loading : LiveTvUiState
    data class Error(val message: String) : LiveTvUiState
    data class Ready(
        val allChannels: List<LiveChannel>,
        val filteredChannels: List<LiveChannel>,
        val categories: List<String>,
        val selectedCategory: String,
        val focusedChannel: LiveChannel?,
        val isRefreshing: Boolean,
        val totalCount: Int,
        val searchQuery: String,
        val sortOrder: LiveTvSortOrder,
        val currentPlaylistUrl: String,
        val currentTimeMs: Long = System.currentTimeMillis()
    ) : LiveTvUiState
}
