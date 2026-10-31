package com.jimmy0933.myplayer

import org.junit.Assert.assertEquals
import org.junit.Test

class FastSeekTest {
    @Test fun swipeUsesOriginalPositionAndFinalDisplacement() {
        assertEquals(16000L, swipeSeekTarget(3000, 200f, 60000))
        assertEquals(9500L, swipeSeekTarget(3000, 100f, 60000))
        assertEquals(0L, swipeSeekTarget(3000, -200f, 60000))
        assertEquals(60000L, swipeSeekTarget(3000, 10000f, 60000))
    }

    @Test fun progressScrubResolvesOnceOnFingerRelease() {
        assertEquals(30000L, progressSeekTarget(.5f, 60000))
        assertEquals(60000L, progressSeekTarget(1.5f, 60000))
        assertEquals(0L, progressSeekTarget(-2f, 60000))
        assertEquals(0L, progressSeekTarget(Float.NaN, 60000))
    }

    @Test fun unknownDurationReturnsSafePosition() {
        assertEquals(0L, swipeSeekTarget(1000, 100f, -1))
        assertEquals(0L, progressSeekTarget(.5f, 0))
    }
}
