/*
 * Copyright 2026 Google LLC
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

package com.example.metflix.data.downloads

import android.content.Context
import android.os.Environment
import android.util.Log
import com.example.metflix.data.settings.DownloadPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

object StorageDownloadScanner {
    private const val TAG = "StorageDownloadScanner"

    /**
     * Finds and gathers all potential download directories across Internal Storage,
     * attached SD Cards, USB drives, custom user folders, and app storage.
     */
    fun collectStorageRoots(context: Context): List<File> {
        val roots = LinkedHashSet<File>()

        // 1. User's currently active download location
        val activeLocation = DownloadPreferences.from(context).downloadLocation.value
        if (activeLocation.startsWith("/")) {
            roots.add(File(activeLocation))
        } else if (activeLocation.equals(Environment.DIRECTORY_MOVIES, ignoreCase = true)) {
            context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)?.let { roots.add(it) }
        } else {
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.let { roots.add(it) }
        }

        // 2. User's custom locations
        DownloadPreferences.from(context).customLocations.value.forEach { (_, uriPath) ->
            if (uriPath.startsWith("/")) {
                roots.add(File(uriPath))
            }
        }

        // 3. System public directories
        runCatching {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)?.let { roots.add(it) }
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)?.let { roots.add(it) }
        }

        // 4. App-safe external files dirs for all mounted volumes (Internal + SD Cards)
        runCatching {
            context.getExternalFilesDirs(null)?.filterNotNull()?.forEach { roots.add(it) }
            context.getExternalFilesDirs(Environment.DIRECTORY_DOWNLOADS)?.filterNotNull()?.forEach { roots.add(it) }
            context.getExternalFilesDirs(Environment.DIRECTORY_MOVIES)?.filterNotNull()?.forEach { roots.add(it) }
        }

        // 5. Scan /storage/ mount points directly for SD Cards / USB drives
        runCatching {
            val storageRoot = File("/storage")
            if (storageRoot.exists() && storageRoot.isDirectory) {
                storageRoot.listFiles()?.forEach { vol ->
                    if (vol.isDirectory && vol.name != "emulated" && vol.name != "self" && !vol.name.startsWith(".")) {
                        roots.add(vol)
                        val dlDir = File(vol, "Download")
                        if (dlDir.exists()) roots.add(dlDir)
                        val moviesDir = File(vol, "Movies")
                        if (moviesDir.exists()) roots.add(moviesDir)
                    }
                }
            }
        }

        // Also check if any root has a "MetFlix" subfolder
        val extendedRoots = LinkedHashSet(roots)
        roots.forEach { root ->
            val metflixSub = File(root, "MetFlix")
            if (metflixSub.exists() && metflixSub.isDirectory) {
                extendedRoots.add(metflixSub)
            }
        }

        return extendedRoots.filter { it.exists() && it.canRead() }
    }

    /**
     * Scans all storage roots and returns all discovered DownloadedItem records.
     */
    suspend fun scanAll(context: Context): List<DownloadedItem> = withContext(Dispatchers.IO) {
        val discovered = mutableListOf<DownloadedItem>()
        val seenIds = HashSet<String>()
        val roots = collectStorageRoots(context)

        Log.d(TAG, "Scanning ${roots.size} storage roots for existing downloads...")

        for (root in roots) {
            try {
                scanDirectory(root, discovered, seenIds)
            } catch (e: Exception) {
                Log.w(TAG, "Error scanning root ${root.absolutePath}: ${e.message}")
            }
        }

        Log.d(TAG, "Storage scan completed. Total items discovered: ${discovered.size}")
        discovered
    }

    private fun scanDirectory(dir: File, discovered: MutableList<DownloadedItem>, seenIds: HashSet<String>) {
        val files = dir.listFiles() ?: return

        for (file in files) {
            if (file.isDirectory) {
                // Check if this subfolder is an organized media folder containing metadata.json or details.json
                val metaFile = File(file, "metadata.json").takeIf { it.exists() }
                    ?: File(file, "details.json").takeIf { it.exists() }

                if (metaFile != null) {
                    val parsedItem = parseMetadataFolder(file, metaFile)
                    if (parsedItem != null && seenIds.add(parsedItem.id)) {
                        discovered.add(parsedItem)
                    }
                } else {
                    // Look for standalone video file directly inside this directory
                    val videoFiles = file.listFiles { f ->
                        f.isFile && (f.name.endsWith(".mp4", ignoreCase = true) || f.name.endsWith(".mkv", ignoreCase = true) || f.name.endsWith(".ts", ignoreCase = true))
                    }
                    if (!videoFiles.isNullOrEmpty()) {
                        for (vf in videoFiles) {
                            val item = createItemFromStandaloneVideo(vf, file)
                            if (item != null && seenIds.add(item.id)) {
                                discovered.add(item)
                            }
                        }
                    }
                }
            } else if (file.isFile) {
                // Loose video files directly in the root
                if (file.name.endsWith(".mp4", ignoreCase = true) || file.name.endsWith(".mkv", ignoreCase = true)) {
                    val item = createItemFromStandaloneVideo(file, dir)
                    if (item != null && seenIds.add(item.id)) {
                        discovered.add(item)
                    }
                }
            }
        }
    }

    private fun parseMetadataFolder(folder: File, metaFile: File): DownloadedItem? {
        return runCatching {
            val json = JSONObject(metaFile.readText())
            val title = json.optString("title").ifBlank { folder.name }
            val mediaId = json.optString("mediaId").ifBlank { folder.name }
            val mediaType = json.optString("mediaType", "MOVIE")
            val seasonNumber = if (json.has("seasonNumber")) json.optInt("seasonNumber") else null
            val episodeNumber = if (json.has("episodeNumber")) json.optInt("episodeNumber") else null
            val quality = json.optString("quality", "1080p")
            val posterUrl = json.optString("posterUrl", "")

            // Locate the actual video file in this folder
            val videoFile = listOf(
                json.optString("videoFilePath").takeIf { it.isNotBlank() }?.let { File(it) },
                json.optString("mp4Path").takeIf { it.isNotBlank() }?.let { File(it) },
                json.optString("videoFileName").takeIf { it.isNotBlank() }?.let { File(folder, it) }
            ).firstOrNull { it != null && it.exists() }
                ?: folder.listFiles { f ->
                    f.isFile && (f.name.endsWith(".mkv", ignoreCase = true) || f.name.endsWith(".mp4", ignoreCase = true) || f.name.endsWith(".ts", ignoreCase = true))
                }?.firstOrNull()

            val actualPath = videoFile?.absolutePath ?: ""
            if (actualPath.isBlank()) return null

            val languages = mutableListOf<String>()
            json.optJSONArray("languages")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val lang = arr.optString(i)
                    if (lang.isNotBlank()) languages.add(lang)
                }
            }

            DownloadedItem(
                id = mediaId,
                title = title,
                mediaType = mediaType,
                mediaId = mediaId,
                seasonNumber = seasonNumber,
                episodeNumber = episodeNumber,
                localFilePath = actualPath,
                downloadId = json.optLong("downloadId", folder.lastModified()),
                quality = quality,
                languages = languages,
                timestamp = json.optLong("optimizedTimestamp", folder.lastModified()),
                posterUrl = posterUrl.ifBlank {
                    val localPoster = File(folder, "poster.jpg")
                    if (localPoster.exists()) localPoster.absolutePath else ""
                },
                catalogJson = json.toString(),
                directUrl = json.optString("directUrl", "")
            )
        }.getOrNull()
    }

    private fun createItemFromStandaloneVideo(file: File, parentFolder: File): DownloadedItem? {
        if (!file.exists() || file.length() < 1024 * 1024L) return null // Ignore files < 1MB

        val rawName = file.nameWithoutExtension
        val title = rawName
            .replace(Regex("""[._]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

        val isAnime = title.contains("Episode", ignoreCase = true) || title.contains("Anime", ignoreCase = true)
        val sRegex = Regex("""S(\d+)""", RegexOption.IGNORE_CASE)
        val eRegex = Regex("""E(\d+)""", RegexOption.IGNORE_CASE)
        val season = sRegex.find(rawName)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val episode = eRegex.find(rawName)?.groupValues?.getOrNull(1)?.toIntOrNull()

        val mediaId = rawName.lowercase().replace(" ", "-")

        val posterFile = File(parentFolder, "poster.jpg")
        val posterPath = if (posterFile.exists()) posterFile.absolutePath else ""

        return DownloadedItem(
            id = mediaId,
            title = title,
            mediaType = if (isAnime || season != null) "SERIES" else "MOVIE",
            mediaId = mediaId,
            seasonNumber = season,
            episodeNumber = episode,
            localFilePath = file.absolutePath,
            downloadId = file.lastModified(),
            quality = "1080p",
            languages = listOf("Original"),
            timestamp = file.lastModified(),
            posterUrl = posterPath,
            catalogJson = "{\"title\":\"$title\",\"mediaId\":\"$mediaId\"}",
            directUrl = ""
        )
    }
}
