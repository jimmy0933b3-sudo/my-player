package com.jimmy0933.myplayer

/**
 * Lightweight directional feedback for changing photos and videos.
 * No SurfaceView transformations: animated video surfaces can flash during
 * decoder handoff, so only still-image content slides, while video shows a
 * brief direction cue. Both paths leave swipe-to-seek unaffected.
 */
internal object MediaSwitchAnimation {
    const val DURATION_MS = 180
    const val HINT_VISIBLE_MS = 350L
    const val PHOTO_ENTER_DISTANCE_DP = 14f

    fun direction(previous: Int, current: Int): Int = when {
        current > previous -> 1
        current < previous -> -1
        else -> 0
    }

    fun enteringOffsetDp(direction: Int): Float = when {
        direction > 0 -> PHOTO_ENTER_DISTANCE_DP
        direction < 0 -> -PHOTO_ENTER_DISTANCE_DP
        else -> 0f
    }

    fun indicatorText(direction: Int): String = when {
        direction > 0 -> "↑ 下一部"
        direction < 0 -> "↓ 上一部"
        else -> ""
    }
}
