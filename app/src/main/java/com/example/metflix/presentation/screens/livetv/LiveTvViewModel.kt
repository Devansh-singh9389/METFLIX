package com.example.metflix.presentation.screens.livetv

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.metflix.data.entities.LiveChannel
import com.example.metflix.data.repositories.LiveTvRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
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

    val currentPlaylistUrl: StateFlow<String> = repository.getCurrentPlaylistUrl()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ""
        )

    val uiState: StateFlow<LiveTvUiState> = combine(
        repository.getChannels(),
        selectedCategory,
        sortOrder,
        isRefreshing,
        repository.getCurrentPlaylistUrl()
    ) { channels, category, sort, refreshing, playlistUrl ->
        if (channels.isEmpty() && refreshing) {
            LiveTvUiState.Loading
        } else {
            val categories = buildList {
                add("All")
                add("Favorites")
                val dynamic = channels.map { it.category }
                    .distinct()
                    .filter { it.isNotBlank() && !it.equals("All", ignoreCase = true) && !it.equals("Favorites", ignoreCase = true) }
                    .sorted()
                addAll(dynamic)
            }

            val categoryFiltered = when {
                category.equals("All", ignoreCase = true) -> channels
                category.equals("Favorites", ignoreCase = true) -> channels.filter { it.isFavorite }
                else -> channels.filter { it.category.equals(category, ignoreCase = true) }
            }

            val sorted = when (sort) {
                LiveTvSortOrder.ChannelNumber -> categoryFiltered.sortedBy { it.channelNumber }
                LiveTvSortOrder.NameAsc -> categoryFiltered.sortedBy { it.name.lowercase() }
            }

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
                currentPlaylistUrl = playlistUrl
            )
        }
    }
    .flowOn(Dispatchers.Default)
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
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
        viewModelScope.launch(Dispatchers.IO) {
            repository.toggleFavorite(channelId)
        }
    }

    fun selectPlaylistUrl(url: String) {
        viewModelScope.launch(Dispatchers.IO) {
            isRefreshing.value = true
            val result = repository.setPlaylistUrl(url)
            isRefreshing.value = false
            result.onSuccess { count ->
                statusMessage.value = "Loaded $count channels"
            }.onFailure { err ->
                statusMessage.value = "Failed to switch playlist: ${err.message}"
            }
        }
    }

    fun refreshChannels() {
        viewModelScope.launch(Dispatchers.IO) {
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
