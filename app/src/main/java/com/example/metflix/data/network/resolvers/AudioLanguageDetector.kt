package com.example.metflix.data.network.resolvers

import java.util.Locale

/**
 * Normalizes language tags, ISO 639 codes, and track labels.
 * Enables deterministic auto-selection of Hindi and multi-language tracks in ExoPlayer.
 */
object AudioLanguageDetector {

    private val HINDI_PATTERNS = listOf(
        "hi", "hin", "hi-in", "hindi", "hin [org]", "hindi dub", "hindi dubbed"
    )

    private val ENGLISH_PATTERNS = listOf(
        "en", "eng", "en-us", "en-gb", "english", "original"
    )

    private val JAPANESE_PATTERNS = listOf(
        "ja", "jpn", "ja-jp", "japanese", "jap"
    )

    /**
     * Normalizes any language string or tag into standard 2-letter ISO code if recognized.
     */
    fun normalize(language: String?): String {
        if (language.isNullOrBlank()) return "und"
        val clean = language.trim().lowercase(Locale.ROOT)

        return when {
            HINDI_PATTERNS.any { clean == it || clean.startsWith("$it ") || clean.contains("hindi") } -> "hi"
            ENGLISH_PATTERNS.any { clean == it || clean.startsWith("$it ") || clean.contains("english") } -> "en"
            JAPANESE_PATTERNS.any { clean == it || clean.startsWith("$it ") || clean.contains("japanese") } -> "ja"
            clean.startsWith("ta") || clean.contains("tamil") -> "ta"
            clean.startsWith("te") || clean.contains("telugu") -> "te"
            clean.startsWith("ml") || clean.contains("malayalam") -> "ml"
            clean.startsWith("kn") || clean.contains("kannada") -> "kn"
            clean.startsWith("ko") || clean.contains("korean") -> "ko"
            clean.startsWith("es") || clean.contains("spanish") -> "es"
            else -> clean
        }
    }

    /**
     * Checks if the given track (from ExoPlayer Format or provider label) is Hindi.
     */
    fun isHindiTrack(language: String?, label: String? = null): Boolean {
        if (normalize(language) == "hi") return true
        if (!label.isNullOrBlank()) {
            val labelClean = label.lowercase(Locale.ROOT)
            if (labelClean.contains("hindi") || labelClean.contains("hin")) return true
        }
        return false
    }

    /**
     * Maps normalized code to user-friendly UI display title.
     */
    fun getDisplayLabel(langCode: String): String {
        return when (normalize(langCode)) {
            "hi" -> "Hindi"
            "en" -> "English"
            "ja" -> "Japanese"
            "ta" -> "Tamil"
            "te" -> "Telugu"
            "ml" -> "Malayalam"
            "kn" -> "Kannada"
            "ko" -> "Korean"
            "es" -> "Spanish"
            "und" -> "Default Track"
            else -> langCode.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
        }
    }
}
