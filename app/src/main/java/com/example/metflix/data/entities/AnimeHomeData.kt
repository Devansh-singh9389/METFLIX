package com.example.metflix.data.entities

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Serializable
@Immutable
data class AnimeHomeData(
    val bigTitles: List<Movie> = emptyList(),
    val newestDrops: List<Movie> = emptyList(),
    val newAnimeArrivals: List<Movie> = emptyList(),
    val mostWatchedShows: List<Movie> = emptyList(),
    val latestAnimeMovies: List<Movie> = emptyList(),
    val mostWatchedFilms: List<Movie> = emptyList(),
    val cartoonSeries: List<Movie> = emptyList(),
    val cartoonFilms: List<Movie> = emptyList(),
    val networks: List<Movie> = emptyList(),
    val languages: List<Movie> = emptyList()
) {
    val isEmpty: Boolean
        get() = bigTitles.isEmpty() &&
                newestDrops.isEmpty() &&
                newAnimeArrivals.isEmpty() &&
                mostWatchedShows.isEmpty() &&
                latestAnimeMovies.isEmpty() &&
                mostWatchedFilms.isEmpty() &&
                cartoonSeries.isEmpty() &&
                cartoonFilms.isEmpty() &&
                networks.isEmpty() &&
                languages.isEmpty()

    fun getAllUniqueMovies(): List<Movie> {
        val all = mutableListOf<Movie>()
        all.addAll(bigTitles)
        all.addAll(newestDrops)
        all.addAll(newAnimeArrivals)
        all.addAll(mostWatchedShows)
        all.addAll(latestAnimeMovies)
        all.addAll(mostWatchedFilms)
        all.addAll(cartoonSeries)
        all.addAll(cartoonFilms)
        all.addAll(networks)
        all.addAll(languages)
        return all.distinctBy { it.id }
    }
}
