package com.jimmy0933.myplayer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DoubleTapZoomTest {
    private fun matches(
        zoom: Float = 2.5f,
        priorTime: Long = 1000L,
        secondTime: Long = 1200L,
        priorX: Float = 400f,
        priorY: Float = 700f,
        secondX: Float = 416f,
        secondY: Float = 711f,
    ) = shouldResetZoomOnDoubleTap(
        zoom, priorTime, secondTime, priorX, priorY, secondX, secondY, 48f,
    )

    @Test fun doubleTapResetsZoomedImageOrVideo() {
        assertTrue(matches())
        assertTrue(matches(zoom = 1.02f))
        assertTrue(matches(zoom = 5f))
        assertTrue(matches(secondTime = 1300L))
    }

    @Test fun singleAndSlowTapsKeepZoom() {
        assertFalse(matches(priorTime = -1L))
        assertFalse(matches(secondTime = 1301L))
        assertFalse(matches(secondTime = 999L))
        assertFalse(matches(secondTime = 1000L))
    }

    @Test fun differentTapLocationsDoNotCount() {
        assertFalse(matches(secondX = 700f))
        assertFalse(matches(secondY = 850f))
    }

    @Test fun normalZoomLevelRetainsExistingTapControls() {
        assertFalse(matches(zoom = 1.0f))
        assertFalse(matches(zoom = 1.01f))
    }
}
