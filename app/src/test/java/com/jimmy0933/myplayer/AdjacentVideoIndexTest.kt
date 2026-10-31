package com.jimmy0933.myplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AdjacentVideoIndexTest {
    @Test fun skipsPhotosWhenMovingForwardAndBackward() {
        val videos = listOf(false, true, false, false, true, false, true)
        assertEquals(4, adjacentVideoIndex(videos, 1, 1))
        assertEquals(1, adjacentVideoIndex(videos, 4, -1))
        assertEquals(6, adjacentVideoIndex(videos, 4, 1))
        assertEquals(4, adjacentVideoIndex(videos, 6, -1))
    }

    @Test fun disablesNavigationAtTheEnds() {
        val videos = listOf(false, true, false, true, false)
        assertNull(adjacentVideoIndex(videos, 1, -1))
        assertNull(adjacentVideoIndex(videos, 3, 1))
        assertNull(adjacentVideoIndex(listOf(false, false), 0, 1))
    }

    @Test fun invalidIndicesAndDirectionsAreSafe() {
        assertNull(adjacentVideoIndex(emptyList(), 0, 1))
        assertNull(adjacentVideoIndex(listOf(true), -1, 1))
        assertNull(adjacentVideoIndex(listOf(true), 9, 1))
        assertNull(adjacentVideoIndex(listOf(true), 0, 0))
    }
}
