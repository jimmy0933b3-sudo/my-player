package com.jimmy0933.myplayer

/**
 * The previous app automatically saved CREATED even if the user did not
 * explicitly pick a sort option. Upgrade that legacy default to MODIFIED
 * once; retain SIZE and choices saved after the migration.
 */
internal object SortPreferenceMigration {
    const val VERSION = 2

    fun migrate(saved: String?, previousVersion: Int): MediaSort {
        val current = MediaSort.entries.firstOrNull { it.name == saved }
            ?: MediaSort.MODIFIED
        return if (previousVersion < VERSION && current == MediaSort.CREATED) {
            MediaSort.MODIFIED
        } else current
    }
}
