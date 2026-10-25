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

import com.example.metflix.data.downloads.ActiveDownloadState
import com.example.metflix.data.downloads.DownloadStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class IdmDownloadProgressTest {

    @Test
    fun testEightParts_atZeroPercent_allEmpty() {
        val state = ActiveDownloadState(
            downloadId = 1L,
            mediaId = "test",
            title = "Test",
            progressPercent = 0
        )
        for (i in 0 until 8) {
            assertEquals(0.0f, state.getPartProgress(i), 0.001f)
        }
    }

    @Test
    fun testEightParts_atFiftyPercent_halfFilled() {
        val state = ActiveDownloadState(
            downloadId = 1L,
            mediaId = "test",
            title = "Test",
            progressPercent = 50
        )
        // 50% means exactly parts 0, 1, 2, 3 are 100% full, parts 4, 5, 6, 7 are 0%
        for (i in 0..3) {
            assertEquals(1.0f, state.getPartProgress(i), 0.001f)
        }
        for (i in 4..7) {
            assertEquals(0.0f, state.getPartProgress(i), 0.001f)
        }
    }

    @Test
    fun testEightParts_atMidSegment_fillsPartially() {
        // 31.25% = 2.5 segments (Part 0 full, Part 1 full, Part 2 half full)
        val state = ActiveDownloadState(
            downloadId = 1L,
            mediaId = "test",
            title = "Test",
            progressPercent = 31 // 31% is (31 - 25) / 12.5 = 6 / 12.5 = 0.48
        )
        assertEquals(1.0f, state.getPartProgress(0), 0.001f)
        assertEquals(1.0f, state.getPartProgress(1), 0.001f)
        assertEquals(0.48f, state.getPartProgress(2), 0.01f)
        assertEquals(0.0f, state.getPartProgress(3), 0.001f)
    }

    @Test
    fun testEightParts_atHundredPercent_allFull() {
        val state = ActiveDownloadState(
            downloadId = 1L,
            mediaId = "test",
            title = "Test",
            progressPercent = 100
        )
        for (i in 0 until 8) {
            assertEquals(1.0f, state.getPartProgress(i), 0.001f)
        }
    }

    @Test
    fun testFormattedProgress_withTotal_andWithoutTotal() {
        val withTotal = ActiveDownloadState(
            downloadId = 1L,
            mediaId = "test",
            title = "Test",
            bytesDownloaded = 124500000L,
            totalBytes = 594000000L,
            progressPercent = 21
        )
        assertEquals("118.7 MB / 566.5 MB (21%)", withTotal.formattedProgress)

        val withoutTotal = ActiveDownloadState(
            downloadId = 2L,
            mediaId = "test2",
            title = "Test 2",
            bytesDownloaded = 50000000L,
            totalBytes = 0L,
            progressPercent = 15
        )
        assertEquals("47.7 MB downloaded (15%)", withoutTotal.formattedProgress)
    }

    @Test
    fun testFormattedSpeed_andEta() {
        val state = ActiveDownloadState(
            downloadId = 1L,
            mediaId = "test",
            title = "Test",
            speedBps = 5 * 1024 * 1024L,
            etaSeconds = 85L,
            status = DownloadStatus.RUNNING
        )
        assertEquals("5.0 MB/s", state.formattedSpeed)
        assertEquals("1m 25s remaining", state.formattedEta)
    }
}
