package com.jimmy0933.myplayer

/**
 * One setting scales the progress track, thumb and touch height together.
 * Width is independent so users can make the slider shorter or full-width.
 */
internal object PlaybackProgressStyle {
    const val DEFAULT_SCALE = 1f
    const val DEFAULT_WIDTH = 0.96f

    fun scale(value: Float): Float =
        if (value.isFinite()) value.coerceIn(0.8f, 2.5f) else DEFAULT_SCALE

    fun width(value: Float): Float =
        if (value.isFinite()) value.coerceIn(0.6f, 1f) else DEFAULT_WIDTH

    fun trackDp(value: Float): Float = 3f * scale(value)

    fun thumbDp(value: Float): Float = 10f * scale(value)

    fun touchHeightDp(value: Float): Float =
        (30f * scale(value)).coerceAtLeast(30f)
}
