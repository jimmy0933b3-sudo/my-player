package com.jimmy0933.myplayer

/**
 * What to do when a video reaches its natural end.
 *
 * STOP preserves the player's original behavior. NEXT skips images and
 * stops at the end of the list. REPEAT loops the current video.
 */
internal enum class VideoEndMode {
    NEXT,
    REPEAT,
    STOP;

    companion object {
        fun fromSaved(value: String?): VideoEndMode =
            entries.firstOrNull { it.name == value } ?: STOP
    }
}

/** NEXT must never select a photo or wrap from the last video to the first. */
internal fun nextVideoAtEnd(videoFlags: List<Boolean>, index: Int): Int? =
    adjacentVideoIndex(videoFlags, index, 1)
