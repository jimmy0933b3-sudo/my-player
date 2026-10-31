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

    @Test fun pinchImageVerticalAndTapCannotTriggerSeek() {
        assertFalse(shouldSeekVideoSwipe(true, true, 140f, 0f, 18f))
        assertFalse(shouldSeekVideoSwipe(false, false, 140f, 0f, 18f))
        assertFalse(shouldSeekVideoSwipe(true, false, 9f, 0f, 18f))
        assertFalse(shouldSeekVideoSwipe(true, false, 20f, 100f, 18f))
    }
}
