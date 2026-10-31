package com.jimmy0933.myplayer

import org.junit.Assert.assertEquals
import java.time.ZoneId
import java.time.LocalDateTime
import org.junit.Test

class MediaLibraryTest {
    @Test
    fun dateGroupingUsesLocalCalendarDayAndSupportsUnknownTime() {
        val taipei = ZoneId.of("Asia/Taipei")
        val morning = LocalDateTime.of(2026, 10, 31, 0, 3).atZone(taipei)
            .toInstant().toEpochMilli()
        val evening = LocalDateTime.of(2026, 10, 31, 23, 58).atZone(taipei)
            .toInstant().toEpochMilli()
        val nextDay = LocalDateTime.of(2026, 11, 1, 0, 1).atZone(taipei)
            .toInstant().toEpochMilli()
        assertEquals("2026-10-31", MediaLibrary.createdDayKey(morning, taipei))
        assertEquals(MediaLibrary.createdDayKey(morning, taipei), MediaLibrary.createdDayKey(evening, taipei))
        assertEquals("2026-11-01", MediaLibrary.createdDayKey(nextDay, taipei))
        assertEquals("unknown", MediaLibrary.createdDayKey(0L, taipei))
    }

    @Test
    fun readableSizeFormatsUnits() {
        assertEquals("0 B", MediaLibrary.readableSize(0))
        assertEquals("1.0 KB", MediaLibrary.readableSize(1024))
        assertEquals("1.5 MB", MediaLibrary.readableSize(1572864))
    }
}
