package com.jimmy0933.myplayer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoGestureTest {
    @Test fun horizontalVideoGestureSeeksEvenWhenViewerIsZoomed() {
        // Video zoom level intentionally does not affect one-finger seek priority.
        assertTrue(shouldSeekVideoSwipe(true, false, 130f, 8f, 18f))
        assertTrue(shouldSeekVideoSwipe(true, false, -110f, -5f, 18f))
    }

    @Test fun centerTapTogglesVideoOnlyWithinMiddleRegion() {
        // Test with both portrait and landscape dimensions.
        assertTrue(isCenterVideoTap(540f, 960f, 1080f, 1920f))
        assertTrue(isCenterVideoTap(960f, 540f, 1920f, 1080f))
        assertTrue(isCenterVideoTap(270f, 540f, 1080f, 1920f))
        assertFalse(isCenterVideoTap(200f, 960f, 1080f, 1920f))
        assertFalse(isCenterVideoTap(900f, 960f, 1080f, 1920f))
    }

    @Test fun seekbarAndBottomTapsNeverPauseOrPlay() {
        // Bottom portion stays inert for tap-to-pause even with controls hidden.
        assertFalse(isCenterVideoTap(540f, 1740f, 1080f, 1920f))
        assertFalse(isCenterVideoTap(540f, 1320f, 1080f, 1920f))
        assertFalse(isCenterVideoTap(960f, 960f, 1920f, 1080f))
        assertFalse(isCenterVideoTap(960f, 710f, 1920f, 1080f))
        assertFalse(isCenterVideoTap(540f, 200f, 1080f, 1920f))
        assertFalse(isCenterVideoTap(0f, 0f, 0f, 1920f))
        assertFalse(isCenterVideoTap(500f, 300f, 1080f, 0f))
    }

    @Test fun pinchImageVerticalAndTapCannotTriggerSeek() {
        assertFalse(shouldSeekVideoSwipe(true, true, 140f, 0f, 18f))
        assertFalse(shouldSeekVideoSwipe(false, false, 140f, 0f, 18f))
        assertFalse(shouldSeekVideoSwipe(true, false, 9f, 0f, 18f))
        assertFalse(shouldSeekVideoSwipe(true, false, 20f, 100f, 18f))
    }
}
