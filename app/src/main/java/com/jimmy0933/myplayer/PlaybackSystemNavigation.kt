package com.jimmy0933.myplayer

/**
 * Keep the viewer immersive by default. When the user asks to show Android's
 * navigation controls, apply it to video playback only. Photo browsing keeps
 * the existing fully immersive viewer behavior.
 */
internal object PlaybackSystemNavigation {
    const val DEFAULT_HIDE_WHILE_PLAYING = true

    fun hideNavigationBar(
        isViewerOpen: Boolean,
        isVideo: Boolean,
        hideWhilePlaying: Boolean,
    ): Boolean = isViewerOpen && (!isVideo || hideWhilePlaying)

    fun hideStatusBar(isViewerOpen: Boolean): Boolean = isViewerOpen
}
