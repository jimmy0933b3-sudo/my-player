package com.jimmy0933.myplayer

import org.junit.Assert.assertEquals
import org.junit.Test

class DecoderSettingsTest {
    @Test fun hardwarePreferenceRanksHardwareBeforeSoftware() {
        assertEquals(2, decoderPriority(VideoDecoderPreference.HARDWARE_FIRST, true, false))
        assertEquals(0, decoderPriority(VideoDecoderPreference.HARDWARE_FIRST, false, true))
    }

    @Test fun softwarePreferenceRanksSoftwareBeforeHardware() {
        assertEquals(2, decoderPriority(VideoDecoderPreference.SOFTWARE_FIRST, false, true))
        assertEquals(0, decoderPriority(VideoDecoderPreference.SOFTWARE_FIRST, true, false))
    }

    @Test fun systemPreferenceLeavesCodecOrderUnchanged() {
        assertEquals(0, decoderPriority(VideoDecoderPreference.SYSTEM, true, false))
        assertEquals(0, decoderPriority(VideoDecoderPreference.SYSTEM, false, true))
        assertEquals(0, decoderPriority(VideoDecoderPreference.SYSTEM, false, false))
    }

    @Test fun defaultsPrioritizeCompatibilityAndAutomaticRecovery() {
        val prefs = DecoderPreferences()
        assertEquals(VideoDecoderPreference.SYSTEM, prefs.video)
        assertEquals(false, prefs.softwareAudioFirst)
        assertEquals(true, prefs.decoderFallback)
        assertEquals(true, prefs.recoverOnPlaybackError)
    }
}
