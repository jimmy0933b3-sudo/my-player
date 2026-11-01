package com.jimmy0933.myplayer

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaSwitchAnimationTest {
    @Test fun upwardSwipeShowsNextMediaWithAShortBottomEntrance() {
        assertEquals(1, MediaSwitchAnimation.direction(3, 4))
        assertEquals(MediaSwitchAnimation.PHOTO_ENTER_DISTANCE_DP,
            MediaSwitchAnimation.enteringOffsetDp(1), .001f)
        assertEquals("↑ 下一部", MediaSwitchAnimation.indicatorText(1))
    }

    @Test fun downwardSwipeShowsPreviousMediaWithAShortTopEntrance() {
        assertEquals(-1, MediaSwitchAnimation.direction(4, 3))
        assertEquals(-MediaSwitchAnimation.PHOTO_ENTER_DISTANCE_DP,
            MediaSwitchAnimation.enteringOffsetDp(-1), .001f)
        assertEquals("↓ 上一部", MediaSwitchAnimation.indicatorText(-1))
    }

    @Test fun noMediaChangeNeverTriggersAnAnimation() {
        assertEquals(0, MediaSwitchAnimation.direction(7, 7))
        assertEquals(0f, MediaSwitchAnimation.enteringOffsetDp(0), .001f)
        assertEquals("", MediaSwitchAnimation.indicatorText(0))
    }

    @Test fun skipThroughMultipleItemsStillHasOnlyOneDirection() {
        assertEquals(1, MediaSwitchAnimation.direction(1, 8))
        assertEquals(-1, MediaSwitchAnimation.direction(8, 1))
    }
}
