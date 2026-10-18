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

import com.example.metflix.data.entities.MovieCategoryDetails
import com.example.metflix.data.entities.MovieCategoryList
import com.example.metflix.data.entities.MovieDetails
import com.example.metflix.data.entities.MovieList
import com.example.metflix.data.entities.TvEpisode
import kotlinx.coroutines.flow.Flow

interface MovieRepository {
    fun getFeaturedMovies(): Flow<MovieList>
    fun getTrendingMovies(): Flow<MovieList>
    fun getTop10Movies(): Flow<MovieList>
    fun getNowPlayingMovies(): Flow<MovieList>
    fun getMovieCategories(): Flow<MovieCategoryList>
    suspend fun getMovieCategoryDetails(categoryId: String): MovieCategoryDetails
    suspend fun getMovieDetails(movieId: String): MovieDetails
    suspend fun getTvSeasonEpisodes(seriesId: String, seasonNumber: Int): List<TvEpisode>
    suspend fun searchMovies(query: String): MovieList
    fun getMoviesWithLongThumbnail(): Flow<MovieList>
    fun getMovies(): Flow<MovieList>
    fun getPopularFilmsThisWeek(): Flow<MovieList>
    fun getTrendingDayMovies(): Flow<MovieList>
    fun getUpcomingMovies(): Flow<MovieList>
    fun getTopRevenueMovies(): Flow<MovieList>
    fun getMostVotedMovies(): Flow<MovieList>
    fun getNetflixMovies(): Flow<MovieList>
    fun getPrimeMovies(): Flow<MovieList>
    fun getDisneyMovies(): Flow<MovieList>
    fun getTVShows(): Flow<MovieList>
    fun getBingeWatchDramas(): Flow<MovieList>
    fun getFavouriteMovies(): Flow<MovieList>
    suspend fun refreshAllCatalogs(): Boolean
}
