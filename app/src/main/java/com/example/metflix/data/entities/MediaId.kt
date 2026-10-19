package com.example.metflix.data.entities

/**
 * Type-safe media identifiers to distinguish movies, TV shows, and anime.
 * Eliminates ID collision bugs and decouples Anime from TMDB dependencies.
 */
sealed interface MediaId {
    data class Movie(
        val tmdbId: String,
        val imdbId: String? = null
    ) : MediaId

    data class TvShow(
        val tmdbId: String,
        val seasonNumber: Int,
        val episodeNumber: Int,
        val imdbId: String? = null
    ) : MediaId

    data class Anime(
        val title: String,
        val episodeNumber: Int,
        val absoluteEpisodeNumber: Int? = null,
        val seasonNumber: Int = 1
    ) : MediaId
}
