/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.metflix.data.network.tmdb

import com.example.metflix.data.entities.Movie
import com.example.metflix.data.entities.MovieCast
import com.example.metflix.data.entities.MovieCategory
import com.example.metflix.data.entities.MovieDetails
import com.example.metflix.data.entities.MovieReviewsAndRatings
import com.example.metflix.data.entities.ThumbnailType
import kotlinx.serialization.Serializable

@Serializable
data class TmdbPageResponse<T>(
    val page: Int = 1,
    val results: List<T> = emptyList(),
    val total_pages: Int = 1,
    val total_results: Int = 0
)

@Serializable
data class TmdbMovieItem(
    val id: Long = 0,
    val title: String = "",
    val overview: String = "",
    val poster_path: String? = null,
    val backdrop_path: String? = null,
    val release_date: String = "",
    val vote_average: Double = 0.0,
    val vote_count: Int = 0,
    val genre_ids: List<Int> = emptyList(),
    val adult: Boolean = false,
    val original_language: String = "en"
)

@Serializable
data class TmdbTvItem(
    val id: Long = 0,
    val name: String = "",
    val overview: String = "",
    val poster_path: String? = null,
    val backdrop_path: String? = null,
    val first_air_date: String = "",
    val vote_average: Double = 0.0,
    val vote_count: Int = 0
)

@Serializable
data class TmdbGenreResponse(
    val genres: List<TmdbGenreItem> = emptyList()
)

@Serializable
data class TmdbGenreItem(
    val id: Int = 0,
    val name: String = ""
)

@Serializable
data class TmdbMovieDetailsResponse(
    val id: Long = 0,
    val title: String = "",
    val overview: String = "",
    val poster_path: String? = null,
    val backdrop_path: String? = null,
    val release_date: String = "",
    val runtime: Int? = null,
    val vote_average: Double = 0.0,
    val vote_count: Int = 0,
    val status: String = "Released",
    val original_language: String = "en",
    val budget: Long = 0,
    val revenue: Long = 0,
    val genres: List<TmdbGenreItem> = emptyList(),
    val credits: TmdbCreditsResponse? = null,
    val similar: TmdbPageResponse<TmdbMovieItem>? = null
)

@Serializable
data class TmdbCreditsResponse(
    val cast: List<TmdbCastItem> = emptyList(),
    val crew: List<TmdbCrewItem> = emptyList()
)

@Serializable
data class TmdbCastItem(
    val id: Long = 0,
    val name: String = "",
    val character: String = "",
    val profile_path: String? = null
)

@Serializable
data class TmdbCrewItem(
    val id: Long = 0,
    val name: String = "",
    val job: String = "",
    val department: String = ""
)

// Helper mappers from TMDB to MetFlix domain entities
fun TmdbMovieItem.toMovie(thumbnailType: ThumbnailType = ThumbnailType.Standard): Movie {
    val posterUrl = poster_path?.let { "${TmdbConfig.IMAGE_BASE_URL_POSTER}$it" }
        ?: "${TmdbConfig.IMAGE_BASE_URL_BACKDROP}$backdrop_path"
    val backdropUrl = backdrop_path?.let { "${TmdbConfig.IMAGE_BASE_URL_BACKDROP}$it" }
        ?: posterUrl

    val chosenThumbnail = when (thumbnailType) {
        ThumbnailType.Standard -> posterUrl
        ThumbnailType.Long -> backdropUrl
    }

    // Default sample stream for playback
    val sampleVideo = TmdbConfig.DEFAULT_STREAM_URL
    val sampleSubtitle = TmdbConfig.DEFAULT_SUBTITLE_URL

    return Movie(
        id = id.toString(),
        videoUri = sampleVideo,
        subtitleUri = sampleSubtitle,
        posterUri = chosenThumbnail,
        name = title,
        description = overview.ifBlank { "No overview available." }
    )
}

