package com.jimmy0933.myplayer

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaLibraryTest {
    @Test
    fun readableSizeFormatsUnits() {
        assertEquals("0 B", MediaLibrary.readableSize(0))
        assertEquals("1.0 KB", MediaLibrary.readableSize(1024))
        assertEquals("1.5 MB", MediaLibrary.readableSize(1572864))
    }
}
