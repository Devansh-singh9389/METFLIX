package com.example.metflix.data.network.anime

import com.example.metflix.data.network.anime.models.AnimeCategoryResponse
import com.example.metflix.data.network.anime.models.AnimeEpisodeItem
import com.example.metflix.data.network.anime.models.AnimeEpisodeResponse
import com.example.metflix.data.network.anime.models.AnimeMovieResponse
import com.example.metflix.data.network.anime.models.AnimeRecommendedMovie
import com.example.metflix.data.network.anime.models.AnimeSeriesResponse
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimeCatalogModelsTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    @Test
    fun testParseSeriesResponse() {
        val seriesJson = """
        {
            "type": "series",
            "slug": "fullmetal-alchemist-brotherhood",
            "title": "Fullmetal Alchemist: Brotherhood",
            "poster": "https://image.tmdb.org/t/p/w185/poster.jpg",
            "synopsis": "Disregard for alchemy laws ripped limbs...",
            "genres": ["Action", "Adventure", "Fantasy"],
            "languages": ["English", "Hindi", "Japanese"],
            "total_seasons": 1,
            "seasons": [
                {
                    "season_name": "Season 1",
                    "season_number": 1,
                    "episodes_count": 2,
                    "episodes": [
                        {
                            "episode_code": "1x1",
                            "title": "Fullmetal Alchemist: Brotherhood 1x1",
                            "url": "https://watchanimeworld.one/episode/fma-1x1/",
                            "route": "/episode/fma-1x1",
                            "thumbnail": "https://img.watchanimeworld.one/thumb1.webp"
                        },
                        {
                            "episode_code": "1x2",
                            "title": "Fullmetal Alchemist: Brotherhood 1x2",
                            "url": "https://watchanimeworld.one/episode/fma-1x2/",
                            "route": "/episode/fma-1x2",
                            "thumbnail": "https://img.watchanimeworld.one/thumb2.webp"
                        }
                    ]
                }
            ]
        }
        """.trimIndent()

        val parsed = json.decodeFromString<AnimeSeriesResponse>(seriesJson)
        assertNotNull(parsed)
        assertEquals("Fullmetal Alchemist: Brotherhood", parsed.title)
        assertEquals(1, parsed.seasons.size)
        assertEquals(2, parsed.seasons[0].episodes.size)

        val ep1 = parsed.seasons[0].episodes[0]
        val epMovie = ep1.toMovie(parsed.title ?: "")
        assertEquals("Fullmetal Alchemist: Brotherhood 1x1", epMovie.name)
        assertEquals("episode/fma-1x1", epMovie.id)
        assertTrue(epMovie.description.contains("1x1"))
    }

    @Test
    fun testParseCategoryResponse() {
        val categoryJson = """
        {
            "type": "listing",
            "title": "Doraemon",
            "url": "https://watchanimeworld.one/category/franchise/doraemon/",
            "total_items": 2,
            "category": "Doraemon",
            "category_type": "franchise",
            "slug": "doraemon",
            "items": [
                {
                    "id": "397",
                    "title": "Stand by Me Doraemon 2",
                    "media_type": "movie",
                    "route": "/movie/stand-by-me-doraemon-2",
                    "url": "https://watchanimeworld.one/movies/stand-by-me-doraemon-2/",
                    "image": "https://image.tmdb.org/t/p/w500/standbyme2.jpg"
                },
                {
                    "id": "400",
                    "title": "Doraemon Classic Series",
                    "media_type": "series",
                    "route": "/series/doraemon-classic",
                    "url": "https://watchanimeworld.one/series/doraemon-classic/",
                    "image": "https://image.tmdb.org/t/p/w500/doraemon.jpg"
                }
            ]
        }
        """.trimIndent()

        val parsed = json.decodeFromString<AnimeCategoryResponse>(categoryJson)
        assertNotNull(parsed)
        assertEquals("Doraemon", parsed.title)
        assertEquals("franchise", parsed.categoryType)
        assertEquals(2, parsed.items.size)

        val movieItem = parsed.items[0].toMovie()
        assertEquals("Stand by Me Doraemon 2", movieItem.name)
        assertEquals("movie/stand-by-me-doraemon-2", movieItem.id)
    }

    @Test
    fun testParseMovieResponse() {
        val movieJson = """
        {
            "type": "movie",
            "slug": "ben-10-vs-the-universe",
            "title": "Ben 10 vs. the Universe: The Movie",
            "poster": "https://image.tmdb.org/t/p/w500/ben10.jpg",
            "synopsis": "Ben Tennyson travels to outer space...",
            "duration": "1h 10m",
            "release_year": "2020",
            "genres": ["Action", "Animation", "Sci-Fi"],
            "languages": ["English", "Hindi"],
            "streaming_servers": [
                {
                    "server_id": "server-1",
                    "server_name": "Stream 1",
                    "embed_url": "https://player.example.com/embed/123"
                }
            ],
            "recommended_movies": [
                {
                    "title": "Ben 10: Secret of the Omnitrix",
                    "route": "/movie/ben-10-secret-of-the-omnitrix",
                    "poster": "https://image.tmdb.org/t/p/w500/omnitrix.jpg"
                }
            ]
        }
        """.trimIndent()

        val parsed = json.decodeFromString<AnimeMovieResponse>(movieJson)
        assertNotNull(parsed)
        assertEquals("Ben 10 vs. the Universe: The Movie", parsed.title)
        assertEquals(1, parsed.streamingServers.size)
        assertEquals(1, parsed.recommendedMovies.size)

        val rec = parsed.recommendedMovies[0].toMovie()
        assertEquals("Ben 10: Secret of the Omnitrix", rec.name)
        assertEquals("movie/ben-10-secret-of-the-omnitrix", rec.id)
    }

    @Test
    fun testParseEpisodeResponse() {
        val epJson = """
        {
            "type": "episode",
            "episode_title": "Naruto Shippuden 1x1",
            "series_title": "Naruto Shippuden",
            "slug": "naruto-shippuden-1x1",
            "season": 1,
            "episode": 1,
            "poster": "https://image.tmdb.org/t/p/w500/naruto.jpg",
            "streaming_servers": [
                {
                    "server_id": "opt-1",
                    "server_name": "Multi-Audio Server",
                    "embed_url": "https://embed.example.com/naruto-1"
                }
            ]
        }
        """.trimIndent()

        val parsed = json.decodeFromString<AnimeEpisodeResponse>(epJson)
        assertNotNull(parsed)
        assertEquals("Naruto Shippuden 1x1", parsed.episodeTitle)
        assertEquals(1, parsed.streamingServers.size)
        assertEquals("https://embed.example.com/naruto-1", parsed.streamingServers[0].embedUrl)
    }
}
