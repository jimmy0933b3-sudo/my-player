package com.jimmy0933.myplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class FolderScrollScopeTest {
    @Test fun eachFolderHasOwnIndependentBookmark() {
        assertNotEquals(folderScrollScope("FOLDERS", "Pictures/"), folderScrollScope("FOLDERS", "DCIM/"))
        assertNotEquals(folderScrollScope("FOLDERS", null), folderScrollScope("FOLDERS", ""))
        assertEquals(folderScrollScope("FOLDERS", "DCIM/"), folderScrollScope("FOLDERS", "DCIM/"))
    }

    @Test fun galleryTabsAndFoldersDoNotCollide() {
        assertNotEquals(folderScrollScope("ALL", null), folderScrollScope("FOLDERS", null))
        assertNotEquals(folderScrollScope("FAVORITES", null), folderScrollScope("ALL", null))
        assertEquals(folderScrollScope("ALL", null), folderScrollScope("ALL", "ignored"))
    }
}
