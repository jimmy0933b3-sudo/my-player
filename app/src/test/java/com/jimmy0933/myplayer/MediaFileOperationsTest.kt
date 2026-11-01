package com.jimmy0933.myplayer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaFileOperationsTest {
    @Test fun rejectsUnsafeFileNames() {
        listOf("", "   ", ".", "..", "photo/name.jpg", "photo\\\\name.jpg",
            "a\u0000b.jpg", "x".repeat(201)).forEach {
            assertFalse("should reject $it", isValidMediaFilename(it))
        }
    }

    @Test fun allowsCommonMediaNames() {
        listOf("旅行 2026.mp4", "IMG_001.jpg", "sunset (copy).heic",
            "影片.mov").forEach {
            assertTrue("should allow $it", isValidMediaFilename(it))
        }
    }
}
