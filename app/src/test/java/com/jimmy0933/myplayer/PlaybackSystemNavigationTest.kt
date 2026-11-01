package com.jimmy0933.myplayer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSystemNavigationTest {
    @Test fun defaultMaintainsImmersiveVideoAndPhotoViewer() {
        assertTrue(PlaybackSystemNavigation.DEFAULT_HIDE_WHILE_PLAYING)
        assertTrue(PlaybackSystemNavigation.hideNavigationBar(true, true, true))
        assertTrue(PlaybackSystemNavigation.hideNavigationBar(true, false, true))
        assertTrue(PlaybackSystemNavigation.hideStatusBar(true))
    }

    @Test fun optionShowsAndroidNavigationOnlyForVideo() {
        assertFalse(PlaybackSystemNavigation.hideNavigationBar(true, true, false))
        assertTrue(PlaybackSystemNavigation.hideNavigationBar(true, false, false))
        assertTrue(PlaybackSystemNavigation.hideStatusBar(true))
    }

    @Test fun returningToGalleryAlwaysRestoresBothSystemBars() {
        assertFalse(PlaybackSystemNavigation.hideNavigationBar(false, true, true))
        assertFalse(PlaybackSystemNavigation.hideNavigationBar(false, true, false))
        assertFalse(PlaybackSystemNavigation.hideNavigationBar(false, false, true))
        assertFalse(PlaybackSystemNavigation.hideStatusBar(false))
    }

    @Test fun videoAndPhotoSwipesRecalculateTheNavigationPolicy() {
        assertFalse(PlaybackSystemNavigation.hideNavigationBar(true, true, false))
        assertTrue(PlaybackSystemNavigation.hideNavigationBar(true, false, false))
        assertFalse(PlaybackSystemNavigation.hideNavigationBar(true, true, false))
    }
}
