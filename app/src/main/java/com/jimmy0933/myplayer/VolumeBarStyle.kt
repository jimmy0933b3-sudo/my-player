package com.jimmy0933.myplayer

/**
 * Appearance of the right-edge video volume overlay. A value of zero
 * transparency preserves the current opaque icons and 60%-black backdrop.
 * Thickness changes only the track and thumb, not the 44dp touch target.
 */
internal object VolumeBarStyle {
    const val DEFAULT_TRANSPARENCY = 0f
    const val MAX_TRANSPARENCY = 0.85f
    const val DEFAULT_TRACK_DP = 5f
    const val MIN_TRACK_DP = 2f
    const val MAX_TRACK_DP = 16f

    fun transparency(value: Float): Float =
        if (value.isFinite()) value.coerceIn(0f, MAX_TRANSPARENCY)
        else DEFAULT_TRANSPARENCY

    fun trackDp(value: Float): Float =
        if (value.isFinite()) value.coerceIn(MIN_TRACK_DP, MAX_TRACK_DP)
        else DEFAULT_TRACK_DP

    fun alpha(value: Float): Float = 1f - transparency(value)

    fun thumbRadiusDp(trackWidthDp: Float): Float =
        maxOf(8f, trackDp(trackWidthDp) * 1.2f)
}
