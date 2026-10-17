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
import com.example.metflix.data.entities.WatchOptionType
import com.example.metflix.data.entities.WatchProvider
import com.example.metflix.data.entities.WatchProvidersInfo
import kotlinx.serialization.SerialName
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
    val similar: TmdbPageResponse<TmdbMovieItem>? = null,
    @SerialName("watch/providers") val watchProviders: TmdbWatchProvidersResponse? = null
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

@Serializable
data class TmdbWatchProvidersResponse(
    val id: Long = 0,
    val results: Map<String, TmdbWatchProviderCountry> = emptyMap()
)

@Serializable
data class TmdbWatchProviderCountry(
    val link: String? = null,
    val flatrate: List<TmdbWatchProviderItem> = emptyList(),
    val rent: List<TmdbWatchProviderItem> = emptyList(),
    val buy: List<TmdbWatchProviderItem> = emptyList(),
    val ads: List<TmdbWatchProviderItem> = emptyList(),
    val free: List<TmdbWatchProviderItem> = emptyList()
)

@Serializable
data class TmdbWatchProviderItem(
    val provider_id: Int = 0,
    val provider_name: String = "",
    val logo_path: String? = null,
    val display_priority: Int = 0
)

fun resolveAppPackage(providerName: String, providerId: Int): String? {
    val lower = providerName.lowercase()
    return when {
        providerId == 8 || lower.contains("netflix") -> "com.netflix.ninja"
        providerId == 119 || providerId == 9 || providerId == 10 || lower.contains("amazon") || lower.contains("prime") -> "com.amazon.amazonvideo.livingroom"
        providerId == 337 || providerId == 122 || lower.contains("disney") || lower.contains("hotstar") -> "in.startv.hotstar"
        providerId == 2 || providerId == 350 || lower.contains("apple") -> "com.apple.atve.androidtv.appletv"
        providerId == 220 || lower.contains("jio") -> "com.jio.media.ondemand"
        providerId == 232 || lower.contains("zee") -> "com.graymatrix.did"
        providerId == 237 || lower.contains("sony") -> "com.msl.liv.tv"
        providerId == 192 || lower.contains("youtube") -> "com.google.android.youtube.tv"
        providerId == 3 || lower.contains("google play") -> "com.google.android.videos"
        providerId == 15 || lower.contains("hulu") -> "com.hulu.livingroomplus"
        providerId == 384 || lower.contains("max") || lower.contains("hbo") -> "com.wbd.stream"
        providerId == 41 || lower.contains("stan") -> "au.com.stan.and"
        else -> null
    }
}

fun TmdbWatchProviderItem.toWatchProvider(type: WatchOptionType): WatchProvider {
    val logoUrl = logo_path?.let { "${TmdbConfig.IMAGE_BASE_URL_POSTER}$it" } ?: ""
    val pkg = resolveAppPackage(provider_name, provider_id)
    return WatchProvider(
        id = provider_id,
        name = provider_name,
        logoUri = logoUrl,
        type = type,
        appPackage = pkg
    )
}

fun TmdbWatchProvidersResponse.toWatchProvidersInfo(countryCode: String = "IN"): WatchProvidersInfo {
    val country = results[countryCode] ?: results["US"] ?: results.values.firstOrNull()
        ?: return WatchProvidersInfo(countryCode = countryCode)

    return WatchProvidersInfo(
        tmdbLink = country.link,
        countryCode = countryCode,
        streamProviders = country.flatrate.map { it.toWatchProvider(WatchOptionType.Stream) },
        rentProviders = country.rent.map { it.toWatchProvider(WatchOptionType.Rent) },
        buyProviders = country.buy.map { it.toWatchProvider(WatchOptionType.Buy) },
        freeProviders = (country.free + country.ads).map { it.toWatchProvider(WatchOptionType.Free) }
    )
}

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
        ),
        watchProviders = watchProviders?.toWatchProvidersInfo()
    )
}
