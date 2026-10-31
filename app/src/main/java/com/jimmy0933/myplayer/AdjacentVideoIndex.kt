package com.jimmy0933.myplayer

/**
 * Finds the next playable video in the gallery order, skipping photos.
 * Returns null at either end so the previous/next button can be disabled.
 */
internal fun adjacentVideoIndex(
    videoFlags: List<Boolean>,
    currentIndex: Int,
    direction: Int,
): Int? {
    if (currentIndex !in videoFlags.indices || direction !in setOf(-1, 1)) return null
    val indices = if (direction > 0) {
        currentIndex + 1..videoFlags.lastIndex
    } else {
        currentIndex - 1 downTo 0
    }
    return indices.firstOrNull { videoFlags[it] }
}
