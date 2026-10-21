package com.example.metflix.data.network.anime

object AnimeBackendConfig {
    const val BASE_URL: String = "https://metflix-anime.onrender.com/"
    const val HOME_ENDPOINT: String = "api/home"
    const val HEALTH_ENDPOINT: String = "api/health"
    const val TIMEOUT_CONNECT_SECONDS: Long = 25
    const val TIMEOUT_READ_SECONDS: Long = 35
}
