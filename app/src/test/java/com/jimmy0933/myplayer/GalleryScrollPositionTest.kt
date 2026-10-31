package com.jimmy0933.myplayer

import org.junit.Assert.assertEquals
import org.junit.Test

class GalleryScrollPositionTest {
    @Test
    fun restoresExactItemInUngroupedGallery() {
        val days = listOf("2026-10-31", "2026-10-31", "2026-10-30", "2026-10-29")
        assertEquals(0, galleryLazyItemIndex(0, days, 0, false))
        assertEquals(3, galleryLazyItemIndex(3, days, 0, false))
        assertEquals(7, galleryLazyItemIndex(3, days, 4, false))
    }

    @Test
    fun accountsForDateHeadersAndNestedFolderRows() {
        val days = listOf("2026-10-31", "2026-10-31", "2026-10-30", "2026-10-30", "2026-10-29")
        assertEquals(1, galleryLazyItemIndex(0, days, 0, true))
        assertEquals(2, galleryLazyItemIndex(1, days, 0, true))
        assertEquals(4, galleryLazyItemIndex(2, days, 0, true))
        assertEquals(8, galleryLazyItemIndex(4, days, 1, true))
        assertEquals(11, galleryLazyItemIndex(4, days, 4, true))
    }

    @Test
    fun invalidIndicesDoNotTryToScroll() {
        assertEquals(-1, galleryLazyItemIndex(-1, listOf("one"), 0, true))
        assertEquals(-1, galleryLazyItemIndex(1, listOf("one"), 0, true))
        assertEquals(-1, galleryLazyItemIndex(0, emptyList(), 0, false))
    }
}
