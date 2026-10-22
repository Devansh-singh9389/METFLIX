package com.example.metflix.data.network.anime

object AnimeBackendConfig {
    const val BASE_URL: String = "http://127.0.0.1:8000/"
    const val FALLBACK_BASE_URL: String = "http://10.227.10.225:8000/"
    const val HOME_ENDPOINT: String = "api/home"
    const val HEALTH_ENDPOINT: String = "api/health"
    const val TIMEOUT_CONNECT_SECONDS: Long = 4
    const val TIMEOUT_READ_SECONDS: Long = 35
}
