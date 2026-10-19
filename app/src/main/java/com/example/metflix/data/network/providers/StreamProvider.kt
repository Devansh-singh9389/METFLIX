package com.example.metflix.data.network.providers

import com.example.metflix.data.entities.MediaId
import com.example.metflix.data.entities.StreamCandidate

/**
 * Common pluggable interface for all stream providers (Movies, TV, Anime).
 * Isolates website-specific search, token generation, and parsing from the player and UI.
 */
interface StreamProvider {
    val id: String
    val displayName: String
    val priority: Int

    /**
     * Returns true if this provider supports the given MediaId type.
     */
    fun canHandle(mediaId: MediaId): Boolean

    /**
     * Resolves stream candidates for the given media request.
     * Implementations should respect coroutine cancellation and timeouts.
     */
    suspend fun findStreams(
        mediaId: MediaId,
        title: String,
        releaseYear: String = ""
    ): List<StreamCandidate>
}
