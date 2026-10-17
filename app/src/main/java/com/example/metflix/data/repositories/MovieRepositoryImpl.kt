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
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

@Singleton
class MovieRepositoryImpl @Inject constructor(
    private val movieDataSource: MovieDataSource,
    private val tvDataSource: TvDataSource,
    private val movieCastDataSource: MovieCastDataSource,
    private val movieCategoryDataSource: MovieCategoryDataSource,
    private val tmdbApiClient: TmdbApiClient,
) : MovieRepository {

    private val movieDetailsCache = ConcurrentHashMap<String, MovieDetails>()
    private val categoryDetailsCache = ConcurrentHashMap<String, MovieCategoryDetails>()

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

        val tmdbMovies = tmdbApiClient.getMoviesByCategory(categoryId).getOrNull()
        val movieList = if (!tmdbMovies.isNullOrEmpty()) {
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

    override suspend fun getMovieDetails(movieId: String): MovieDetails {
        movieDetailsCache[movieId]?.let { return it }

        // Try live TMDB first
        val tmdbDetails = tmdbApiClient.getMovieDetails(movieId).getOrNull()
        if (tmdbDetails != null) {
            movieDetailsCache[movieId] = tmdbDetails
            return tmdbDetails
        }

        // Fallback if TMDB details unavailable
        val movieList = movieDataSource.getMovieList()
        val movie = movieList.find { it.id == movieId }
            ?: movieList.firstOrNull()
            ?: Movie(
                id = movieId,
                name = "Details",
                description = "Details currently unavailable.",
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
        return details
    }

    override suspend fun searchMovies(query: String): MovieList {
        return movieDataSource.searchMovies(query)
    }

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

    override fun getFavouriteMovies(): Flow<MovieList> = flow {
        val list = movieDataSource.getFavoriteMovieList()
        emit(list)
    }.flowOn(Dispatchers.IO)
}