fun TmdbTvItem.toMovie(thumbnailType: ThumbnailType = ThumbnailType.Long): Movie {
    val posterUrl = poster_path?.let { "${TmdbConfig.IMAGE_BASE_URL_POSTER}$it" }
        ?: "${TmdbConfig.IMAGE_BASE_URL_BACKDROP}$backdrop_path"
    val backdropUrl = backdrop_path?.let { "${TmdbConfig.IMAGE_BASE_URL_BACKDROP}$it" }
        ?: posterUrl

    val chosenThumbnail = when (thumbnailType) {
        ThumbnailType.Standard -> posterUrl
        ThumbnailType.Long -> backdropUrl
    }

    return Movie(
        id = id.toString(),
        videoUri = TmdbConfig.DEFAULT_STREAM_URL,
        subtitleUri = TmdbConfig.DEFAULT_SUBTITLE_URL,
        posterUri = chosenThumbnail,
        name = name,
        description = overview.ifBlank { "No overview available." }
    )
}

fun TmdbGenreItem.toMovieCategory(): MovieCategory {
    return MovieCategory(
        id = id.toString(),
        name = name
    )
}

fun TmdbCastItem.toMovieCast(): MovieCast {
    val avatar = profile_path?.let { "${TmdbConfig.IMAGE_BASE_URL_POSTER}$it" } ?: ""
    return MovieCast(
        id = id.toString(),
        characterName = character.ifBlank { "Cast" },
        realName = name,
        avatarUrl = avatar
    )
}

fun TmdbMovieDetailsResponse.toMovieDetails(): MovieDetails {
    val backdropUrl = backdrop_path?.let { "${TmdbConfig.IMAGE_BASE_URL_BACKDROP}$it" }
        ?: poster_path?.let { "${TmdbConfig.IMAGE_BASE_URL_POSTER}$it" }
        ?: ""

    val directorName = credits?.crew?.find { it.job.equals("Director", ignoreCase = true) }?.name
        ?: "Unknown Director"
    val screenplayName = credits?.crew?.find {
        it.job.equals("Screenplay", ignoreCase = true) || it.job.equals("Writer", ignoreCase = true)
    }?.name ?: directorName
    val musicName = credits?.crew?.find {
        it.job.contains("Music", ignoreCase = true) || it.job.contains("Composer", ignoreCase = true)
    }?.name ?: "Original Score"

    val castList = credits?.cast?.take(10)?.map { it.toMovieCast() } ?: emptyList()
    val similarList = similar?.results?.take(10)?.map { it.toMovie(ThumbnailType.Standard) } ?: emptyList()

    val durationString = runtime?.let { mins ->
        val h = mins / 60
        val m = mins % 60
        if (h > 0) "${h}h ${m}m" else "${m}m"
    } ?: "1h 55m"

    val formattedBudget = if (budget > 0) "\$${budget / 1_000_000}M" else "N/A"
    val formattedRevenue = if (revenue > 0) "\$${revenue / 1_000_000}M" else "N/A"

    return MovieDetails(
        id = id.toString(),
        videoUri = TmdbConfig.DEFAULT_STREAM_URL,
        subtitleUri = TmdbConfig.DEFAULT_SUBTITLE_URL,
        posterUri = backdropUrl,
        name = title,
        description = overview.ifBlank { "No overview available." },
        pgRating = if (vote_average > 0) "Rating: ${String.format("%.1f", vote_average)}/10" else "PG-13",
        releaseDate = release_date.take(4).ifBlank { "2024" },
        categories = genres.map { it.name }.ifEmpty { listOf("Drama", "Action") },
        duration = durationString,
        director = directorName,
        screenplay = screenplayName,
        music = musicName,
        castAndCrew = castList,
        status = status,
        originalLanguage = original_language.uppercase(),
        budget = formattedBudget,
        revenue = formattedRevenue,
        similarMovies = similarList,
        reviewsAndRatings = listOf(
            MovieReviewsAndRatings(
                reviewerName = "TMDB Users (${vote_count} votes)",
                reviewerIconUri = "",
                reviewCount = vote_count.toString(),
                reviewRating = String.format("%.1f", vote_average)
            )
        )
    )
}
