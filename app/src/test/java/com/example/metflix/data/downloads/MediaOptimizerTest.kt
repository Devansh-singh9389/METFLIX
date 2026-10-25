package com.example.metflix.data.downloads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MediaOptimizerTest {

    @Test
    fun testSanitizeFolderName_stripsIllegalCharacters() {
        val raw = "Your Name: Weathering With You? / A <Special> *Edition* | \"Director's Cut\""
        val sanitized = MediaOptimizer.sanitizeFolderName(raw)

        assertFalse(sanitized.contains(":"))
        assertFalse(sanitized.contains("?"))
        assertFalse(sanitized.contains("/"))
        assertFalse(sanitized.contains("<"))
        assertFalse(sanitized.contains(">"))
        assertFalse(sanitized.contains("*"))
        assertFalse(sanitized.contains("|"))
        assertFalse(sanitized.contains("\""))
        assertTrue(sanitized.contains("Your Name"))
    }

    @Test
    fun testSanitizeFolderName_handlesEmptyAndBlank() {
        val emptyResult = MediaOptimizer.sanitizeFolderName("")
        assertEquals("OptimizedMedia", emptyResult)

        val symbolsOnly = MediaOptimizer.sanitizeFolderName("???***///")
        assertEquals("OptimizedMedia", symbolsOnly)
    }

    @Test
    fun testResolveCatalogSlug_stripsQualityAndSeasonEpisodeSuffixes() {
        assertEquals("your-name", MediaOptimizer.resolveCatalogSlug("your-name_1080pFHD"))
        assertEquals("suzume", MediaOptimizer.resolveCatalogSlug("suzume_720pHD"))
        assertEquals("attack-on-titan", MediaOptimizer.resolveCatalogSlug("attack-on-titan_s1e1"))
        assertEquals("demon-slayer", MediaOptimizer.resolveCatalogSlug("demon-slayer_1080pFHD_s2e5"))
        assertEquals("solo-leveling", MediaOptimizer.resolveCatalogSlug("solo-leveling"))
    }

    @Test
    fun testBuildMetadataJson_producesValidJsonWithOriginalCatalogFields() {
        val originalCatalogJson = "{\"synopsis\":\"A teenage boy and girl embark on a quest...\",\"rating\":\"8.9\",\"releaseYear\":\"2016\",\"type\":\"movie\"}"

        val dummyMp4 = File("C:/Downloads/Your Name/Your Name.mp4")
        val dummyPoster = File("C:/Downloads/Your Name/poster.jpg")

        val item = DownloadedItem(
            id = "sess_123",
            mediaId = "your-name_1080pFHD",
            title = "Your Name",
            mediaType = "ANIME_MOVIE",
            localFilePath = dummyMp4.absolutePath,
            posterUrl = "https://example.com/poster.jpg",
            catalogJson = originalCatalogJson
        )

        val jsonStr = MediaOptimizer.buildMetadataJson(item, dummyMp4, dummyPoster)

        assertTrue(jsonStr.contains("\"title\": \"Your Name\"") || jsonStr.contains("\"title\":\"Your Name\""))
        assertTrue(jsonStr.contains("\"ANIME_MOVIE\""))
        assertTrue(jsonStr.contains("\"catalogSlug\": \"your-name\"") || jsonStr.contains("\"catalogSlug\":\"your-name\""))
        assertTrue(jsonStr.contains("\"rating\": \"8.9\"") || jsonStr.contains("\"rating\":\"8.9\""))
        assertTrue(jsonStr.contains("\"releaseYear\": \"2016\"") || jsonStr.contains("\"releaseYear\":\"2016\""))
        assertTrue(jsonStr.contains("A teenage boy and girl embark on a quest..."))
        assertTrue(jsonStr.contains("Your Name.mp4"))
        assertTrue(jsonStr.contains("poster.jpg"))
    }

    @Test
    fun testCreatePatPacket_generatesValid188BytePacket() {
        val pat = MediaOptimizer.createPatPacket()
        assertEquals(188, pat.size)
        assertEquals(0x47.toByte(), pat[0]) // Sync byte
        assertEquals(0x40.toByte(), pat[1]) // PUSI = 1, PID = 0
        assertEquals(0x00.toByte(), pat[2])
    }

    @Test
    fun testCreatePmtPacket_generatesValid188BytePacket() {
        val pmt = MediaOptimizer.createPmtPacket()
        assertEquals(188, pmt.size)
        assertEquals(0x47.toByte(), pmt[0]) // Sync byte
        assertEquals(0x50.toByte(), pmt[1]) // PUSI = 1, PID = 0x1000 (high byte)
        assertEquals(0x00.toByte(), pmt[2]) // PID = 0x1000 (low byte)
    }

    @Test
    fun testMultiplexStreams_mergesVideoAndAudioPackets() {
        val tempDir = java.nio.file.Files.createTempDirectory("optimizer_test").toFile()
        try {
            val vFile = File(tempDir, "video.ts")
            val aFile = File(tempDir, "audio.ts")
            val outFile = File(tempDir, "out.ts")

            // Create a fake TS packet for video (PID 0x0100)
            val vPkt = ByteArray(188) { 0xAA.toByte() }
            vPkt[0] = 0x47.toByte()
            vPkt[1] = 0x01.toByte()
            vPkt[2] = 0x00.toByte()
            vFile.outputStream().use { fos ->
                repeat(20) { fos.write(vPkt) }
            }

            // Create a fake TS packet for audio (PID 0x0100 to be remapped to 0x0101)
            val aPkt = ByteArray(188) { 0xBB.toByte() }
            aPkt[0] = 0x47.toByte()
            aPkt[1] = 0x01.toByte()
            aPkt[2] = 0x00.toByte()
            aFile.outputStream().use { fos ->
                repeat(20) { fos.write(aPkt) }
            }

            val success = MediaOptimizer.multiplexStreams(vFile, aFile, outFile)
            assertTrue(success)
            assertTrue(outFile.exists())
            assertTrue(outFile.length() > 0)
            assertEquals(0, outFile.length() % 188) // Must be aligned to 188-byte packets

            // Read the output and verify PAT (PID 0), PMT (PID 0x1000), Video (0x0100), Audio (0x0101)
            val outBytes = outFile.readBytes()
            val pids = mutableSetOf<Int>()
            for (offset in 0 until outBytes.size step 188) {
                assertEquals(0x47.toByte(), outBytes[offset])
                val pid = ((outBytes[offset + 1].toInt() and 0x1F) shl 8) or (outBytes[offset + 2].toInt() and 0xFF)
                pids.add(pid)
            }

            assertTrue("PAT should be present", pids.contains(0))
            assertTrue("PMT should be present", pids.contains(0x1000))
            assertTrue("Video PID should be present", pids.contains(0x0100))
            assertTrue("Audio PID should be present and remapped to 0x0101", pids.contains(0x0101))
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
