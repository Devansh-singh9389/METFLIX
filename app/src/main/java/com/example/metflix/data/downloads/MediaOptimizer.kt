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
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.FileDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.example.metflix.data.player.MediaCacheManager
import com.example.metflix.data.settings.DownloadPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import kotlin.coroutines.resume

object MediaOptimizer {
    private const val TAG = "MediaOptimizer"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    fun sanitizeFolderName(name: String): String {
        val sanitized = name
            .replace(Regex("""[\\/:*?"<>|]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
        return sanitized.ifBlank { "OptimizedMedia" }
    }

    fun resolveCatalogSlug(rawMediaId: String): String {
        return rawMediaId
            .substringAfterLast("/")
            .replace(Regex("""_(?:1080p|720p|480p|360p|2160p|4k)[a-zA-Z0-9]*""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""_s\d+e\d+""", RegexOption.IGNORE_CASE), "")
            .trim()
    }

    fun buildMetadataJson(
        item: DownloadedItem,
        videoFile: File,
        posterFile: File? = null
    ): String {
        val metaObj = runCatching {
            if (item.catalogJson.isNotBlank()) JSONObject(item.catalogJson) else JSONObject()
        }.getOrDefault(JSONObject())

        metaObj.put("title", item.title)
        metaObj.put("mediaId", item.mediaId)
        metaObj.put("catalogSlug", resolveCatalogSlug(item.mediaId))
        metaObj.put("mediaType", item.mediaType)
        metaObj.put("quality", item.quality)
        metaObj.put("languages", JSONArray(item.languages))
        metaObj.put("videoFileName", videoFile.name)
        metaObj.put("videoFilePath", videoFile.absolutePath)
        metaObj.put("mp4Path", videoFile.absolutePath)
        posterFile?.let { metaObj.put("posterPath", it.absolutePath) }
        metaObj.put("optimizedTimestamp", System.currentTimeMillis())

        return metaObj.toString(2)
    }

    /**
     * Optimizes a downloaded item by remuxing cached stream segments into a standalone MP4 file,
     * organizing it into a dedicated folder with metadata.json and poster.jpg, and purging the raw stream cache.
     */
    suspend fun optimize(
        context: Context,
        item: DownloadedItem,
        onProgress: (percent: Int, status: String) -> Unit = { _, _ -> }
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            onProgress(5, "Preparing destination folder...")
            val downloadPreferences = DownloadPreferences(context)
            val baseDirType = downloadPreferences.downloadLocation.value

            val targetDir = when {
                baseDirType.startsWith("/") -> File(baseDirType)
                baseDirType.equals(Environment.DIRECTORY_MOVIES, ignoreCase = true) -> {
                    context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: File(context.filesDir, "Movies")
                }
                else -> {
                    context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: File(context.filesDir, "Downloads")
                }
            }

            val cleanTitle = sanitizeFolderName(item.title)
            val dedicatedFolder = File(targetDir, cleanTitle).apply { mkdirs() }
            val isProgressiveMp4 = item.localFilePath.endsWith(".mp4", ignoreCase = true) || item.directUrl.contains(".mp4", ignoreCase = true)
            val finalVideoFile = File(dedicatedFolder, "$cleanTitle.mp4")

            // 1. If final standalone MP4/MKV file already exists and is valid, return early
            val existingFinal = listOf(
                finalVideoFile,
                File(dedicatedFolder, "$cleanTitle.mp4"),
                File(dedicatedFolder, "$cleanTitle.mkv")
            ).firstOrNull { it.exists() && it.length() > 5 * 1024 * 1024L }

            if (existingFinal != null) {
                saveMetadataAndPoster(context, dedicatedFolder, item, existingFinal)
                updateRepository(item, existingFinal)
                onProgress(100, "Already optimized!")
                return@runCatching existingFinal
            }

            // 2. Check if localFilePath already points to an existing file
            if (item.localFilePath.isNotBlank()) {
                val existingFile = File(item.localFilePath)
                if (existingFile.exists() && existingFile.length() > 5 * 1024 * 1024L) {
                    if (existingFile.name.endsWith(".mp4", ignoreCase = true) || existingFile.name.endsWith(".mkv", ignoreCase = true)) {
                        onProgress(40, "Organizing standalone video file...")
                        if (existingFile.absolutePath != finalVideoFile.absolutePath) {
                            existingFile.copyTo(finalVideoFile, overwrite = true)
                        }
                        saveMetadataAndPoster(context, dedicatedFolder, item, finalVideoFile)
                        updateRepository(item, finalVideoFile)
                        onProgress(100, "Optimized!")
                        return@runCatching finalVideoFile
                    } else if (existingFile.name.endsWith(".ts", ignoreCase = true)) {
                        // Upgrade existing .ts file (like Naruto) directly to .mp4 with full seeking and audio!
                        onProgress(40, "Upgrading TS to seekable MP4 via Media3...")
                        val transmuxSuccess = transmuxToMp4(
                            context = context,
                            videoTsFile = existingFile,
                            audioTsFile = null,
                            outputMp4File = finalVideoFile,
                            onProgress = onProgress
                        )
                        if (transmuxSuccess && finalVideoFile.exists() && finalVideoFile.length() > 1024 * 500L) {
                            if (existingFile.absolutePath != finalVideoFile.absolutePath) {
                                runCatching { existingFile.delete() }
                            }
                            saveMetadataAndPoster(context, dedicatedFolder, item, finalVideoFile)
                            updateRepository(item, finalVideoFile)
                            onProgress(100, "Optimized: Standalone MP4 ready!")
                            return@runCatching finalVideoFile
                        }
                    }
                }
            }

            // 3. Remux from HLS cache
            onProgress(15, "Reading stream manifest...")
            val manifestFile = resolveManifestFile(context, item)
            if (manifestFile == null || !manifestFile.exists()) {
                throw IllegalStateException("Stream manifest not found for ${item.title}")
            }

            val manifestContent = manifestFile.readText()
            val tempDir = File(context.cacheDir, "optimizer_temp").apply { mkdirs() }
            val tempVideoTs = File(tempDir, "temp_video_${System.currentTimeMillis()}.ts")
            val tempAudioTs = File(tempDir, "temp_audio_${System.currentTimeMillis()}.ts")

            try {
                // Extract video and audio playlists
                onProgress(25, "Extracting stream segments from cache...")
                extractSegmentsToTs(
                    context = context,
                    manifestContent = manifestContent,
                    manifestBaseUrl = manifestFile.toURI().toString(),
                    outVideoTs = tempVideoTs,
                    outAudioTs = tempAudioTs,
                    onExtractProgress = { current, total ->
                        val pct = 25 + ((current.toFloat() / total.coerceAtLeast(1)) * 45).toInt()
                        onProgress(pct, "Extracting segments ($current/$total)...")
                    }
                )

                if (!tempVideoTs.exists() || tempVideoTs.length() < 1024 * 100) {
                    throw IllegalStateException("Failed to extract video stream from cache")
                }

                // Transmux Video & Audio streams to MP4 using Google Media3 Transformer (hardware-accelerated, 0% CPU re-encoding)
                onProgress(75, "Transmuxing streams to seekable MP4...")
                val audioInput = if (tempAudioTs.exists() && tempAudioTs.length() > 188L) tempAudioTs else null
                var transmuxSuccess = transmuxToMp4(
                    context = context,
                    videoTsFile = tempVideoTs,
                    audioTsFile = audioInput,
                    outputMp4File = finalVideoFile,
                    onProgress = onProgress
                )

                if (!transmuxSuccess || !finalVideoFile.exists() || finalVideoFile.length() < 1024 * 100) {
                    Log.w(TAG, "Media3 Transformer fallback engaged for ${item.title}")
                    onProgress(80, "Applying fallback multiplexer...")
                    multiplexStreams(
                        videoTsFile = tempVideoTs,
                        audioTsFile = audioInput,
                        outputFile = finalVideoFile,
                        onProgress = onProgress
                    )
                }

                if (!finalVideoFile.exists() || finalVideoFile.length() < 1024 * 100) {
                    throw IllegalStateException("Failed to multiplex streams into standalone video")
                }

                // Save metadata and poster in the dedicated folder
                onProgress(90, "Saving metadata and poster in folder...")
                saveMetadataAndPoster(context, dedicatedFolder, item, finalVideoFile)

                // Update repository FIRST
                updateRepository(item, finalVideoFile)

                // Purge raw stream cache to free device storage ONLY IF final file is verified
                if (finalVideoFile.exists() && finalVideoFile.length() > 1024 * 500L) {
                    onProgress(95, "Purging raw stream cache...")
                    purgeStreamCache(context, manifestContent)
                }
                onProgress(100, "Optimized: Standalone video ready!")
                finalVideoFile
            } finally {
                // Clean up temp TS files
                runCatching { tempVideoTs.delete() }
                runCatching { tempAudioTs.delete() }
            }
        }
    }

    private fun resolveManifestFile(context: Context, item: DownloadedItem): File? {
        val candidates = listOf(
            item.directUrl,
            item.localFilePath
        )
        for (cand in candidates) {
            if (cand.isBlank()) continue
            val path = when {
                cand.startsWith("file://", ignoreCase = true) -> cand.removePrefix("file://")
                cand.startsWith("file:/", ignoreCase = true) -> cand.removePrefix("file:")
                else -> cand
            }
            val f = File(path)
            if (f.exists() && f.isFile && f.name.endsWith(".m3u8", ignoreCase = true)) {
                return f
            }
        }

        // Search download_manifests directory
        val manifestDir = File(context.filesDir, "download_manifests")
        if (manifestDir.exists()) {
            val cleanMediaId = item.mediaId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val found = manifestDir.listFiles { _, name -> name.contains(cleanMediaId) && name.endsWith(".m3u8") }
            if (!found.isNullOrEmpty()) {
                return found.maxByOrNull { it.lastModified() }
            }
        }
        return null
    }

    private fun extractSegmentsToTs(
        context: Context,
        manifestContent: String,
        manifestBaseUrl: String,
        outVideoTs: File,
        outAudioTs: File,
        onExtractProgress: (Int, Int) -> Unit
    ) {
        val cache = MediaCacheManager.getDownloadCache(context)
        val dataSource = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(FileDataSource.Factory())
            .createDataSource()

        var videoPlaylistUri: String? = null
        var audioPlaylistUri: String? = null

        val lines = manifestContent.lines()
        for (i in lines.indices) {
            val line = lines[i].trim()
            if (line.startsWith("#EXT-X-STREAM-INF", ignoreCase = true)) {
                if (i + 1 < lines.size && !lines[i + 1].trim().startsWith("#")) {
                    videoPlaylistUri = HlsManifestFilter.toAbsoluteUrl(lines[i + 1].trim(), manifestBaseUrl)
                }
            } else if (line.startsWith("#EXT-X-MEDIA:TYPE=AUDIO", ignoreCase = true)) {
                val uriMatcher = Pattern.compile("""URI="([^"]+)"""").matcher(line)
                if (uriMatcher.find()) {
                    audioPlaylistUri = HlsManifestFilter.toAbsoluteUrl(uriMatcher.group(1).orEmpty(), manifestBaseUrl)
                }
            }
        }

        val videoSegmentUrls = mutableListOf<String>()
        if (videoPlaylistUri != null) {
            readSegmentUrls(context, videoPlaylistUri, videoSegmentUrls)
        } else {
            // Manifest is directly a media playlist
            readSegmentUrlsFromText(manifestContent, manifestBaseUrl, videoSegmentUrls)
        }

        val audioSegmentUrls = mutableListOf<String>()
        if (audioPlaylistUri != null) {
            readSegmentUrls(context, audioPlaylistUri, audioSegmentUrls)
        }

        val totalSegments = videoSegmentUrls.size + audioSegmentUrls.size
        var processed = 0

        // Extract Video Segments
        FileOutputStream(outVideoTs).use { fos ->
            val buf = ByteArray(64 * 1024)
            for (segUrl in videoSegmentUrls) {
                readSegmentBytes(dataSource, segUrl, fos, buf)
                processed++
                onExtractProgress(processed, totalSegments)
            }
        }

        // Extract Audio Segments (if separate)
        if (audioSegmentUrls.isNotEmpty()) {
            FileOutputStream(outAudioTs).use { fos ->
                val buf = ByteArray(64 * 1024)
                for (segUrl in audioSegmentUrls) {
                    readSegmentBytes(dataSource, segUrl, fos, buf)
                    processed++
                    onExtractProgress(processed, totalSegments)
                }
            }
        }
    }

    private fun readSegmentUrls(context: Context, playlistUrl: String, outUrls: MutableList<String>) {
        val cache = MediaCacheManager.getDownloadCache(context)
        val ds = CacheDataSource.Factory().setCache(cache).setUpstreamDataSourceFactory(FileDataSource.Factory()).createDataSource()
        val text = runCatching {
            val dataSpec = DataSpec(Uri.parse(playlistUrl))
            ds.open(dataSpec)
            val bytes = ds.readAllBytes()
            ds.close()
            String(bytes)
        }.getOrNull() ?: runCatching {
            if (playlistUrl.startsWith("file:", ignoreCase = true)) {
                val path = playlistUrl.removePrefix("file://").removePrefix("file:")
                File(path).readText()
            } else {
                httpClient.newCall(Request.Builder().url(playlistUrl).build()).execute().body?.string().orEmpty()
            }
        }.getOrDefault("")

        readSegmentUrlsFromText(text, playlistUrl, outUrls)
    }

    private fun readSegmentUrlsFromText(text: String, baseUrl: String, outUrls: MutableList<String>) {
        val lines = text.lines()
        for (i in lines.indices) {
            val line = lines[i].trim()
            if (line.startsWith("#EXTINF", ignoreCase = true) && i + 1 < lines.size) {
                val next = lines[i + 1].trim()
                if (next.isNotBlank() && !next.startsWith("#")) {
                    outUrls.add(HlsManifestFilter.toAbsoluteUrl(next, baseUrl))
                }
            }
        }
    }

    private fun readSegmentBytes(
        dataSource: CacheDataSource,
        segUrl: String,
        outStream: FileOutputStream,
        buffer: ByteArray
    ) {
        try {
            val dataSpec = DataSpec(Uri.parse(segUrl))
            dataSource.open(dataSpec)
            while (true) {
                val bytesRead = dataSource.read(buffer, 0, buffer.size)
                if (bytesRead <= 0) break
                outStream.write(buffer, 0, bytesRead)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error reading cached segment ($segUrl): ${e.message}")
        } finally {
            runCatching { dataSource.close() }
        }
    }

    private fun androidx.media3.datasource.DataSource.readAllBytes(): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(32 * 1024)
        while (true) {
            val r = this.read(buf, 0, buf.size)
            if (r <= 0) break
            out.write(buf, 0, r)
        }
        return out.toByteArray()
    }

    @OptIn(UnstableApi::class)
    suspend fun transmuxToMp4(
        context: Context,
        videoTsFile: File,
        audioTsFile: File?,
        outputMp4File: File,
        onProgress: (percent: Int, status: String) -> Unit = { _, _ -> }
    ): Boolean {
        return try {
            if (outputMp4File.exists()) {
                outputMp4File.delete()
            }
            withContext(Dispatchers.Main) {
                suspendCancellableCoroutine<Boolean> { continuation ->
                    try {
                        val listener = object : Transformer.Listener {
                            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                                Log.i(TAG, "Transformer completed: ${outputMp4File.name} (${outputMp4File.length()} bytes)")
                                if (continuation.isActive) continuation.resume(true)
                            }

                            override fun onError(
                                composition: Composition,
                                exportResult: ExportResult,
                                exception: ExportException
                            ) {
                                Log.e(TAG, "Transformer error: ${exception.message}", exception)
                                if (continuation.isActive) continuation.resume(false)
                            }
                        }

                        val transformer = Transformer.Builder(context.applicationContext)
                            .addListener(listener)
                            .build()

                        val videoItem = EditedMediaItem.Builder(
                            MediaItem.fromUri(Uri.fromFile(videoTsFile))
                        ).build()

                        val progressJob = CoroutineScope(Dispatchers.Main).launch {
                            val progressHolder = ProgressHolder()
                            while (continuation.isActive) {
                                delay(250)
                                if (!continuation.isActive) break
                                val state = transformer.getProgress(progressHolder)
                                if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                                    val pct = 75 + ((progressHolder.progress / 100f) * 15).toInt()
                                    onProgress(pct, "Transmuxing to MP4 ($pct%)...")
                                }
                            }
                        }

                        continuation.invokeOnCancellation {
                            progressJob.cancel()
                            runCatching { transformer.cancel() }
                        }

                        if (audioTsFile != null && audioTsFile.exists() && audioTsFile.length() > 188L) {
                            val audioItem = EditedMediaItem.Builder(
                                MediaItem.fromUri(Uri.fromFile(audioTsFile))
                            ).build()
                            val composition = Composition.Builder(
                                listOf(
                                    EditedMediaItemSequence.Builder(listOf(videoItem)).build(),
                                    EditedMediaItemSequence.Builder(listOf(audioItem)).build()
                                )
                            ).build()
                            transformer.start(composition, outputMp4File.absolutePath)
                        } else {
                            transformer.start(videoItem, outputMp4File.absolutePath)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to start transformer: ${e.message}", e)
                        if (continuation.isActive) continuation.resume(false)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "transmuxToMp4 exception: ${e.message}", e)
            false
        }
    }

    fun multiplexStreams(
        videoTsFile: File,
        audioTsFile: File?,
        outputFile: File,
        onProgress: (percent: Int, status: String) -> Unit = { _, _ -> }
    ): Boolean {
        return try {
            // If there is no separate audio stream, the video file already contains the complete stream
            if (audioTsFile == null || !audioTsFile.exists() || audioTsFile.length() < 188L) {
                videoTsFile.copyTo(outputFile, overwrite = true)
                return outputFile.exists() && outputFile.length() > 0
            }

            // Pure Kotlin Transport Stream Multiplexer
            // Combines video (PID 0x0100) and audio (PID 0x0101) with unified PAT and PMT
            val patPacket = createPatPacket()
            val pmtPacket = createPmtPacket()

            val videoLength = videoTsFile.length()
            val audioLength = audioTsFile.length()
            val totalBytes = videoLength + audioLength
            var bytesProcessed = 0L

            val videoBuf = ByteArray(188 * 10) // 10 packets (~1.88 KB) for tight interleaving
            val audioBuf = ByteArray(188 * 1)  // 1 packet (188 B) for synchronized PTS alignment

            FileOutputStream(outputFile).buffered(128 * 1024).use { out ->
                // Write initial PAT and PMT
                out.write(patPacket)
                out.write(pmtPacket)

                FileInputStream(videoTsFile).buffered(128 * 1024).use { vIn ->
                    FileInputStream(audioTsFile).buffered(128 * 1024).use { aIn ->
                        var vDone = false
                        var aDone = false
                        var packetsSincePatPmt = 0

                        while (!vDone || !aDone) {
                            // 1. Write Video Block
                            if (!vDone) {
                                val vRead = vIn.read(videoBuf)
                                if (vRead <= 0) {
                                    vDone = true
                                } else {
                                    var offset = 0
                                    while (offset + 188 <= vRead) {
                                        if (videoBuf[offset] == 0x47.toByte()) {
                                            val pid = ((videoBuf[offset + 1].toInt() and 0x1F) shl 8) or (videoBuf[offset + 2].toInt() and 0xFF)
                                            // Filter out source PAT (0) or PMT (4096 / 0x1000) from source file
                                            if (pid != 0 && pid != 0x1000) {
                                                out.write(videoBuf, offset, 188)
                                                packetsSincePatPmt++
                                            }
                                        }
                                        offset += 188
                                    }
                                    bytesProcessed += vRead
                                }
                            }

                            // 2. Write Audio Block
                            if (!aDone) {
                                val aRead = aIn.read(audioBuf)
                                if (aRead <= 0) {
                                    aDone = true
                                } else {
                                    var offset = 0
                                    while (offset + 188 <= aRead) {
                                        if (audioBuf[offset] == 0x47.toByte()) {
                                            val pid = ((audioBuf[offset + 1].toInt() and 0x1F) shl 8) or (audioBuf[offset + 2].toInt() and 0xFF)
                                            // Filter out source PAT (0) or PMT
                                            if (pid != 0 && pid != 0x1000) {
                                                // Remap audio PID to 0x0101
                                                audioBuf[offset + 1] = ((audioBuf[offset + 1].toInt() and 0xE0) or 0x01).toByte()
                                                audioBuf[offset + 2] = 0x01.toByte()
                                                out.write(audioBuf, offset, 188)
                                                packetsSincePatPmt++
                                            }
                                        }
                                        offset += 188
                                    }
                                    bytesProcessed += aRead
                                }
                            }

                            // Frequently re-emit PAT and PMT (every 200 packets / ~37 KB) for instant player seek recovery in VLC and ExoPlayer
                            if (packetsSincePatPmt >= 200) {
                                out.write(patPacket)
                                out.write(pmtPacket)
                                packetsSincePatPmt = 0
                            }

                            if (totalBytes > 0) {
                                val pct = 75 + ((bytesProcessed.toFloat() / totalBytes) * 15).toInt()
                                onProgress(pct, "Multiplexing video and audio ($pct%)...")
                            }
                        }
                    }
                }
                out.flush()
            }

            outputFile.exists() && outputFile.length() > 0
        } catch (e: Exception) {
            Log.e(TAG, "Multiplexing failed: ${e.message}", e)
            false
        }
    }

    fun createPatPacket(): ByteArray {
        val packet = ByteArray(188) { 0xFF.toByte() }
        packet[0] = 0x47.toByte() // Sync byte
        packet[1] = 0x40.toByte() // PUSI = 1, PID = 0x0000 (high byte)
        packet[2] = 0x00.toByte() // PID = 0x0000 (low byte)
        packet[3] = 0x10.toByte() // Adaptation = 01 (payload only), Continuity counter = 0
        packet[4] = 0x00.toByte() // Pointer field = 0

        // PAT Section data
        val section = byteArrayOf(
            0x00.toByte(),               // Table ID = 0x00 (PAT)
            0xB0.toByte(), 0x0D.toByte(), // Section syntax = 1, Section length = 13 bytes
            0x00.toByte(), 0x01.toByte(), // Transport Stream ID = 1
            0xC1.toByte(),               // Version = 0, Current = 1
            0x00.toByte(),               // Section number = 0
            0x00.toByte(),               // Last section number = 0
            0x00.toByte(), 0x01.toByte(), // Program number = 1
            0xF0.toByte(), 0x00.toByte()  // Reserved (111) + Program Map PID = 0x1000 (4096)
        )
        System.arraycopy(section, 0, packet, 5, section.size)

        // CRC-32 / MPEG-2
        val crc = calculateMpeg2Crc(packet, 5, section.size)
        packet[5 + section.size] = ((crc shr 24) and 0xFF).toByte()
        packet[5 + section.size + 1] = ((crc shr 16) and 0xFF).toByte()
        packet[5 + section.size + 2] = ((crc shr 8) and 0xFF).toByte()
        packet[5 + section.size + 3] = (crc and 0xFF).toByte()

        return packet
    }

    fun createPmtPacket(): ByteArray {
        val packet = ByteArray(188) { 0xFF.toByte() }
        packet[0] = 0x47.toByte() // Sync byte
        packet[1] = 0x50.toByte() // PUSI = 1, PID = 0x1000 (high byte: 0x10 or 0x40 = 0x50)
        packet[2] = 0x00.toByte() // PID = 0x1000 (low byte)
        packet[3] = 0x10.toByte() // Adaptation = 01 (payload only), Continuity counter = 0
        packet[4] = 0x00.toByte() // Pointer field = 0

        // PMT Section data
        val section = byteArrayOf(
            0x02.toByte(),               // Table ID = 0x02 (PMT)
            0xB0.toByte(), 0x17.toByte(), // Section syntax = 1, Section length = 23 bytes
            0x00.toByte(), 0x01.toByte(), // Program number = 1
            0xC1.toByte(),               // Version = 0, Current = 1
            0x00.toByte(),               // Section number = 0
            0x00.toByte(),               // Last section number = 0
            0xE1.toByte(), 0x00.toByte(), // PCR PID = 0x0100 (Video track PCR)
            0xF0.toByte(), 0x00.toByte(), // Program Info length = 0
            // Stream 1 (Video)
            0x1B.toByte(),               // Stream Type = 0x1B (H.264 / AVC Video)
            0xE1.toByte(), 0x00.toByte(), // Elementary PID = 0x0100
            0xF0.toByte(), 0x00.toByte(), // ES Info length = 0
            // Stream 2 (Audio)
            0x0F.toByte(),               // Stream Type = 0x0F (ISO/IEC 13818-7 Audio with ADTS transport syntax / AAC)
            0xE1.toByte(), 0x01.toByte(), // Elementary PID = 0x0101
            0xF0.toByte(), 0x00.toByte()  // ES Info length = 0
        )
        System.arraycopy(section, 0, packet, 5, section.size)

        // CRC-32 / MPEG-2
        val crc = calculateMpeg2Crc(packet, 5, section.size)
        packet[5 + section.size] = ((crc shr 24) and 0xFF).toByte()
        packet[5 + section.size + 1] = ((crc shr 16) and 0xFF).toByte()
        packet[5 + section.size + 2] = ((crc shr 8) and 0xFF).toByte()
        packet[5 + section.size + 3] = (crc and 0xFF).toByte()

        return packet
    }

    fun calculateMpeg2Crc(data: ByteArray, offset: Int, length: Int): Int {
        var crc = 0xFFFFFFFF.toInt()
        for (i in offset until offset + length) {
            val b = data[i].toInt() and 0xFF
            crc = crc xor (b shl 24)
            for (j in 0 until 8) {
                crc = if ((crc and 0x80000000.toInt()) != 0) {
                    (crc shl 1) xor 0x04C11DB7
                } else {
                    crc shl 1
                }
            }
        }
        return crc
    }

    private fun saveMetadataAndPoster(
        context: Context,
        folder: File,
        item: DownloadedItem,
        videoFile: File
    ) {
        val metaFile = File(folder, "metadata.json")
        val posterFile = File(folder, "poster.jpg")
        val jsonStr = buildMetadataJson(item, videoFile, posterFile)
        metaFile.writeText(jsonStr)

        // Download poster
        if (item.posterUrl.isNotBlank()) {
            val posterFile = File(folder, "poster.jpg")
            if (!posterFile.exists() || posterFile.length() == 0L) {
                runCatching {
                    if (item.posterUrl.startsWith("http", ignoreCase = true)) {
                        httpClient.newCall(Request.Builder().url(item.posterUrl).build()).execute().use { resp ->
                            if (resp.isSuccessful) {
                                resp.body?.bytes()?.let { posterFile.writeBytes(it) }
                            }
                        }
                    } else if (item.posterUrl.startsWith("file:", ignoreCase = true)) {
                        File(item.posterUrl.removePrefix("file://").removePrefix("file:")).copyTo(posterFile, overwrite = true)
                    }
                }
            }
        }
    }

    private fun purgeStreamCache(context: Context, manifestContent: String) {
        runCatching {
            val cache = MediaCacheManager.getDownloadCache(context)
            // Remove cached resources associated with the segments
            val matcher = Pattern.compile("""https?://[^\s\r\n]+""")
            val m = matcher.matcher(manifestContent)
            while (m.find()) {
                val url = m.group(0) ?: continue
                cache.removeResource(url)
            }
        }
    }

    private fun updateRepository(item: DownloadedItem, finalFile: File) {
        val updated = item.copy(
            localFilePath = finalFile.absolutePath,
            directUrl = Uri.fromFile(finalFile).toString()
        )
        DownloadedCatalogRepository.instance?.addOrUpdateDownloadedItem(updated)
    }
}
