package com.jimmy0933.myplayer

import org.junit.Assert.assertEquals
import org.junit.Test

class VerticalVolumeGeometryTest {
    @Test fun topIsMaximumAndBottomIsMuted() {
        assertEquals(1f, VerticalVolumeGeometry.fractionAt(12f, 200f, 12f), .0001f)
        assertEquals(0f, VerticalVolumeGeometry.fractionAt(188f, 200f, 12f), .0001f)
        assertEquals(.5f, VerticalVolumeGeometry.fractionAt(100f, 200f, 12f), .0001f)
    }

    @Test fun outOfBoundsTouchesClampAndGeometryRoundTrips() {
        assertEquals(1f, VerticalVolumeGeometry.fractionAt(-20f, 200f, 12f), .0001f)
        assertEquals(0f, VerticalVolumeGeometry.fractionAt(250f, 200f, 12f), .0001f)
        for (fraction in listOf(0f, .1f, .25f, .5f, .75f, .9f, 1f)) {
            val y = VerticalVolumeGeometry.yAt(fraction, 168f, 12f)
            assertEquals(fraction, VerticalVolumeGeometry.fractionAt(y, 168f, 12f), .0001f)
        }
    }

    @Test fun invalidDimensionsAreSafe() {
        assertEquals(0f, VerticalVolumeGeometry.fractionAt(10f, 0f, 12f), .0001f)
        assertEquals(0f, VerticalVolumeGeometry.fractionAt(Float.NaN, 100f, 12f), .0001f)
        assertEquals(0f, VerticalVolumeGeometry.yAt(0.5f, Float.NaN, 12f), .0001f)
    }
}
