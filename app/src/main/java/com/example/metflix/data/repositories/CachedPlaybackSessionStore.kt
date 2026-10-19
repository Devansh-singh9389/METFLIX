package com.example.metflix.data.repositories

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

data class CachedPlaybackSession(
    val url: String,
    val title: String,
    val torrentId: Int,
    val fileId: Int?
)

@Singleton
class CachedPlaybackSessionStore @Inject constructor() {
    private val sessions = ConcurrentHashMap<String, CachedPlaybackSession>()

    fun create(session: CachedPlaybackSession): String {
        val id = UUID.randomUUID().toString()
        sessions[id] = session
        return id
    }

    fun get(id: String): CachedPlaybackSession? = sessions[id]

    fun replace(id: String, session: CachedPlaybackSession) {
        sessions[id] = session
    }

    fun remove(id: String) {
        sessions.remove(id)
    }
}
