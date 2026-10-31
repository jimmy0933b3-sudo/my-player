package com.jimmy0933.myplayer

import kotlin.math.max

/** Landscape modes. Portrait playback keeps its original fit behavior. */
internal enum class LandscapeVideoDisplay {
    SMART, FIT, FILL;

    companion object {
        fun fromSaved(value: String?): LandscapeVideoDisplay =
            entries.firstOrNull { it.name == value } ?: SMART
    }
}

/**
 * Use the actual video and viewport dimensions instead of enlarging a video
 * that already fills the screen. SMART limits the maximum crop to approximately
 * 15% of a dimension; users can select FIT (no crop) or FILL (no black bars).
 */
internal fun landscapeSmartScale(
    viewportWidth: Int,
    viewportHeight: Int,
    videoWidth: Int,
    videoHeight: Int,
    pixelWidthHeightRatio: Float = 1f,
    rotationDegrees: Int = 0,
    maxScale: Float = 1.18f,
): Float {
    if (viewportWidth <= 0 || viewportHeight <= 0 ||
        videoWidth <= 0 || videoHeight <= 0 ||
        !pixelWidthHeightRatio.isFinite() || pixelWidthHeightRatio <= 0f
    ) return 1f

    val rotated = rotationDegrees % 180 != 0
    val width = if (rotated) videoHeight.toFloat() else videoWidth * pixelWidthHeightRatio
    val height = if (rotated) videoWidth * pixelWidthHeightRatio else videoHeight.toFloat()
    val videoAspect = width / height
    val viewportAspect = viewportWidth.toFloat() / viewportHeight
    if (!videoAspect.isFinite() || videoAspect <= 0f) return 1f
    return max(videoAspect / viewportAspect, viewportAspect / videoAspect)
        .coerceIn(1f, maxScale.coerceAtLeast(1f))
}
