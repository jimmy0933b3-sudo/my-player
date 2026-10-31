package com.jimmy0933.myplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotEquals
import org.junit.Test

class MediaFoldersTest {
    @Test
    fun normalizesMediaStoreRelativePaths() {
        assertEquals("DCIM/Camera", MediaFolders.normalizePath("DCIM//Camera/"))
        assertEquals("Pictures/Screenshots", MediaFolders.normalizePath("Pictures\\Screenshots/"))
        assertEquals("Movies/Trips", MediaFolders.normalizePath("/Movies/./Trips/"))
    }

    @Test
    fun navigationGoesUpExactlyOneDirectory() {
        val current = MediaFolders.folderKey("external_primary", "DCIM/Camera")
        assertEquals("external_primary:DCIM/Camera", current)
        assertEquals("external_primary:DCIM", MediaFolders.parentKey(current))
        assertEquals("external_primary:", MediaFolders.parentKey("external_primary:DCIM"))
        assertNull(MediaFolders.parentKey("external_primary:"))
    }

    @Test
    fun sameDirectoryOnSdCardAndInternalStorageAreDifferent() {
        val internal = MediaFolders.folderKey("external_primary", "DCIM/Camera")
        val sdCard = MediaFolders.folderKey("39AB-1C22", "DCIM/Camera")
        assertNotEquals(internal, sdCard)
        assertEquals("內部儲存空間 / DCIM / Camera", MediaFolders.folderLabel(internal))
    }

    @Test
    fun missingFavoritesCanStillBeRemovedFromList() {
        val missing = MediaFolders.unavailableFavorite("external_primary:Movies/Clips")
        assertFalse(missing.available)
        assertEquals("Clips", missing.name)
        assertEquals("external_primary:Movies", missing.parentKey)
        assertEquals(0, missing.mediaCount)
    }
}
