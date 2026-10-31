package com.jimmy0933.myplayer

/**
 * The player's own volume (0..1), independent of Android's media stream volume.
 * Keep the last audible level while muted so one tap can restore it.
 */
internal data class PlaybackVolumeSettings(
    val level: Float = DEFAULT_LEVEL,
    val muted: Boolean = false,
) {
    val effectiveVolume: Float
        get() = if (muted) 0f else normalizeLevel(level)

    fun withSlider(value: Float): PlaybackVolumeSettings {
        if (!value.isFinite()) return this
        val next = value.coerceIn(0f, 1f)
        return if (next == 0f) copy(muted = true)
        else copy(level = next, muted = false)
    }

    fun toggleMute(): PlaybackVolumeSettings = copy(muted = !muted)

    companion object {
        const val DEFAULT_LEVEL = 1f

        fun normalizeLevel(value: Float): Float =
            if (value.isFinite()) value.coerceIn(0.01f, 1f) else DEFAULT_LEVEL
    }
}
