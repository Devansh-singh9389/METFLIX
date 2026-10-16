package com.example.metflix.data.repositories

import com.example.metflix.data.entities.LiveChannel
import kotlinx.coroutines.flow.Flow

interface LiveTvRepository {
    fun getChannels(): Flow<List<LiveChannel>>
    fun getChannelsByCategory(category: String): Flow<List<LiveChannel>>
    fun getCategories(): Flow<List<String>>
    fun getFavoriteChannels(): Flow<List<LiveChannel>>
    suspend fun getChannelById(channelId: String): LiveChannel?
    suspend fun toggleFavorite(channelId: String)
    suspend fun refreshChannels(playlistUrl: String? = null): Result<Int>
    suspend fun searchChannels(query: String): List<LiveChannel>
    fun getCurrentPlaylistUrl(): Flow<String>
    suspend fun setPlaylistUrl(url: String): Result<Int>
}
