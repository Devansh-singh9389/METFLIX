package com.example.metflix.presentation.screens.videoPlayer

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ResolutionRequestTrackerTest {
    @Test
    fun onlyLatestResolutionRequestRemainsCurrent() {
        val tracker = ResolutionRequestTracker()
        val first = tracker.begin()
        val second = tracker.begin()

        assertFalse(tracker.isCurrent(first))
        assertTrue(tracker.isCurrent(second))
    }
}
