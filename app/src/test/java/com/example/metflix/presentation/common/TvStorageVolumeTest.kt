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

package com.example.metflix.presentation.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TvStorageVolumeTest {

    @Test
    fun testFormatBytes_formatsGbCorrectly() {
        val tenGb = 10L * 1024L * 1024L * 1024L
        val formatted = StorageVolumeManager.formatBytes(tenGb)
        assertEquals("10.0 GB", formatted)

        val oneAndHalfGb = (1.5 * 1024.0 * 1024.0 * 1024.0).toLong()
        assertEquals("1.5 GB", StorageVolumeManager.formatBytes(oneAndHalfGb))
    }

    @Test
    fun testFormatBytes_formatsMbCorrectly() {
        val fiveHundredMb = 500L * 1024L * 1024L
        val formatted = StorageVolumeManager.formatBytes(fiveHundredMb)
        assertEquals("500 MB", formatted)
    }

    @Test
    fun testGetBreadcrumbs_constructsHierarchicalTrail() {
        val path = File("/storage/emulated/0/Movies/Action")
        val crumbs = StorageVolumeManager.getBreadcrumbs(path)

        assertTrue(crumbs.isNotEmpty())
        assertEquals("Action", crumbs.last().name)
        assertEquals(path.absolutePath, crumbs.last().file.absolutePath)

        // Ancestors should include Movies, 0, emulated, storage
        val names = crumbs.map { it.name }
        assertTrue(names.contains("Movies"))
        assertTrue(names.contains("Internal Storage") || names.contains("0"))
    }

    @Test
    fun testSubfoldersFilter_ignoresHiddenAndFiles() {
        val tempDir = java.nio.file.Files.createTempDirectory("picker_test").toFile()
        try {
            val normalDir = File(tempDir, "VisibleFolder").apply { mkdirs() }
            val hiddenDir = File(tempDir, ".hiddenFolder").apply { mkdirs() }
            val testFile = File(tempDir, "video.mp4").apply { createNewFile() }

            val subFolders = tempDir.listFiles { file ->
                file.isDirectory && !file.name.startsWith(".") && file.canRead()
            }?.toList() ?: emptyList()

            assertEquals(1, subFolders.size)
            assertEquals("VisibleFolder", subFolders[0].name)
            assertFalse(subFolders.any { it.name == ".hiddenFolder" })
            assertFalse(subFolders.any { it.name == "video.mp4" })
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
