package com.example.metflix.data.network.anime

import com.example.metflix.data.entities.AnimeHomeData
import com.example.metflix.data.network.anime.models.AnimeHomeItem
import com.example.metflix.data.network.anime.models.AnimeHomeResponse
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimeHomeModelsTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    @Test
    fun testParseAnimeHomeResponse() {
        val sampleJson = """
        {
            "type": "home",
            "url": "https://watchanimeworld.one",
            "pagination": {
                "current_page": 1,
                "total_pages": 1
            },
            "navbar": [
                {"label": "MOVIES", "url": "https://watchanimeworld.one/movies/", "route": "/movies"}
            ],
            "big_titles_main": [
                {"name": "Doraemon", "url": "https://watchanimeworld.one/category/franchise/doraemon/", "route": "/category/franchise/doraemon", "image": "https://watchanimeworld.one/doraemon.png"}
            ],
            "newest_drops": [
                {
                    "title": "Arifureta: From Commonplace to World's Strongest",
                    "url": "https://watchanimeworld.one/series/arifureta/",
                    "route": "/series/arifureta-from-commonplace-to-worlds-strongest",
                    "media_type": "series",
                    "image": "https://image.tmdb.org/t/p/w500/arifureta.jpg",
                    "season": "Season 3",
                    "episodes_or_year": "EP:1-16"
                }
            ],
            "networks": [
                {"name": "Disney Channel", "url": "https://watchanimeworld.one/disney/", "route": "/category/network/disney-channel", "image": "https://watchanimeworld.one/disney.png"}
            ],
            "stream_in_your_language": [
                {"name": "Hindi", "url": "https://watchanimeworld.one/hindi/", "route": "/category/language/hindi", "image": "https://watchanimeworld.one/hindi.png"}
            ],
            "most_watched_shows": [
                {"title": "Naruto Shippuden", "url": "https://watchanimeworld.one/series/naruto/", "route": "/series/naruto-shippuden", "image": "https://image.tmdb.org/t/p/naruto.jpg"}
            ],
            "new_anime_arrivals": [
                {"title": "Black Torch", "url": "https://watchanimeworld.one/series/black-torch/", "route": "/series/black-torch", "media_type": "series", "image": "https://image.tmdb.org/t/p/blacktorch.jpg"}
            ],
            "just_in_cartoon_series": [
                {"title": "The Mighty Nein", "url": "https://watchanimeworld.one/series/the-mighty-nein/", "route": "/series/the-mighty-nein", "media_type": "series", "image": "https://image.tmdb.org/t/p/mightynein.jpg"}
            ],
            "most_watched_films": [
                {"title": "Demon Slayer", "url": "https://watchanimeworld.one/movies/demon-slayer/", "route": "/movie/demon-slayer", "image": "https://image.tmdb.org/t/p/demonslayer.jpg"}
            ],
            "latest_anime_movies": [
                {"title": "Shinchan Movie", "url": "https://watchanimeworld.one/movies/shinchan-movie/", "route": "/movie/shinchan-movie", "media_type": "movie", "image": "https://image.tmdb.org/t/p/shinchan.jpg"}
            ],
            "fresh_cartoon_films": [
                {"title": "The Loud House Movie", "url": "https://watchanimeworld.one/movies/loud-house/", "route": "/movie/the-loud-house-movie", "media_type": "movie", "image": "https://image.tmdb.org/t/p/loudhouse.jpg"}
            ]
        }
        """.trimIndent()

        val parsed = json.decodeFromString<AnimeHomeResponse>(sampleJson)
        assertNotNull(parsed)
        assertEquals(1, parsed.bigTitlesMain.size)
        assertEquals(1, parsed.newestDrops.size)
        assertEquals(1, parsed.networks.size)
        assertEquals(1, parsed.streamInYourLanguage.size)
        assertEquals(1, parsed.mostWatchedShows.size)
        assertEquals(1, parsed.newAnimeArrivals.size)
        assertEquals(1, parsed.justInCartoonSeries.size)
        assertEquals(1, parsed.mostWatchedFilms.size)
        assertEquals(1, parsed.latestAnimeMovies.size)
        assertEquals(1, parsed.freshCartoonFilms.size)

        // Test item to movie conversion
        val drop = parsed.newestDrops.first()
        val movie = drop.toMovie()
        assertEquals("Arifureta: From Commonplace to World's Strongest", movie.name)
        assertEquals("series/arifureta-from-commonplace-to-worlds-strongest", movie.id)
        assertTrue(movie.description.contains("Season 3"))
        assertTrue(movie.description.contains("EP:1-16"))
        assertEquals("https://image.tmdb.org/t/p/w500/arifureta.jpg", movie.posterUri)

        // Test franchise item conversion
        val franchise = parsed.bigTitlesMain.first().toMovie()
        assertEquals("Doraemon", franchise.name)
        assertEquals("category/franchise/doraemon", franchise.id)
        assertTrue(franchise.description.contains("Franchise"))
    }

    @Test
    fun testAnimeHomeDataNotEmpty() {
        val item = AnimeHomeItem(title = "One Piece", route = "/series/one-piece")
        val data = AnimeHomeData(
            newAnimeArrivals = listOf(item.toMovie())
        )
        assertFalse(data.isEmpty)
        assertEquals(1, data.getAllUniqueMovies().size)
    }

    @Test
    fun testAnimeHomeDataEmpty() {
        val data = AnimeHomeData()
        assertTrue(data.isEmpty)
        assertEquals(0, data.getAllUniqueMovies().size)
    }
}
