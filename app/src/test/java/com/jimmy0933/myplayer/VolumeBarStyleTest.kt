package com.jimmy0933.myplayer

import org.junit.Assert.assertEquals
import org.junit.Test

class VolumeBarStyleTest {
    @Test fun defaultsPreserveExistingVolumeAppearance() {
        assertEquals(0f, VolumeBarStyle.DEFAULT_TRANSPARENCY, .0001f)
        assertEquals(5f, VolumeBarStyle.DEFAULT_TRACK_DP, .0001f)
        assertEquals(1f, VolumeBarStyle.alpha(VolumeBarStyle.DEFAULT_TRANSPARENCY), .0001f)
        assertEquals(8f, VolumeBarStyle.thumbRadiusDp(VolumeBarStyle.DEFAULT_TRACK_DP), .0001f)
    }

    @Test fun increasingTransparencyLowersOverlayOpacity() {
        assertEquals(0.75f, VolumeBarStyle.alpha(0.25f), .0001f)
        assertEquals(0.15f, VolumeBarStyle.alpha(1f), .0001f)
        assertEquals(0.15f, VolumeBarStyle.alpha(0.85f), .0001f)
    }

    @Test fun thicknessChangesTrackAndThumbWithoutInvalidValues() {
        assertEquals(2f, VolumeBarStyle.trackDp(0f), .0001f)
        assertEquals(12f, VolumeBarStyle.trackDp(12f), .0001f)
        assertEquals(16f, VolumeBarStyle.trackDp(99f), .0001f)
        assertEquals(14.4f, VolumeBarStyle.thumbRadiusDp(12f), .0001f)
        assertEquals(19.2f, VolumeBarStyle.thumbRadiusDp(16f), .0001f)
    }

    @Test fun malformedSavedValuesUseSafeDefaults() {
        assertEquals(0f, VolumeBarStyle.transparency(Float.NaN), .0001f)
        assertEquals(0f, VolumeBarStyle.transparency(-100f), .0001f)
        assertEquals(0f, VolumeBarStyle.transparency(Float.POSITIVE_INFINITY), .0001f)
        assertEquals(5f, VolumeBarStyle.trackDp(Float.NaN), .0001f)
        assertEquals(5f, VolumeBarStyle.trackDp(Float.NEGATIVE_INFINITY), .0001f)
    }
}
