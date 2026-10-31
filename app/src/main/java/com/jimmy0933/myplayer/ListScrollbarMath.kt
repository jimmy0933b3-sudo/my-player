package com.jimmy0933.myplayer

import kotlin.math.roundToInt

/**
 * Convert a LazyColumn position to a draggable scrollbar position and back.
 * Indices include folder rows and date headers, just like LazyListState.
 */
internal object ListScrollbarMath {
    fun thumbHeightPx(
        trackHeightPx: Float,
        totalItems: Int,
        visibleItems: Int,
        minimumThumbPx: Float,
    ): Float {
        if (trackHeightPx <= 0f || totalItems <= 0) return 0f
        val ratio = (visibleItems.toFloat() / totalItems).coerceIn(0f, 1f)
        return (trackHeightPx * ratio).coerceIn(
            minimumThumbPx.coerceAtMost(trackHeightPx), trackHeightPx,
        )
    }

    fun positionFraction(
        firstVisibleIndex: Int,
        firstVisibleOffsetPx: Int,
        averageItemPx: Float,
        totalItems: Int,
        visibleItems: Int,
        isAtEnd: Boolean,
    ): Float {
        val lastStart = (totalItems - visibleItems).coerceAtLeast(0)
        if (lastStart == 0) return 0f
        if (isAtEnd) return 1f
        val itemFraction = if (averageItemPx > 0f) firstVisibleOffsetPx / averageItemPx else 0f
        return ((firstVisibleIndex + itemFraction) / lastStart).coerceIn(0f, 1f)
    }

    fun indexForTrackPosition(
        fingerY: Float,
        fingerOffsetInsideThumb: Float,
        trackHeightPx: Float,
        thumbHeightPx: Float,
        totalItems: Int,
        visibleItems: Int,
    ): Int {
        val lastStart = (totalItems - visibleItems).coerceAtLeast(0)
        val travel = (trackHeightPx - thumbHeightPx).coerceAtLeast(0f)
        if (lastStart == 0 || travel <= 0f) return 0
        val thumbTop = (fingerY - fingerOffsetInsideThumb).coerceIn(0f, travel)
        return (thumbTop / travel * lastStart).roundToInt().coerceIn(0, lastStart)
    }
}
