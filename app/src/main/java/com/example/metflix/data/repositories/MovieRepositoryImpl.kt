/*
 * Copyright 2023 Google LLC
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

package com.example.metflix.data.repositories

import com.example.metflix.data.entities.Movie
import com.example.metflix.data.entities.MovieCategoryDetails
import com.example.metflix.data.entities.MovieDetails
import com.example.metflix.data.entities.MovieList
import com.example.metflix.data.entities.TvEpisode
import com.example.metflix.data.entities.TvSeason
import com.example.metflix.data.entities.MovieReviewsAndRatings
import com.example.metflix.data.entities.ThumbnailType
import com.example.metflix.data.util.StringConstants
import com.example.metflix.data.util.StringConstants.Movie.Reviewer.DefaultCount
import com.example.metflix.data.util.StringConstants.Movie.Reviewer.DefaultRating
import com.example.metflix.data.util.StringConstants.Movie.Reviewer.FreshTomatoes
import com.example.metflix.data.util.StringConstants.Movie.Reviewer.ReviewerName
import com.example.metflix.data.network.tmdb.TmdbApiClient
import com.example.metflix.data.network.tmdb.TmdbConfig
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

@Singleton
class MovieRepositoryImpl @Inject constructor(
    private val movieDataSource: MovieDataSource,
    private val tvDataSource: TvDataSource,
    private val animeDataSource: AnimeDataSource,
    private val movieCastDataSource: MovieCastDataSource,
    private val movieCategoryDataSource: MovieCategoryDataSource,
    private val tmdbApiClient: TmdbApiClient,
    private val diskCache: TmdbDiskCache
) : MovieRepository {

    private val movieDetailsCache = ConcurrentHashMap<String, MovieDetails>()
    private val categoryDetailsCache = ConcurrentHashMap<String, MovieCategoryDetails>()
    private val tvEpisodesCache = ConcurrentHashMap<String, List<TvEpisode>>()

    override fun getFeaturedMovies() = flow {
        val list = movieDataSource.getFeaturedMovieList()
        emit(list)
    }.flowOn(Dispatchers.IO)

    override fun getTrendingMovies(): Flow<MovieList> = flow {
        val list = movieDataSource.getTrendingMovieList()
        emit(list)
    }.flowOn(Dispatchers.IO)

    override fun getTop10Movies(): Flow<MovieList> = flow {
        val list = movieDataSource.getTop10MovieList()
        emit(list)
    }.flowOn(Dispatchers.IO)

    override fun getNowPlayingMovies(): Flow<MovieList> = flow {
        val list = movieDataSource.getNowPlayingMovieList()
        emit(list)
    }.flowOn(Dispatchers.IO)

    override fun getMovieCategories() = flow {
        val list = movieCategoryDataSource.getMovieCategoryList()
        emit(list)
    }.flowOn(Dispatchers.IO)

    override suspend fun getMovieCategoryDetails(categoryId: String): MovieCategoryDetails {
        categoryDetailsCache[categoryId]?.let { return it }

        val categoryList = movieCategoryDataSource.getMovieCategoryList()
        val category = categoryList.find { categoryId == it.id } ?: categoryList.firstOrNull()

        val p1 = tmdbApiClient.getMoviesByCategory(categoryId, page = 1).getOrNull().orEmpty()
        val p2 = tmdbApiClient.getMoviesByCategory(categoryId, page = 2).getOrNull().orEmpty()
        val tmdbMovies = (p1 + p2).distinctBy { it.id }
        val movieList = if (tmdbMovies.isNotEmpty()) {
            tmdbMovies
        } else {
            val allLocal = movieDataSource.getMovieList()
            if (allLocal.size > 20) allLocal.shuffled().take(20) else allLocal
        }

        val result = MovieCategoryDetails(
            id = category?.id ?: categoryId,
            name = category?.name ?: "Movies",
            movies = movieList
        )
        categoryDetailsCache[categoryId] = result
        return result
    }

    override suspend fun getMovieDetails(movieId: String, isTvShow: Boolean?): MovieDetails = withContext(Dispatchers.IO) {
        val cacheKey = if (isTvShow != null) "${movieId}_tv$isTvShow" else movieId
        movieDetailsCache[cacheKey]?.let { return@withContext it }

        // If explicitly requested as TV show, or checking TV first
        val shouldCheckTvFirst = isTvShow == true || tvDataSource.getTvShowList().any { it.id == movieId } || animeDataSource.getTrendingAnimeList().any { it.id == movieId }

        if (shouldCheckTvFirst) {
            val tmdbTvDetails = tmdbApiClient.getTvShowDetails(movieId).getOrNull()
            if (tmdbTvDetails != null) {
                val season1Episodes = tmdbApiClient.getTvSeasonDetails(movieId, 1).getOrNull().orEmpty()
                val enrichedSeasons = tmdbTvDetails.seasons.map { s ->
                    if (s.seasonNumber == 1 && season1Episodes.isNotEmpty()) {
                        s.copy(episodes = season1Episodes)
                    } else s
                }
                val finalDetails = tmdbTvDetails.copy(seasons = enrichedSeasons)
                movieDetailsCache[cacheKey] = finalDetails
                return@withContext finalDetails
            }
        }

        // Try movie details
        val tmdbDetails = tmdbApiClient.getMovieDetails(movieId).getOrNull()
        if (tmdbDetails != null) {
            movieDetailsCache[cacheKey] = tmdbDetails
            return@withContext tmdbDetails
        }

        // If movie details failed and wasn't checked as TV yet, try TV show details!
        if (!shouldCheckTvFirst) {
            val tmdbTvDetails = tmdbApiClient.getTvShowDetails(movieId).getOrNull()
            if (tmdbTvDetails != null) {
                val season1Episodes = tmdbApiClient.getTvSeasonDetails(movieId, 1).getOrNull().orEmpty()
                val enrichedSeasons = tmdbTvDetails.seasons.map { s ->
                    if (s.seasonNumber == 1 && season1Episodes.isNotEmpty()) {
                        s.copy(episodes = season1Episodes)
                    } else s
                }
                val finalDetails = tmdbTvDetails.copy(seasons = enrichedSeasons)
                movieDetailsCache[cacheKey] = finalDetails
                return@withContext finalDetails
            }
        }

        // Check local / disk cached TV shows & anime before generic fallback
        val matchedTv = tvDataSource.getTvShowList().find { it.id == movieId }
            ?: animeDataSource.getTrendingAnimeList().find { it.id == movieId }
            ?: animeDataSource.getCartoonsList().find { it.id == movieId }

        if (matchedTv != null) {
            val tvDetails = MovieDetails(
                id = matchedTv.id,
                videoUri = matchedTv.videoUri,
                subtitleUri = matchedTv.subtitleUri,
                posterUri = matchedTv.posterUri,
                name = matchedTv.name,
                description = matchedTv.description,
                pgRating = "TV-14",
                releaseDate = "2024",
                categories = listOf("Animation", "Anime", "Series"),
                duration = "TV Series",
                director = "Animation Studios",
                screenplay = "Original Adaptation",
                music = "Original Soundtrack",
                castAndCrew = emptyList(),
                status = "Returning Series",
                originalLanguage = "Japanese",
                budget = "TV Series",
                revenue = "Series",
                similarMovies = emptyList(),
                reviewsAndRatings = emptyList(),
                isTvShow = true,
                seasons = listOf(TvSeason(id = "${matchedTv.id}_s1", name = "Season 1", seasonNumber = 1, episodeCount = 24)),
                totalSeasons = 1,
                totalEpisodes = 24
            )
            movieDetailsCache[cacheKey] = tvDetails
            return@withContext tvDetails
        }

        // Exact match in local movie catalog, or build a clean fallback using the clicked item's ID
        val movieList = movieDataSource.getMovieList()
        val movie = movieList.find { it.id == movieId }
            ?: Movie(
                id = movieId,
                name = "Details",
                description = "Details currently loading or unavailable.",
                posterUri = "",
                videoUri = TmdbConfig.DEFAULT_STREAM_URL,
                subtitleUri = TmdbConfig.DEFAULT_SUBTITLE_URL
            )
        val similarMovieList = movieList.filter { it.id != movie.id }.take(4)
        val castList = movieCastDataSource.getMovieCastList()

        val details = MovieDetails(
            id = movie.id,
            videoUri = movie.videoUri,
            subtitleUri = movie.subtitleUri,
            posterUri = movie.posterUri,
            name = movie.name,
            description = movie.description,
            pgRating = "PG-13",
            releaseDate = "2024",
            categories = listOf("Action", "Adventure", "Drama"),
            duration = "1h 55m",
            director = "MetFlix Studios",
            screenplay = "Original Production",
            music = "Dolby Atmos",
            castAndCrew = castList,
            status = "Released",
            originalLanguage = "English",
            budget = "$20M",
            revenue = "$85M",
            similarMovies = similarMovieList,
            reviewsAndRatings = listOf(
                MovieReviewsAndRatings(
                    reviewerName = FreshTomatoes,
                    reviewerIconUri = StringConstants.Movie.Reviewer.FreshTomatoesImageUrl,
                    reviewCount = "48",
                    reviewRating = "92%"
                ),
                MovieReviewsAndRatings(
                    reviewerName = ReviewerName,
                    reviewerIconUri = StringConstants.Movie.Reviewer.ImageUrl,
                    reviewCount = DefaultCount,
                    reviewRating = DefaultRating
                ),
            ),
        )
        movieDetailsCache[movieId] = details
        details
    }

    override suspend fun searchMovies(query: String): MovieList {
        return movieDataSource.searchMovies(query)
    }

    override suspend fun getCachedPopularMovies(): MovieList =
        diskCache.loadMovies("popular")

    override fun getMoviesWithLongThumbnail() = flow {
        val list = movieDataSource.getMovieList(ThumbnailType.Long)
        emit(list)
    }.flowOn(Dispatchers.IO)

    override fun getMovies(): Flow<MovieList> = flow {
        val list = movieDataSource.getMovieList()
        emit(list)
    }.flowOn(Dispatchers.IO)

    override fun getPopularFilmsThisWeek(): Flow<MovieList> = flow {
        val list = movieDataSource.getPopularFilmThisWeek()
        emit(list)
    }.flowOn(Dispatchers.IO)

    override fun getTrendingDayMovies(): Flow<MovieList> = flow {
        val list = movieDataSource.getTrendingDayMovieList()
        emit(list)
    }.flowOn(Dispatchers.IO)

    override fun getUpcomingMovies(): Flow<MovieList> = flow {
        val list = movieDataSource.getUpcomingMovieList()
        emit(list)
    }.flowOn(Dispatchers.IO)

    override fun getTopRevenueMovies(): Flow<MovieList> = flow {
        val list = movieDataSource.getTopRevenueMovieList()
        emit(list)
    }.flowOn(Dispatchers.IO)

    override fun getMostVotedMovies(): Flow<MovieList> = flow {
        val list = movieDataSource.getMostVotedMovieList()
        emit(list)
    }.flowOn(Dispatchers.IO)

    override fun getNetflixMovies(): Flow<MovieList> = flow {
        val list = movieDataSource.getNetflixMovieList()
        emit(list)
    }.flowOn(Dispatchers.IO)

    override fun getPrimeMovies(): Flow<MovieList> = flow {
        val list = movieDataSource.getPrimeMovieList()
        emit(list)
    }.flowOn(Dispatchers.IO)

    override fun getDisneyMovies(): Flow<MovieList> = flow {
        val list = movieDataSource.getDisneyMovieList()
        emit(list)
    }.flowOn(Dispatchers.IO)

    override fun getTVShows(): Flow<MovieList> = flow {
        val list = tvDataSource.getTvShowList()
        emit(list)
    }.flowOn(Dispatchers.IO)

    override fun getBingeWatchDramas(): Flow<MovieList> = flow {
        val list = tvDataSource.getBingeWatchDramaList()
        emit(list)
    }.flowOn(Dispatchers.IO)

    override fun getTrendingAnime(): Flow<MovieList> = flow {
        val list = animeDataSource.getTrendingAnimeList()
        emit(list)
    }.flowOn(Dispatchers.IO)

    override fun getAnimeMovies(): Flow<MovieList> = flow {
        val list = animeDataSource.getAnimeMoviesList()
        emit(list)
    }.flowOn(Dispatchers.IO)

    override fun getCartoons(): Flow<MovieList> = flow {
        val list = animeDataSource.getCartoonsList()
        emit(list)
    }.flowOn(Dispatchers.IO)

    override fun getTopRatedAnime(): Flow<MovieList> = flow {
        val list = animeDataSource.getTopRatedAnimeList()
        emit(list)
    }.flowOn(Dispatchers.IO)

    override fun getFavouriteMovies(): Flow<MovieList> = flow {
        val list = movieDataSource.getFavoriteMovieList()
        emit(list)
    }.flowOn(Dispatchers.IO)

    override suspend fun getTvSeasonEpisodes(seriesId: String, seasonNumber: Int): List<TvEpisode> {
        val cacheKey = "${seriesId}_s${seasonNumber}"
        tvEpisodesCache[cacheKey]?.let { return it }

        val episodes = tmdbApiClient.getTvSeasonDetails(seriesId, seasonNumber).getOrNull().orEmpty()
        if (episodes.isNotEmpty()) {
            tvEpisodesCache[cacheKey] = episodes
        }
        return episodes
    }

    override suspend fun refreshAllCatalogs(): Boolean = kotlinx.coroutines.withContext(Dispatchers.IO) {
        diskCache.clearAll()
        categoryDetailsCache.clear()
        movieDetailsCache.clear()
        tvEpisodesCache.clear()
        movieDataSource.clearAllReaders()
        true
    }
}
