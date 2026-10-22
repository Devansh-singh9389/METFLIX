package com.example.metflix.data.network.tmdb

import com.example.metflix.data.entities.ThumbnailType
import com.example.metflix.data.util.StringConstants
import kotlinx.serialization.json.Json
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BollywoodMoviesTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = true
    }

    @Test
    fun deserializesTmdbBollywoodResponseAndMapsToMovie() {
        val tmdbResponse = """
            {
                "page": 1,
                "results": [
                    {
                        "id": 872585,
                        "title": "Jawan",
                        "overview": "An emotional journey of a prison warden.",
                        "poster_path": "/jawan_poster.jpg",
                        "backdrop_path": "/jawan_backdrop.jpg",
                        "release_date": "2023-09-07",
                        "original_language": "hi",
                        "vote_average": 7.8,
                        "vote_count": 1200
                    },
                    {
                        "id": 866398,
                        "title": "Pathaan",
                        "overview": "An Indian spy takes on a mercenary group.",
                        "poster_path": "/pathaan_poster.jpg",
                        "backdrop_path": "/pathaan_backdrop.jpg",
                        "release_date": "2023-01-25",
                        "original_language": "hi",
                        "vote_average": 7.5,
                        "vote_count": 950
                    }
                ],
                "total_pages": 5,
                "total_results": 100
            }
        """.trimIndent()

        val page = json.decodeFromString<TmdbPageResponse<TmdbMovieItem>>(tmdbResponse)
        assertEquals(2, page.results.size)

        val jawan = page.results[0]
        assertEquals(872585L, jawan.id)
        assertEquals("Jawan", jawan.title)
        assertEquals("hi", jawan.original_language)

        val movie = jawan.toMovie(ThumbnailType.Standard)
        assertEquals("872585", movie.id)
        assertEquals("Jawan", movie.name)
        assertEquals("An emotional journey of a prison warden.", movie.description)
        assertTrue(movie.posterUri.contains("/jawan_poster.jpg"))
    }

    @Test
    fun verifiesStringConstants() {
        assertEquals("Bollywood Hits & Blockbusters", StringConstants.Composable.BollywoodMoviesTitle)
    }
}
