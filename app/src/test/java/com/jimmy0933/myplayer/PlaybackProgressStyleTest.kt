package com.jimmy0933.myplayer

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackProgressStyleTest {
    @Test fun defaultMatchesOriginalProgressBar() {
        assertEquals(3f, PlaybackProgressStyle.trackDp(1f), .001f)
        assertEquals(10f, PlaybackProgressStyle.thumbDp(1f), .001f)
        assertEquals(30f, PlaybackProgressStyle.touchHeightDp(1f), .001f)
        assertEquals(.96f, PlaybackProgressStyle.width(PlaybackProgressStyle.DEFAULT_WIDTH), .001f)
    }

    @Test fun largerSizesEnlargeTrackThumbAndTouchTarget() {
        assertEquals(7.5f, PlaybackProgressStyle.trackDp(2.5f), .001f)
        assertEquals(25f, PlaybackProgressStyle.thumbDp(2.5f), .001f)
        assertEquals(75f, PlaybackProgressStyle.touchHeightDp(2.5f), .001f)
    }

    @Test fun playbackControlsKeepTheExistingTwoSecondDefault() {
        assertEquals(2, PlaybackControlsTimeout.DEFAULT_SECONDS)
        assertEquals(2000L, PlaybackControlsTimeout.delayMillis(PlaybackControlsTimeout.DEFAULT_SECONDS))
    }

    @Test fun playbackControlsTimerSupportsOneToFifteenSeconds() {
        assertEquals(1000L, PlaybackControlsTimeout.delayMillis(1))
        assertEquals(5000L, PlaybackControlsTimeout.delayMillis(5))
        assertEquals(15000L, PlaybackControlsTimeout.delayMillis(15))
    }

    @Test fun neverHideIsSupportedAndMalformedSavedValuesAreClamped() {
        assertEquals(null, PlaybackControlsTimeout.delayMillis(PlaybackControlsTimeout.NEVER))
        assertEquals(PlaybackControlsTimeout.NEVER, PlaybackControlsTimeout.normalize(0))
        assertEquals(1, PlaybackControlsTimeout.normalize(-5))
        assertEquals(15, PlaybackControlsTimeout.normalize(999))
    }

    @Test fun scaleAndWidthAreClampedForPersistedSettings() {
        assertEquals(.8f, PlaybackProgressStyle.scale(-1f), .001f)
        assertEquals(2.5f, PlaybackProgressStyle.scale(99f), .001f)
        assertEquals(.6f, PlaybackProgressStyle.width(0f), .001f)
        assertEquals(1f, PlaybackProgressStyle.width(2f), .001f)
        assertEquals(PlaybackProgressStyle.DEFAULT_SCALE, PlaybackProgressStyle.scale(Float.NaN), .001f)
        assertEquals(PlaybackProgressStyle.DEFAULT_WIDTH, PlaybackProgressStyle.width(Float.POSITIVE_INFINITY), .001f)
    }
}
