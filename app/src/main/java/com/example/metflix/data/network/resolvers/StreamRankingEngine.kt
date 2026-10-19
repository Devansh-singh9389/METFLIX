package com.example.metflix.data.network.resolvers

import android.util.Log
import com.example.metflix.data.entities.MediaId
import com.example.metflix.data.entities.StreamCandidate
import com.example.metflix.data.network.providers.StreamProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StreamRankingEngine @Inject constructor(
    private val providers: Set<@JvmSuppressWildcards StreamProvider>
) {
    companion object {
        private const val TAG = "StreamRankingEngine"
        private const val PROVIDER_TIMEOUT_MS = 2800L
    }

    /**
     * Concurrently collects candidates from all compatible providers,
     * enforces timeouts with cancellation, and ranks by verified audio and reliability.
     */
    suspend fun collectAndRankCandidates(
        mediaId: MediaId,
        title: String,
        releaseYear: String = "",
        preferHindi: Boolean = true,
        preferredQuality: String = "Auto"
    ): List<StreamCandidate> = withContext(Dispatchers.IO) {
        val compatibleProviders = providers.filter { it.canHandle(mediaId) }
        if (compatibleProviders.isEmpty()) {
            Log.w(TAG, "No compatible providers registered for media: $mediaId")
            return@withContext emptyList()
        }

        Log.d(TAG, "Querying ${compatibleProviders.size} providers for '$title' ($mediaId)")

        val candidates = mutableListOf<StreamCandidate>()

        val deferredResults = compatibleProviders.map { provider ->
            async {
                try {
                    withTimeoutOrNull(PROVIDER_TIMEOUT_MS) {
                        provider.findStreams(mediaId, title, releaseYear)
                    } ?: emptyList()
                } catch (e: Exception) {
                    Log.w(TAG, "Provider ${provider.id} error: ${e.message}")
                    emptyList()
                }
            }
        }

        val allResults = deferredResults.awaitAll()
        allResults.forEach { list -> candidates.addAll(list) }

        // Rank candidates by audio verification, preferred quality, media format, and latency
        val ranked = candidates.sortedByDescending { candidate ->
            computeCandidateScore(candidate, preferHindi, preferredQuality)
        }

        Log.d(TAG, "Collected and ranked ${ranked.size} stream candidates for '$title'")
        ranked
    }

    private fun computeCandidateScore(
        candidate: StreamCandidate,
        preferHindi: Boolean,
        preferredQuality: String
    ): Long {
        var score = 0L

        // 1. Audio track verification bonus (Hindi priority when requested)
        val hasHindi = candidate.reportedLanguages.any { AudioLanguageDetector.isHindiTrack(it) } ||
                candidate.providerName.contains("hindi", ignoreCase = true) ||
                candidate.providerName.contains("dual", ignoreCase = true)

        if (preferHindi) {
            if (candidate.isAudioVerified && hasHindi) {
                score += 2500L
            } else if (hasHindi) {
                score += 1800L
            }
        } else {
            if (candidate.isAudioVerified) score += 300L
        }

        // 2. Preferred Quality bonus
        if (!preferredQuality.equals("Auto", ignoreCase = true)) {
            val qTag = preferredQuality.lowercase().replace(" ", "").replace("hd", "").replace("uhd", "").replace("fhd", "")
            val candQ = candidate.quality.lowercase()
            if (candQ.contains(qTag)) {
                score += 1200L
            }
        }

        // 3. Format & direct stream quality bonus
        if (candidate.isDirectStream) {
            score += 300L
        }

        // 4. Subtract latency penalty (capped)
        val latency = candidate.latencyMs?.coerceIn(0L, 800L) ?: 300L
        score += (800L - latency)

        return score
    }
}
