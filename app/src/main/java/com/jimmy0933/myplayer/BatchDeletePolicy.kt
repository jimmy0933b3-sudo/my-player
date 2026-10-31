package com.jimmy0933.myplayer

/**
 * Android 16 (API 36) restricts MediaStore delete requests to at most 2,000
 * item URIs. Use this batch ceiling on all supported versions so the same
 * deletion plan is safe across OS upgrades.
 */
internal object BatchDeletePolicy {
    const val MAX_REQUEST_ITEMS = 2000

    fun <T> unique(items: List<T>, key: (T) -> String): List<T> =
        items.distinctBy(key)

    fun <T> nextChunk(items: List<T>, modernMediaStore: Boolean): List<T> =
        items.take(if (modernMediaStore) MAX_REQUEST_ITEMS else 1)

    fun needsAppConfirmation(apiLevel: Int, canManageMedia: Boolean): Boolean =
        apiLevel < 29 || (apiLevel >= 31 && canManageMedia)

    fun usesMediaStoreDeleteRequest(apiLevel: Int): Boolean = apiLevel >= 30
}
