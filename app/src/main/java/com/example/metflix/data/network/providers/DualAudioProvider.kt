package com.example.metflix.data.network.providers

import android.util.Log
import com.example.metflix.data.entities.MediaId
import com.example.metflix.data.entities.StreamCandidate
import com.example.metflix.data.entities.StreamFormat
import com.example.metflix.data.network.resolvers.DualAudioScraper
import com.example.metflix.data.network.resolvers.StreamServer
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DualAudioProvider @Inject constructor(
    private val dualAudioScraper: DualAudioScraper
) : StreamProvider {

    companion object {
        private const val TAG = "DualAudioProvider"
    }

    override val id: String = "dualaudio"
    override val displayName: String = "Server 1: Dual-Audio (Hindi+Eng)"
    override val priority: Int = 100

    override fun canHandle(mediaId: MediaId): Boolean =
        mediaId is MediaId.Movie || mediaId is MediaId.TvShow

    override suspend fun findStreams(
        mediaId: MediaId,
        title: String,
        releaseYear: String
    ): List<StreamCandidate> {
        val tmdbId = when (mediaId) {
            is MediaId.Movie -> mediaId.tmdbId
            is MediaId.TvShow -> mediaId.tmdbId
            is MediaId.Anime -> return emptyList()
        }

        val isTvShow = mediaId is MediaId.TvShow
        val season = (mediaId as? MediaId.TvShow)?.seasonNumber ?: 1
        val episode = (mediaId as? MediaId.TvShow)?.episodeNumber ?: 1
        val imdbId = (mediaId as? MediaId.Movie)?.imdbId ?: (mediaId as? MediaId.TvShow)?.imdbId

        val dummyServer = StreamServer(
            id = id,
            name = displayName,
            description = "Direct Dual-Audio (Hindi+Eng)",
            rank = 1,
            speed = "Fast",
            isDirect = true,
            audioLanguages = listOf("Hindi", "English"),
            badge = "Hindi+Eng"
        )

        val resolved = dualAudioScraper.resolveDualAudioStream(
            tmdbId = tmdbId,
            movieTitle = title,
            releaseYear = releaseYear,
            server = dummyServer,
            isTvShow = isTvShow,
            season = season,
            episode = episode,
            imdbId = imdbId
        ) ?: return emptyList()

        val format = when {
            resolved.isHls || resolved.streamUrl.contains(".m3u8") -> StreamFormat.HLS
            resolved.streamUrl.contains(".mkv") -> StreamFormat.MKV
            resolved.streamUrl.contains(".mp4") -> StreamFormat.MP4
            else -> StreamFormat.UNKNOWN
        }

        val isVerified = resolved.audioLanguages.any { it.equals("Hindi", ignoreCase = true) } &&
                (resolved.streamUrl.contains("r2.cloudflarestorage.com") || resolved.streamUrl.contains(".mkv"))

        Log.d(TAG, "DualAudioProvider resolved stream: ${resolved.streamUrl} (verified: $isVerified)")

        return listOf(
            StreamCandidate(
                providerId = id,
                providerName = displayName,
                streamUrl = resolved.streamUrl,
                format = format,
                reportedLanguages = resolved.audioLanguages.ifEmpty { listOf("Hindi", "English") },
                isAudioVerified = isVerified,
                headers = resolved.headers,
                latencyMs = resolved.latencyMs ?: 80L,
                quality = "1080p",
                backupUrls = resolved.backupUrls,
                isDirectStream = resolved.isDirectStream
            )
        )
    }
}
