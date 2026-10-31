package com.jimmy0933.myplayer

/**
 * Match two short taps while content is magnified. Touches must be near each
 * other to avoid treating separate UI interactions as a double tap.
 */
internal fun shouldResetZoomOnDoubleTap(
    zoom: Float,
    previousTapMillis: Long,
    currentTapMillis: Long,
    previousX: Float,
    previousY: Float,
    currentX: Float,
    currentY: Float,
    maxDistancePx: Float,
): Boolean {
    if (zoom <= 1.01f || previousTapMillis < 0L) return false
    val elapsed = currentTapMillis - previousTapMillis
    if (elapsed !in 1L..300L || maxDistancePx <= 0f) return false
    val dx = currentX - previousX
    val dy = currentY - previousY
    return dx * dx + dy * dy <= maxDistancePx * maxDistancePx
}
