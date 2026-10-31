package com.jimmy0933.myplayer

import org.junit.Assert.assertEquals
import org.junit.Test

class LandscapeVideoDisplayTest {
    @Test fun matchingAspectRatioNeedsNoExtraZoom() {
        assertEquals(1f, landscapeSmartScale(1920, 1080, 1920, 1080), .001f)
    }

    @Test fun typicalWidescreenPhoneReducesSideBarsWithoutExcessiveCropping() {
        // A 16:9 file viewed on a 19.5:9 landscape phone.
        assertEquals(1.18f, landscapeSmartScale(2340, 1080, 1920, 1080), .001f)
    }

    @Test fun cinemaAspectVideoOnPhoneGetsOnlyNeededZoom() {
        val actual = landscapeSmartScale(2340, 1080, 2390, 1000)
        assertEquals(2390f / (2340f / 1080f * 1000f), actual, .001f)
    }

    @Test fun tallVideosAndMalformedMetadataStaySafe() {
        assertEquals(1.18f, landscapeSmartScale(2340, 1080, 1080, 1920), .001f)
        assertEquals(1f, landscapeSmartScale(0, 1080, 1920, 1080), .001f)
        assertEquals(1f, landscapeSmartScale(2340, 1080, 0, 1080), .001f)
        assertEquals(1f, landscapeSmartScale(2340, 1080, 1920, 1080, Float.NaN), .001f)
    }

    @Test fun rotatedVideoUsesVisibleVideoDimensions() {
        assertEquals(1f, landscapeSmartScale(1920, 1080, 1080, 1920, rotationDegrees = 90), .001f)
    }

    @Test fun storedDisplayModeDefaultsSafely() {
        assertEquals(LandscapeVideoDisplay.SMART, LandscapeVideoDisplay.fromSaved(null))
        assertEquals(LandscapeVideoDisplay.SMART, LandscapeVideoDisplay.fromSaved("WRONG"))
        assertEquals(LandscapeVideoDisplay.FIT, LandscapeVideoDisplay.fromSaved("FIT"))
        assertEquals(LandscapeVideoDisplay.FILL, LandscapeVideoDisplay.fromSaved("FILL"))
    }
}
