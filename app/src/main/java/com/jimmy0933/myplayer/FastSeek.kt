package com.jimmy0933.myplayer

/**
 * Swipe scrubbing is only a preview until finger-up.
 * Calling seekTo for each pointer move repeatedly flushes the video decoder.
 */
internal fun swipeSeekTarget(
    startPositionMs: Long,
    horizontalDisplacementPx: Float,
    durationMs: Long,
): Long {
    if (durationMs <= 0L) return 0L
    val offsetMs = (horizontalDisplacementPx * 65f).toLong()
    return (startPositionMs + offsetMs).coerceIn(0L, durationMs)
}

internal fun progressSeekTarget(fraction: Float, durationMs: Long): Long =
    if (durationMs <= 0L || !fraction.isFinite()) 0L
    else (fraction.coerceIn(0f, 1f) * durationMs).toLong().coerceIn(0L, durationMs)
