package com.jimmy0933.myplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BatchDeletePolicyTest {
    @Test fun mediaStoreRequestsAreLimitedTo2000Items() {
        val all = (1..4501).toList()
        assertEquals(2000, BatchDeletePolicy.nextChunk(all, true).size)
        assertEquals(1, BatchDeletePolicy.nextChunk(all, false).size)
        assertEquals(501, BatchDeletePolicy.nextChunk(all.drop(4000), true).size)
    }

    @Test fun repeatedMediaItemsAreSubmittedOnlyOnce() {
        val media = listOf("content://media/1", "content://media/2", "content://media/1")
        assertEquals(listOf("content://media/1", "content://media/2"),
            BatchDeletePolicy.unique(media) { it })
    }

    @Test fun permissionsChooseOneConfirmationSource() {
        assertTrue(BatchDeletePolicy.needsAppConfirmation(31, true))
        assertTrue(BatchDeletePolicy.needsAppConfirmation(35, true))
        assertFalse(BatchDeletePolicy.needsAppConfirmation(35, false))
        assertFalse(BatchDeletePolicy.needsAppConfirmation(30, false))
        assertFalse(BatchDeletePolicy.needsAppConfirmation(29, false))
        assertTrue(BatchDeletePolicy.needsAppConfirmation(28, false))
        assertTrue(BatchDeletePolicy.usesMediaStoreDeleteRequest(30))
        assertFalse(BatchDeletePolicy.usesMediaStoreDeleteRequest(29))
    }
}
