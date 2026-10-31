package com.jimmy0933.myplayer

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class LocalMedia(
    val id: Long,
    val uri: Uri,
    val name: String,
    val isVideo: Boolean,
    val bytes: Long,
    val createdAtMs: Long,
    val durationMs: Long = 0L,
    val folderPath: String = "",
    val volumeName: String = "external_primary",
)

enum class MediaFilter { ALL, PHOTOS, VIDEOS }
enum class MediaSort { CREATED, SIZE }

// An IndexedValue preserves the media's position in the *sorted* collection.
// Group headings are not part of that collection or the viewer swipe sequence.
data class MediaDayGroup(
    val key: String,
    val label: String,
    val entries: List<IndexedValue<LocalMedia>>,
)

object MediaLibrary {
    private val sectionLabelFormat = DateTimeFormatter.ofPattern("yyyy/MM/dd EEEE", Locale.TAIWAN)

    fun createdDayKey(timestampMs: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        if (timestampMs <= 0L) return "unknown"
        return Instant.ofEpochMilli(timestampMs).atZone(zone).toLocalDate().toString()
    }

    fun groupByCreatedDay(sortedMedia: List<LocalMedia>): List<MediaDayGroup> {
        val zone = ZoneId.systemDefault()
        return sortedMedia.withIndex()
            .groupBy { createdDayKey(it.value.createdAtMs, zone) }
            .map { (key, entries) ->
                MediaDayGroup(
                    key = key,
                    label = if (key == "unknown") "未知日期"
                            else java.time.LocalDate.parse(key).format(sectionLabelFormat),
                    entries = entries,
                )
            }
    }

