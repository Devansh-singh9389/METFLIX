package com.example.metflix.presentation.screens.livetv

import androidx.compose.runtime.Immutable
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

enum class LiveTvSortOrder(val title: String) {
    ChannelNumber("Channel No."),
    NameAsc("Name (A-Z)")
}

@HiltViewModel
class LiveTvViewModel @Inject constructor(
    private val repository: LiveTvRepository
) : ViewModel() {

    private val selectedCategory = MutableStateFlow("All")
    private val focusedChannel = MutableStateFlow<LiveChannel?>(null)
    private val isRefreshing = MutableStateFlow(false)
    private val statusMessage = MutableStateFlow<String?>(null)
    private val searchQuery = MutableStateFlow("")
    private val sortOrder = MutableStateFlow(LiveTvSortOrder.ChannelNumber)

    val currentPlaylistUrl: StateFlow<String> = repository.getCurrentPlaylistUrl()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ""
        )

    private val filteredChannelsFlow = combine(
        repository.getChannels(),
        selectedCategory,
        searchQuery,
        sortOrder
    ) { channels, category, query, sort ->
        val categoryFiltered = when {
            category.equals("All", ignoreCase = true) -> channels
            category.equals("Favorites", ignoreCase = true) -> channels.filter { it.isFavorite }
            else -> channels.filter { it.category.equals(category, ignoreCase = true) }
        }

        val searchFiltered = if (query.isBlank()) {
            categoryFiltered
        } else {
            val q = query.trim().lowercase()
            categoryFiltered.filter {
                it.name.lowercase().contains(q) ||
                it.channelNumber.toString().contains(q) ||
                it.category.lowercase().contains(q)
            }
        }

        when (sort) {
            LiveTvSortOrder.ChannelNumber -> searchFiltered.sortedBy { it.channelNumber }
            LiveTvSortOrder.NameAsc -> searchFiltered.sortedBy { it.name.lowercase() }
        }
    }

    val uiState: StateFlow<LiveTvUiState> = combine(
        repository.getChannels(),
        filteredChannelsFlow,
        repository.getCategories(),
        selectedCategory,
        focusedChannel,
        isRefreshing,
        searchQuery,
        sortOrder,
        repository.getCurrentPlaylistUrl()
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        val channels = args[0] as List<LiveChannel>
        @Suppress("UNCHECKED_CAST")
        val sorted = args[1] as List<LiveChannel>
        @Suppress("UNCHECKED_CAST")
        val categories = args[2] as List<String>
        val category = args[3] as String
        val focused = args[4] as LiveChannel?
        val refreshing = args[5] as Boolean
        val query = args[6] as String
        val sort = args[7] as LiveTvSortOrder
        val playlistUrl = args[8] as String

        if (channels.isEmpty() && refreshing) {
            LiveTvUiState.Loading
        } else {
            val currentFocused = focused?.let { f ->
                sorted.firstOrNull { it.id == f.id }
            } ?: sorted.firstOrNull()

            LiveTvUiState.Ready(
                allChannels = channels,
                filteredChannels = sorted,
                categories = categories,
                selectedCategory = category,
                focusedChannel = currentFocused,
                isRefreshing = refreshing,
                totalCount = channels.size,
                searchQuery = query,
                sortOrder = sort,
                currentPlaylistUrl = playlistUrl
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
        focusedChannel.value = channel
    }

    fun onSearchQueryChange(query: String) {
        searchQuery.value = query
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
        val currentPlaylistUrl: String
    ) : LiveTvUiState
}
