package com.example.metflix.data.network.anime.models

import com.example.metflix.data.entities.Movie
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AnimeEpisodeItem(
    @SerialName("episode_code")
    val episodeCode: String? = null,
    val title: String? = null,
    val url: String = "",
    val route: String = "",
    val thumbnail: String? = null
) {
    fun toMovie(seriesTitle: String = ""): Movie {
        val cleanRoute = route.trim().removePrefix("/")
        val slug = cleanRoute.substringAfterLast("/").ifEmpty { "ep" }
        return Movie(
            id = cleanRoute.ifEmpty { slug },
            videoUri = "https://metflix-anime.onrender.com/api/$cleanRoute",
            subtitleUri = null,
            posterUri = thumbnail.orEmpty(),
            name = title ?: episodeCode ?: "Episode",
            description = if (seriesTitle.isNotEmpty()) "$seriesTitle • ${episodeCode.orEmpty()}" else episodeCode.orEmpty()
        )
    }
}

@Serializable
data class AnimeSeasonItem(
    @SerialName("season_name")
    val seasonName: String? = null,
    @SerialName("season_number")
    val seasonNumber: Int = 1,
    @SerialName("episodes_count")
    val episodesCount: Int = 0,
    val episodes: List<AnimeEpisodeItem> = emptyList()
)

@Serializable
data class AnimeDecodedStream(
    val language: String? = null,
    val link: String? = null
)

@Serializable
data class AnimeStreamingServer(
    @SerialName("server_id")
    val serverId: String? = null,
    @SerialName("server_name")
    val serverName: String? = null,
    val audio: String? = null,
    @SerialName("embed_url")
    val embedUrl: String? = null,
    @SerialName("direct_stream_url")
    val directStreamUrl: String? = null,
    @SerialName("direct_stream_type")
    val directStreamType: String? = null,
    @SerialName("decoded_streams")
    val decodedStreams: List<AnimeDecodedStream>? = null,
    @SerialName("direct_stream_error")
    val directStreamError: String? = null
)

@Serializable
data class AnimeSeriesResponse(
    val type: String? = null,
    val slug: String = "",
    val title: String? = null,
    val url: String = "",
    val route: String = "",
    val poster: String? = null,
    val synopsis: String? = null,
    val genres: List<String> = emptyList(),
    val languages: List<String> = emptyList(),
    @SerialName("total_seasons")
    val totalSeasons: Int = 0,
    val seasons: List<AnimeSeasonItem> = emptyList()
)

@Serializable
data class AnimeRecommendedMovie(
    val title: String? = null,
    val url: String = "",
    val route: String = "",
    val poster: String? = null
) {
    fun toMovie(): Movie {
        val cleanRoute = route.trim().removePrefix("/")
        val slug = cleanRoute.substringAfterLast("/").ifEmpty { "movie" }
        return Movie(
            id = cleanRoute.ifEmpty { slug },
            videoUri = "https://metflix-anime.onrender.com/api/$cleanRoute",
            subtitleUri = null,
            posterUri = poster.orEmpty(),
            name = title ?: "Recommended Movie",
            description = "Movie"
        )
    }
}

@Serializable
data class AnimeMovieResponse(
    val type: String? = null,
    val slug: String = "",
    val title: String? = null,
    @SerialName("post_id")
    val postId: String? = null,
    val url: String = "",
    val route: String = "",
    val poster: String? = null,
    val synopsis: String? = null,
    val duration: String? = null,
    @SerialName("release_year")
    val releaseYear: String? = null,
    val genres: List<String> = emptyList(),
    val languages: List<String> = emptyList(),
    @SerialName("streaming_servers")
    val streamingServers: List<AnimeStreamingServer> = emptyList(),
    @SerialName("vlc_url")
    val vlcUrl: String? = null,
    @SerialName("vlc_stream_type")
    val vlcStreamType: String? = null,
    @SerialName("recommended_movies")
    val recommendedMovies: List<AnimeRecommendedMovie> = emptyList()
)

@Serializable
data class AnimeCategoryResponse(
    val type: String? = null,
    val title: String? = null,
    val category: String? = null,
    @SerialName("category_type")
    val categoryType: String? = null,
    val slug: String = "",
    val url: String = "",
    @SerialName("total_items")
    val totalItems: Int = 0,
    val items: List<AnimeHomeItem> = emptyList(),
    val pagination: AnimePagination? = null
)

@Serializable
data class AnimeEpisodeResponse(
    val type: String? = null,
    @SerialName("episode_title")
    val episodeTitle: String? = null,
    @SerialName("series_title")
    val seriesTitle: String? = null,
    @SerialName("current_url")
    val currentUrl: String = "",
    val slug: String = "",
    val season: Int? = null,
    val episode: Int? = null,
    val poster: String? = null,
    @SerialName("vlc_url")
    val vlcUrl: String? = null,
    @SerialName("vlc_stream_type")
    val vlcStreamType: String? = null,
    @SerialName("streaming_servers")
    val streamingServers: List<AnimeStreamingServer> = emptyList()
)
