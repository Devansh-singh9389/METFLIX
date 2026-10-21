package com.example.metflix.data.network.anime.models

import com.example.metflix.data.entities.Movie
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AnimePagination(
    @SerialName("current_page")
    val currentPage: Int = 1,
    @SerialName("total_pages")
    val totalPages: Int = 1,
    @SerialName("previous_url")
    val previousUrl: String? = null,
    @SerialName("previous_route")
    val previousRoute: String? = null,
    @SerialName("next_url")
    val nextUrl: String? = null,
    @SerialName("next_route")
    val nextRoute: String? = null
)

@Serializable
data class AnimeNavbarItem(
    val label: String = "",
    val url: String = "",
    val route: String = ""
)

@Serializable
data class AnimeHomeItem(
    val title: String? = null,
    val name: String? = null,
    val url: String = "",
    val route: String = "",
    @SerialName("media_type")
    val mediaType: String? = null,
    val image: String? = null,
    val season: String? = null,
    @SerialName("episodes_or_year")
    val episodesOrYear: String? = null
) {
    val displayName: String
        get() = (title ?: name ?: "").trim()

    fun toMovie(): Movie {
        val cleanRoute = route.trim()
        val slug = cleanRoute.substringAfterLast("/").ifEmpty {
            displayName.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
        }
        val itemId = if (cleanRoute.isNotEmpty()) cleanRoute.trimStart('/') else slug
        val extraInfo = buildString {
            if (!season.isNullOrBlank()) append(season.trim()).append(" ")
            if (!episodesOrYear.isNullOrBlank()) append(episodesOrYear.trim())
        }.trim()

        val typeLabel = when {
            mediaType.equals("movie", ignoreCase = true) || cleanRoute.startsWith("/movie") -> "Movie"
            mediaType.equals("series", ignoreCase = true) || cleanRoute.startsWith("/series") -> "Series"
            cleanRoute.contains("franchise") -> "Franchise"
            cleanRoute.contains("network") -> "Network"
            cleanRoute.contains("language") -> "Audio"
            else -> "Anime"
        }

        val descriptionText = if (extraInfo.isNotEmpty()) "$extraInfo • $typeLabel" else typeLabel

        val cleanApiUrl = if (cleanRoute.isNotEmpty()) {
            "https://metflix-anime.onrender.com/api/${cleanRoute.trimStart('/')}"
        } else {
            url
        }

        return Movie(
            id = itemId,
            videoUri = cleanApiUrl,
            subtitleUri = null,
            posterUri = image.orEmpty(),
            name = displayName.ifEmpty { "Anime" },
            description = descriptionText
        )
    }
}

@Serializable
data class AnimeHomeResponse(
    val type: String? = null,
    val url: String? = null,
    val pagination: AnimePagination? = null,
    val navbar: List<AnimeNavbarItem> = emptyList(),
    @SerialName("big_titles_main")
    val bigTitlesMain: List<AnimeHomeItem> = emptyList(),
    @SerialName("newest_drops")
    val newestDrops: List<AnimeHomeItem> = emptyList(),
    val networks: List<AnimeHomeItem> = emptyList(),
    @SerialName("stream_in_your_language")
    val streamInYourLanguage: List<AnimeHomeItem> = emptyList(),
    @SerialName("most_watched_shows")
    val mostWatchedShows: List<AnimeHomeItem> = emptyList(),
    @SerialName("new_anime_arrivals")
    val newAnimeArrivals: List<AnimeHomeItem> = emptyList(),
    @SerialName("just_in_cartoon_series")
    val justInCartoonSeries: List<AnimeHomeItem> = emptyList(),
    @SerialName("most_watched_films")
    val mostWatchedFilms: List<AnimeHomeItem> = emptyList(),
    @SerialName("latest_anime_movies")
    val latestAnimeMovies: List<AnimeHomeItem> = emptyList(),
    @SerialName("fresh_cartoon_films")
    val freshCartoonFilms: List<AnimeHomeItem> = emptyList()
)
