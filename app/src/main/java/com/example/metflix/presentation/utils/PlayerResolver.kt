/*
 * Copyright 2025 Google LLC
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

package com.example.metflix.presentation.utils

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.widget.Toast
import com.example.metflix.data.entities.WatchOptionType
import com.example.metflix.data.entities.WatchProvider

/**
 * TMDb Helper-inspired player and streaming provider resolver for Android TV.
 * Resolves movie titles and provider configurations into direct app intents, deep links,
 * or fallback store/browser experiences.
 */
object PlayerResolver {
    private const val TAG = "PlayerResolver"

    // Supported Android TV streaming packages
    val KNOWN_PACKAGES = mapOf(
        "netflix" to listOf("com.netflix.ninja", "com.netflix.mediaclient"),
        "prime" to listOf("com.amazon.amazonvideo.livingroom", "com.amazon.avod.thirdpartyclient"),
        "amazon" to listOf("com.amazon.amazonvideo.livingroom", "com.amazon.avod.thirdpartyclient"),
        "disney" to listOf("in.startv.hotstar", "com.disney.disneyplus"),
        "hotstar" to listOf("in.startv.hotstar", "com.disney.disneyplus"),
        "apple" to listOf("com.apple.atve.androidtv.appletv"),
        "jio" to listOf("com.jio.media.ondemand"),
        "zee" to listOf("com.graymatrix.did"),
        "sony" to listOf("com.msl.liv.tv"),
        "youtube" to listOf("com.google.android.youtube.tv", "com.google.android.youtube"),
        "google" to listOf("com.google.android.videos")
    )

    fun isPackageInstalled(context: Context, packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun findInstalledPackage(context: Context, providerName: String, fallbackPackage: String? = null): String? {
        if (!fallbackPackage.isNullOrBlank() && isPackageInstalled(context, fallbackPackage)) {
            return fallbackPackage
        }
        val lower = providerName.lowercase()
        for ((key, candidates) in KNOWN_PACKAGES) {
            if (lower.contains(key)) {
                candidates.firstOrNull { isPackageInstalled(context, it) }?.let { return it }
            }
        }
        return fallbackPackage
    }

    /**
     * Resolves and launches a playback or streaming session for a movie.
     */
    fun launchProvider(
        context: Context,
        provider: WatchProvider,
        movieTitle: String,
        tmdbLink: String? = null
    ) {
        val installedPkg = findInstalledPackage(context, provider.name, provider.appPackage)

        if (installedPkg != null) {
            val launched = tryLaunchDeepLink(context, installedPkg, provider.name, movieTitle)
            if (launched) return
        }

        // If not installed or deep-link failed, try web search or TMDB link
        val webFallbackLaunched = tryLaunchWebSearch(context, provider.name, movieTitle, tmdbLink)
        if (!webFallbackLaunched && !provider.appPackage.isNullOrBlank()) {
            openPlayStore(context, provider.appPackage)
        }
    }

    private fun tryLaunchDeepLink(
        context: Context,
        packageName: String,
        providerName: String,
        movieTitle: String
    ): Boolean {
        val lower = providerName.lowercase()
        val encodedTitle = Uri.encode(movieTitle)

        val intent = when {
            lower.contains("netflix") -> {
                Intent(Intent.ACTION_VIEW, Uri.parse("nflx://www.netflix.com/Search?query=$encodedTitle")).apply {
                    setPackage(packageName)
                }
            }
            lower.contains("prime") || lower.contains("amazon") -> {
                Intent(Intent.ACTION_VIEW, Uri.parse("https://app.primevideo.com/search?phrase=$encodedTitle")).apply {
                    setPackage(packageName)
                }
            }
            lower.contains("hotstar") || lower.contains("disney") -> {
                Intent(Intent.ACTION_VIEW, Uri.parse("https://www.hotstar.com/search?q=$encodedTitle")).apply {
                    setPackage(packageName)
                }
            }
            lower.contains("youtube") -> {
                Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=$encodedTitle+official+trailer")).apply {
                    setPackage(packageName)
                }
            }
            else -> {
                context.packageManager.getLaunchIntentForPackage(packageName)
            }
        }

        return if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
                true
            } catch (e: Exception) {
                Log.w(TAG, "Failed to launch intent for $packageName: ${e.message}")
                false
            }
        } else {
            false
        }
    }

    private fun tryLaunchWebSearch(
        context: Context,
        providerName: String,
        movieTitle: String,
        tmdbLink: String?
    ): Boolean {
        val targetUrl = tmdbLink?.takeIf { it.isNotBlank() }
            ?: "https://www.google.com/search?q=${Uri.encode("watch $movieTitle on $providerName")}"

        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to launch browser: ${e.message}")
            false
        }
    }

    fun launchYouTubeTrailer(context: Context, movieTitle: String) {
        val encoded = Uri.encode("$movieTitle official trailer")
        val ytIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=$encoded")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val candidates = listOf("com.google.android.youtube.tv", "com.google.android.youtube")
        val installed = candidates.firstOrNull { isPackageInstalled(context, it) }
        if (installed != null) {
            ytIntent.setPackage(installed)
        }
        try {
            context.startActivity(ytIntent)
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open YouTube", Toast.LENGTH_SHORT).show()
        }
    }

    fun openPlayStore(context: Context, packageName: String) {
        try {
            val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(marketIntent)
        } catch (_: Exception) {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webIntent)
        }
    }
}
