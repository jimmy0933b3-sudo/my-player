package com.jimmy0933.myplayer

import org.junit.Assert.assertEquals
import org.junit.Test

class ListScrollbarMathTest {
    @Test fun thumbIsProportionalButHasTouchFriendlyMinimum() {
        assertEquals(120f, ListScrollbarMath.thumbHeightPx(600f, 50, 10, 48f), 0.001f)
        assertEquals(48f, ListScrollbarMath.thumbHeightPx(600f, 10_000, 10, 48f), 0.001f)
        assertEquals(600f, ListScrollbarMath.thumbHeightPx(600f, 4, 4, 48f), 0.001f)
        assertEquals(30f, ListScrollbarMath.thumbHeightPx(30f, 100, 1, 48f), 0.001f)
    }

    @Test fun thumbTracksScrollAndExactEndOfList() {
        assertEquals(0f, ListScrollbarMath.positionFraction(0, 0, 120f, 100, 10, false), 0.001f)
        assertEquals(0.5f, ListScrollbarMath.positionFraction(45, 0, 120f, 100, 10, false), 0.001f)
        assertEquals(1f, ListScrollbarMath.positionFraction(87, 0, 120f, 100, 10, true), 0.001f)
        assertEquals(0f, ListScrollbarMath.positionFraction(0, 0, 120f, 8, 8, true), 0.001f)
    }

    @Test fun draggingJumpsToTopMiddleAndBottom() {
        assertEquals(0, ListScrollbarMath.indexForTrackPosition(0f, 24f, 600f, 48f, 1000, 10))
        assertEquals(495, ListScrollbarMath.indexForTrackPosition(300f, 24f, 600f, 48f, 1000, 10))
        assertEquals(990, ListScrollbarMath.indexForTrackPosition(600f, 24f, 600f, 48f, 1000, 10))
        assertEquals(0, ListScrollbarMath.indexForTrackPosition(200f, 20f, 600f, 600f, 5, 5))
    }

    @Test fun thumbGrabOffsetPreventsJumpingOnFirstTouch() {
        assertEquals(495, ListScrollbarMath.indexForTrackPosition(300f, 24f, 600f, 48f, 1000, 10))
        assertEquals(495, ListScrollbarMath.indexForTrackPosition(310f, 34f, 600f, 48f, 1000, 10))
    }
}
