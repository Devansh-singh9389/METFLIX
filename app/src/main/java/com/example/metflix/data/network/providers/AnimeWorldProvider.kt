package com.example.metflix.data.network.providers

import android.util.Log
import com.example.metflix.data.entities.MediaId
import com.example.metflix.data.entities.StreamCandidate
import com.example.metflix.data.entities.StreamFormat
import com.example.metflix.data.network.resolvers.AnimeScraper
import com.example.metflix.data.network.resolvers.StreamServer
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnimeWorldProvider @Inject constructor(
    private val animeScraper: AnimeScraper
) : StreamProvider {

    companion object {
        private const val TAG = "AnimeWorldProvider"
    }

    override val id: String = "anime_world"
    override val displayName: String = "Anime: Multi-Audio (Jap+Hin+Eng)"
    override val priority: Int = 100

    override fun canHandle(mediaId: MediaId): Boolean =
        mediaId is MediaId.Anime

    override suspend fun findStreams(
        mediaId: MediaId,
        title: String,
        releaseYear: String
    ): List<StreamCandidate> {
        val animeId = mediaId as? MediaId.Anime ?: return emptyList()
        val queryTitle = animeId.title.ifBlank { title }
        val episode = animeId.absoluteEpisodeNumber ?: animeId.episodeNumber

        Log.d(TAG, "Resolving anime candidate for '$queryTitle' Ep $episode (direct title resolution, no TMDB dependency)")

        val dummyServer = StreamServer(
            id = id,
            name = displayName,
            description = "Anime Multi-Audio (Jap+Hin+Eng)",
            rank = 1,
            speed = "Ultra",
            isDirect = true,
            audioLanguages = listOf("Japanese", "Hindi", "English"),
            badge = "Multi-Audio"
        )

        val resolved = animeScraper.resolveAnimeStream(
            tmdbId = "anime_direct",
            animeTitle = queryTitle,
            season = animeId.seasonNumber,
            episode = episode,
            server = dummyServer
        ) ?: return emptyList()

        val format = when {
            resolved.isHls || resolved.streamUrl.contains(".m3u8") -> StreamFormat.HLS
            resolved.streamUrl.contains(".mkv") -> StreamFormat.MKV
            else -> StreamFormat.MP4
        }

        return listOf(
            StreamCandidate(
                providerId = id,
                providerName = displayName,
                streamUrl = resolved.streamUrl,
                format = format,
                reportedLanguages = resolved.audioLanguages.ifEmpty { listOf("Japanese", "Hindi", "English") },
                isAudioVerified = resolved.audioLanguages.isNotEmpty(),
                headers = resolved.headers,
                latencyMs = resolved.latencyMs ?: 65L,
                quality = "1080p",
                backupUrls = resolved.backupUrls,
                isDirectStream = resolved.isDirectStream
            )
        )
    }
}
