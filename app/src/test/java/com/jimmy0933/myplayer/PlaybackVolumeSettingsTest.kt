package com.jimmy0933.myplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackVolumeSettingsTest {
    @Test fun muteAndUnmuteRestoreLastAudibleVolume() {
        val initial = PlaybackVolumeSettings(level = 0.65f)
        val silent = initial.toggleMute()
        assertTrue(silent.muted)
        assertEquals(0f, silent.effectiveVolume, 0.0001f)
        val restored = silent.toggleMute()
        assertFalse(restored.muted)
        assertEquals(0.65f, restored.effectiveVolume, 0.0001f)
    }

    @Test fun draggingSliderUnmutesAndRemembersNewLevel() {
        val muted = PlaybackVolumeSettings(level = 0.75f, muted = true)
        val changed = muted.withSlider(0.3f)
        assertFalse(changed.muted)
        assertEquals(0.3f, changed.effectiveVolume, 0.0001f)
        assertEquals(0.3f, changed.toggleMute().toggleMute().effectiveVolume, 0.0001f)
    }

    @Test fun draggingToZeroMutesWithoutDiscardingLastAudibleLevel() {
        val silent = PlaybackVolumeSettings(level = 0.8f).withSlider(0f)
        assertTrue(silent.muted)
        assertEquals(0f, silent.effectiveVolume, 0.0001f)
        assertEquals(0.8f, silent.toggleMute().effectiveVolume, 0.0001f)
    }

    @Test fun volumeIsClampedAndBadValuesAreIgnored() {
        assertEquals(1f, PlaybackVolumeSettings.normalizeLevel(Float.NaN), 0.0001f)
        assertEquals(1f, PlaybackVolumeSettings().withSlider(8f).effectiveVolume, 0.0001f)
        assertEquals(0.01f, PlaybackVolumeSettings.normalizeLevel(-2f), 0.0001f)
        assertEquals(0.5f, PlaybackVolumeSettings(level = 0.5f).withSlider(Float.NaN).effectiveVolume, 0.0001f)
    }
}
