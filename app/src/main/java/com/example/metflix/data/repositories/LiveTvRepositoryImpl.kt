package com.example.metflix.data.repositories

import android.content.Context
import android.util.Log
import com.example.metflix.data.entities.LiveChannel
import com.example.metflix.data.util.M3uParser
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Singleton
class LiveTvRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : LiveTvRepository {

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val favoriteIdsFlow = MutableStateFlow<Set<String>>(loadFavoritesFromPrefs())
    private val playlistUrlFlow = MutableStateFlow(
        prefs.getString(KEY_PLAYLIST_URL, DEFAULT_IPTV_URL) ?: DEFAULT_IPTV_URL
    )
    private val rawChannelsFlow = MutableStateFlow<List<LiveChannel>>(emptyList())

    // Combined channels stream taking favorite state into account
    private val channelsFlow: Flow<List<LiveChannel>> = combine(rawChannelsFlow, favoriteIdsFlow) { channels, favIds ->
        channels.map { ch ->
            ch.copy(isFavorite = favIds.contains(ch.id))
        }
    }

    init {
        repositoryScope.launch {
            loadInitialChannels()
            // In the background, optionally sync updated channels from active playlist
            refreshChannels(playlistUrlFlow.value)
        }
    }

    override fun getChannels(): Flow<List<LiveChannel>> = channelsFlow

    override fun getChannelsByCategory(category: String): Flow<List<LiveChannel>> {
        return channelsFlow.map { channels ->
            when {
                category.equals("All", ignoreCase = true) -> channels
                category.equals("Favorites", ignoreCase = true) -> channels.filter { it.isFavorite }
                else -> channels.filter { it.category.equals(category, ignoreCase = true) }
            }
        }
    }

    override fun getCategories(): Flow<List<String>> {
        return channelsFlow.map { channels ->
            val set = linkedSetOf("All", "Favorites")
            val dynamicCategories = channels
                .map { it.category }
                .distinct()
                .filter { it.isNotBlank() && !it.equals("All", ignoreCase = true) && !it.equals("Favorites", ignoreCase = true) }
                .sorted()
            set.addAll(dynamicCategories)
            set.toList()
        }
    }

    override fun getFavoriteChannels(): Flow<List<LiveChannel>> {
        return channelsFlow.map { list -> list.filter { it.isFavorite } }
    }

    override suspend fun getChannelById(channelId: String): LiveChannel? = withContext(Dispatchers.Default) {
        val favs = favoriteIdsFlow.value
        rawChannelsFlow.value.firstOrNull { it.id == channelId }?.let {
            it.copy(isFavorite = favs.contains(it.id))
        }
    }

    override suspend fun toggleFavorite(channelId: String) = withContext(Dispatchers.IO) {
        val currentFavs = favoriteIdsFlow.value.toMutableSet()
        if (currentFavs.contains(channelId)) {
            currentFavs.remove(channelId)
        } else {
            currentFavs.add(channelId)
        }
        favoriteIdsFlow.value = currentFavs
        prefs.edit().putStringSet(KEY_FAVORITES, currentFavs).apply()
    }

    override fun getCurrentPlaylistUrl(): Flow<String> = playlistUrlFlow

    override suspend fun setPlaylistUrl(url: String): Result<Int> = withContext(Dispatchers.IO) {
        val target = if (url.isBlank()) DEFAULT_IPTV_URL else url.trim()
        prefs.edit().putString(KEY_PLAYLIST_URL, target).apply()
        playlistUrlFlow.value = target
        refreshChannels(target)
    }

    override suspend fun refreshChannels(playlistUrl: String?): Result<Int> = withContext(Dispatchers.IO) {
        val targetUrl = playlistUrl ?: playlistUrlFlow.value
        runCatching {
            Log.d(TAG, "Fetching live channels from: $targetUrl")
            val url = URL(targetUrl)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 12000
                readTimeout = 15000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Android TV; MetFlix)")
            }

