package com.jimmy0933.myplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoEndModeTest {
    @Test fun savedModeRoundTripsAndDefaultsToStop() {
        assertEquals(VideoEndMode.STOP, VideoEndMode.fromSaved(null))
        assertEquals(VideoEndMode.STOP, VideoEndMode.fromSaved("not-a-mode"))
        assertEquals(VideoEndMode.STOP, VideoEndMode.fromSaved("STOP"))
        assertEquals(VideoEndMode.NEXT, VideoEndMode.fromSaved("NEXT"))
        assertEquals(VideoEndMode.REPEAT, VideoEndMode.fromSaved("REPEAT"))
    }

    @Test fun nextVideoSkipsPhotosAndUsesGalleryOrdering() {
        assertEquals(3, nextVideoAtEnd(listOf(true, false, false, true, false), 0))
        assertEquals(4, nextVideoAtEnd(listOf(true, true, false, false, true), 1))
    }

    @Test fun finalVideoStopsInsteadOfWrapping() {
        assertNull(nextVideoAtEnd(listOf(true, false, true, false), 2))
        assertNull(nextVideoAtEnd(listOf(false, false, true), 2))
        assertNull(nextVideoAtEnd(emptyList(), 0))
    }
}
