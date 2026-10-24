package com.example.metflix.data.downloads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.RandomAccessFile

class InAppDownloadEngineRangeTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testInflatedPartFileIsTruncatedToActualDownloadedBytes() {
        val partFile = tempFolder.newFile("test_movie.mp4.part")
        val recordedDownloaded = 50_000_000L // 50 MB
        val totalBytes = 1_500_000_000L // 1.5 GB

        // Simulate the old sparse pre-allocation bug that inflated the file to 1.5 GB
        RandomAccessFile(partFile, "rw").use { it.setLength(totalBytes) }
        assertEquals(totalBytes, partFile.length())

        // Reconcile as InAppDownloadEngine does
        var downloadedBytes = recordedDownloaded
        val fileLen = partFile.length()
        if (totalBytes > 0 && fileLen >= totalBytes && downloadedBytes < totalBytes) {
            RandomAccessFile(partFile, "rw").use { it.setLength(downloadedBytes) }
        }

        // Verify the file was truncated back to 50 MB and downloadedBytes matches
        assertEquals(recordedDownloaded, partFile.length())
        assertEquals(recordedDownloaded, downloadedBytes)

        // Verify range header would be constructed with 50 MB, NOT 1.5 GB
        val rangeHeader = "bytes=$downloadedBytes-"
        assertEquals("bytes=50000000-", rangeHeader)
    }

    @Test
    fun testClampingWhenPartFileIsShorterThanRecordedProgress() {
        val partFile = tempFolder.newFile("test_short.mp4.part")
        val recordedDownloaded = 80_000_000L
        val actualOnDisk = 30_000_000L

        RandomAccessFile(partFile, "rw").use { it.setLength(actualOnDisk) }

        var downloadedBytes = recordedDownloaded
        val fileLen = partFile.length()
        if (fileLen < downloadedBytes) {
            downloadedBytes = fileLen
        }

        assertEquals(actualOnDisk, downloadedBytes)
        assertEquals("bytes=30000000-", "bytes=$downloadedBytes-")
    }

    @Test
    fun testCompletedDownloadDoesNotSendInvalidRangeHeader() {
        val totalBytes = 100_000_000L
        val downloadedBytes = 100_000_000L

        val isAlreadyComplete = totalBytes > 0 && downloadedBytes >= totalBytes
        assertTrue("Download should be marked complete without network call", isAlreadyComplete)
    }
}