            val statusCode = connection.responseCode
            if (statusCode in 200..299) {
                val parsed = M3uParser.parse(connection.inputStream)
                connection.disconnect()

                if (parsed.isNotEmpty()) {
                    // Prepend default curated channels so guaranteed working channels remain at top
                    val curated = loadBundledDefaultChannels()
                    val merged = mergeChannels(curated, parsed)

                    rawChannelsFlow.value = merged
                    saveChannelsToCache(merged)
                    Log.d(TAG, "Successfully loaded ${merged.size} live channels")
                    merged.size
                } else {
                    Log.w(TAG, "M3U fetch returned 0 channels")
                    rawChannelsFlow.value.size
                }
            } else {
                connection.disconnect()
                throw Exception("Failed to fetch M3U playlist. Status code: $statusCode")
            }
        }.onFailure { err ->
            Log.w(TAG, "Error fetching remote IPTV playlist, retaining cached/bundled channels: ${err.message}")
        }
    }

    override suspend fun searchChannels(query: String): List<LiveChannel> = withContext(Dispatchers.Default) {
        if (query.isBlank()) return@withContext emptyList()
        val q = query.trim().lowercase()
        val favs = favoriteIdsFlow.value
        rawChannelsFlow.value.filter { ch ->
            ch.name.lowercase().contains(q) ||
            ch.category.lowercase().contains(q) ||
            ch.channelNumber.toString().contains(q)
        }.map { it.copy(isFavorite = favs.contains(it.id)) }
    }

    private suspend fun loadInitialChannels() = withContext(Dispatchers.IO) {
        // 1. Try cache file first
        val cached = loadChannelsFromCache()
        if (cached.isNotEmpty()) {
            rawChannelsFlow.value = cached
            Log.d(TAG, "Loaded ${cached.size} channels from local cache")
            return@withContext
        }

        // 2. Fall back to bundled default channels in assets
        val bundled = loadBundledDefaultChannels()
        rawChannelsFlow.value = bundled
        Log.d(TAG, "Loaded ${bundled.size} channels from bundled assets")
    }

    private fun loadBundledDefaultChannels(): List<LiveChannel> {
        return try {
            context.assets.open(DEFAULT_CHANNELS_ASSET).use { inputStream ->
                val reader = InputStreamReader(inputStream)
                val text = reader.readText()
                json.decodeFromString<List<LiveChannel>>(text)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading bundled channels", e)
            emptyList()
        }
    }

    private fun loadChannelsFromCache(): List<LiveChannel> {
        val cacheFile = File(context.filesDir, CACHE_FILE_NAME)
        if (!cacheFile.exists()) return emptyList()
        return try {
            val text = cacheFile.readText()
            json.decodeFromString<List<LiveChannel>>(text)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read cached channels", e)
            emptyList()
        }
    }

    private fun saveChannelsToCache(channels: List<LiveChannel>) {
        try {
            val cacheFile = File(context.filesDir, CACHE_FILE_NAME)
            val text = json.encodeToString(channels)
            cacheFile.writeText(text)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save channels to cache", e)
        }
    }

    private fun mergeChannels(primary: List<LiveChannel>, secondary: List<LiveChannel>): List<LiveChannel> {
        val result = ArrayList<LiveChannel>(primary.size + secondary.size)
        val seenUrls = HashSet<String>()
        val seenNames = HashSet<String>()

        primary.forEach {
            seenUrls.add(it.streamUrl)
            seenNames.add(it.name.lowercase())
            result.add(it)
        }

        var counter = if (primary.isNotEmpty()) primary.maxOf { it.channelNumber } + 1 else 101

        secondary.forEach { ch ->
            val normalizedName = ch.name.lowercase()
            if (!seenUrls.contains(ch.streamUrl) && !seenNames.contains(normalizedName)) {
                seenUrls.add(ch.streamUrl)
                seenNames.add(normalizedName)
                result.add(ch.copy(channelNumber = counter++))
            }
        }

        return result
    }

    private fun loadFavoritesFromPrefs(): Set<String> {
        return prefs.getStringSet(KEY_FAVORITES, emptySet()) ?: emptySet()
    }

    companion object {
        private const val TAG = "LiveTvRepository"
        private const val PREFS_NAME = "metflix_live_tv_prefs"
        private const val KEY_FAVORITES = "favorite_channel_ids"
        private const val KEY_PLAYLIST_URL = "custom_live_playlist_url"
        private const val CACHE_FILE_NAME = "live_channels_cache.json"
        private const val DEFAULT_CHANNELS_ASSET = "default_channels.json"
        const val DEFAULT_IPTV_URL = "https://iptv-org.github.io/iptv/languages/eng.m3u"
    }
}
