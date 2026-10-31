package com.jimmy0933.myplayer

/**
 * LazyColumn and LazyVerticalGrid index every header and folder row as an item.
 * The viewer indexes only media, so map the selected media back to its actual
 * item position in either gallery layout.
 */
internal fun galleryLazyItemIndex(
    mediaIndex: Int,
    dayKeys: List<String>,
    folderRows: Int,
    groupedByDay: Boolean,
): Int {
    if (mediaIndex !in dayKeys.indices) return -1
    if (!groupedByDay) return folderRows + mediaIndex
    val headersBeforeOrAtMedia = dayKeys.subList(0, mediaIndex + 1).distinct().size
    return folderRows + mediaIndex + headersBeforeOrAtMedia
}