    fun requestedPermissions(): Array<String> = when {
        Build.VERSION.SDK_INT >= 34 -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
        )
        Build.VERSION.SDK_INT >= 33 -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
        )
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    fun hasAccess(context: Context): Boolean {
        fun permitted(permission: String) =
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        return when {
            Build.VERSION.SDK_INT >= 34 ->
                permitted(Manifest.permission.READ_MEDIA_IMAGES) ||
                permitted(Manifest.permission.READ_MEDIA_VIDEO) ||
                permitted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            Build.VERSION.SDK_INT >= 33 ->
                permitted(Manifest.permission.READ_MEDIA_IMAGES) ||
                permitted(Manifest.permission.READ_MEDIA_VIDEO)
            else -> permitted(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    suspend fun scan(context: Context): List<LocalMedia> = withContext(Dispatchers.IO) {
        val media = mutableListOf<LocalMedia>()
        // Read through the system MediaStore. Partial-library permission also scopes these queries.
        runCatching { media += readCollection(context, video = false) }
        runCatching { media += readCollection(context, video = true) }
        media
    }

    private fun readCollection(context: Context, video: Boolean): List<LocalMedia> {
        val base = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                   else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val dateTaken = if (video) MediaStore.Video.Media.DATE_TAKEN
                        else MediaStore.Images.Media.DATE_TAKEN
        val columns = buildList {
            add(MediaStore.MediaColumns._ID)
            add(MediaStore.MediaColumns.DISPLAY_NAME)
            add(MediaStore.MediaColumns.SIZE)
            add(MediaStore.MediaColumns.DATE_ADDED)
            add(dateTaken)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.MediaColumns.RELATIVE_PATH)
                add(MediaStore.MediaColumns.VOLUME_NAME)
            } else {
                add(MediaStore.MediaColumns.DATA)
            }
            if (video) add(MediaStore.Video.Media.DURATION)
        }.toTypedArray()
        val result = mutableListOf<LocalMedia>()
        context.contentResolver.query(
            base,
            columns,
            null,
            null,
            "${MediaStore.MediaColumns.DATE_ADDED} DESC",
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val addedColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            val takenColumn = cursor.getColumnIndex(dateTaken)
            val durationColumn = if (video) cursor.getColumnIndex(MediaStore.Video.Media.DURATION) else -1
            val folderColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH) else -1
            val volumeColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                cursor.getColumnIndex(MediaStore.MediaColumns.VOLUME_NAME) else -1
            val legacyPathColumn = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q)
                cursor.getColumnIndex(MediaStore.MediaColumns.DATA) else -1
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val added = if (cursor.isNull(addedColumn)) 0L else cursor.getLong(addedColumn) * 1000
                val taken = if (takenColumn < 0 || cursor.isNull(takenColumn)) 0L
                            else cursor.getLong(takenColumn)
                val size = if (cursor.isNull(sizeColumn)) 0L else cursor.getLong(sizeColumn)
                val duration = if (durationColumn < 0 || cursor.isNull(durationColumn)) 0L
                               else cursor.getLong(durationColumn)
                // Relative paths are exposed for authorized media without direct
                // filesystem access. Older Android versions use the DATA parent.
                val legacyPath = if (legacyPathColumn >= 0 &&
                    !cursor.isNull(legacyPathColumn)) cursor.getString(legacyPathColumn) else null
                val (legacyVolume, legacyFolder) = legacyLocation(legacyPath)
                val folderPath = if (folderColumn >= 0 && !cursor.isNull(folderColumn))
                    MediaFolders.normalizePath(cursor.getString(folderColumn)) else legacyFolder
                val volumeName = if (volumeColumn >= 0 && !cursor.isNull(volumeColumn))
                    cursor.getString(volumeColumn).orEmpty().ifBlank { "external_primary" }
                    else legacyVolume
                result += LocalMedia(
                    id = id,
                    uri = ContentUris.withAppendedId(base, id),
                    name = cursor.getString(nameColumn) ?: "未命名媒體",
                    isVideo = video,
                    bytes = size,
                    createdAtMs = if (taken > 0) taken else added,
                    durationMs = duration,
                    folderPath = folderPath,
                    volumeName = volumeName,
                )
            }
        }
        return result
    }

    private fun legacyLocation(path: String?): Pair<String, String> {
        val parent = path?.replace('\\\\', '/')?.substringBeforeLast('/', "")
            .orEmpty()
        if (parent.startsWith("/storage/emulated/")) {
            val relative = parent.removePrefix("/storage/emulated/")
                .substringAfter('/', "")
            return "external_primary" to MediaFolders.normalizePath(relative)
        }
        if (parent.startsWith("/storage/")) {
            val storagePath = parent.removePrefix("/storage/")
            val volume = storagePath.substringBefore('/')
            return volume to MediaFolders.normalizePath(storagePath.substringAfter('/', ""))
        }
        if (parent.startsWith("/sdcard/")) {
            return "external_primary" to MediaFolders.normalizePath(parent.removePrefix("/sdcard/"))
        }
        return "external_primary" to MediaFolders.normalizePath(parent)
    }

    fun sorted(
        media: List<LocalMedia>,
        filter: MediaFilter,
        sort: MediaSort,
        ascending: Boolean,
        query: String,
    ): List<LocalMedia> {
        val filtered = media.asSequence().filter {
            when (filter) {
                MediaFilter.ALL -> true
                MediaFilter.PHOTOS -> !it.isVideo
                MediaFilter.VIDEOS -> it.isVideo
            } && it.name.contains(query.trim(), ignoreCase = true)
        }.toList()
        val comparison = when (sort) {
            MediaSort.CREATED -> compareBy<LocalMedia> { it.createdAtMs }
            MediaSort.SIZE -> compareBy<LocalMedia> { it.bytes }
        }.thenBy { it.id }
        return filtered.sortedWith(if (ascending) comparison else comparison.reversed())
    }

    fun readableSize(bytes: Long): String = when {
        bytes >= 1024L * 1024 * 1024 -> "%.1f GB".format(bytes / (1024.0 * 1024 * 1024))
        bytes >= 1024L * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
        bytes >= 1024L -> "%.1f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }
}
