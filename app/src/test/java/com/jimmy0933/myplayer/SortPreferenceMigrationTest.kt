package com.jimmy0933.myplayer

import org.junit.Assert.assertEquals
import org.junit.Test

class SortPreferenceMigrationTest {
    @Test fun newInstallDefaultsToModificationTime() {
        assertEquals(MediaSort.MODIFIED, SortPreferenceMigration.migrate(null, 0))
    }

    @Test fun legacyAutoSavedCreatedDefaultMigratesOnce() {
        assertEquals(MediaSort.MODIFIED, SortPreferenceMigration.migrate("CREATED", 0))
        assertEquals(MediaSort.MODIFIED, SortPreferenceMigration.migrate("CREATED", 1))
    }

    @Test fun savedChoicesAfterMigrationStayUserControlled() {
        assertEquals(MediaSort.CREATED, SortPreferenceMigration.migrate("CREATED", 2))
        assertEquals(MediaSort.MODIFIED, SortPreferenceMigration.migrate("MODIFIED", 2))
        assertEquals(MediaSort.SIZE, SortPreferenceMigration.migrate("SIZE", 2))
        assertEquals(MediaSort.SIZE, SortPreferenceMigration.migrate("SIZE", 1))
    }

    @Test fun invalidSavedValuesSafelyDefaultToModificationTime() {
        assertEquals(MediaSort.MODIFIED, SortPreferenceMigration.migrate("INVALID", 2))
    }
}
