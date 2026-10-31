package com.jimmy0933.myplayer

/**
 * A media-only directory tree built from accessible MediaStore images/videos.
 * Volume-aware folder keys remain stable across rescans.
 */
data class MediaFolder(
    val key: String,
    val parentKey: String?,
    val name: String,
    val volume: String,
    val relativePath: String,
    val mediaCount: Int,
    val preview: LocalMedia?,
    val available: Boolean = true,
)

object MediaFolders {
    fun normalizePath(raw: String): String =
        raw.replace('\\', '/')
            .split('/')
            .filter { it.isNotBlank() && it != "." && it != ".." }
            .joinToString("/")

    fun folderKey(volume: String, relativePath: String): String =
        "${volume.ifBlank { "external_primary" }}:${normalizePath(relativePath)}"

    fun folderKey(media: LocalMedia): String =
        folderKey(media.volumeName, media.folderPath)

    fun parentKey(key: String): String? {
        val volume = key.substringBefore(':')
        val path = normalizePath(key.substringAfter(':', ""))
        if (path.isBlank()) return null
        return folderKey(volume, path.substringBeforeLast('/', ""))
    }

    fun volumeLabel(volume: String): String = when (volume) {
        "external_primary", "primary", "external" -> "內部儲存空間"
        else -> "SD 卡 ($volume)"
    }

    fun folderLabel(key: String): String {
        val volume = key.substringBefore(':')
        val path = normalizePath(key.substringAfter(':', ""))
        return if (path.isBlank()) volumeLabel(volume)
        else "${volumeLabel(volume)} / ${path.replace("/", " / ")}"
    }

    // Preserve favorite entries if SD storage or photo permissions disappear.
    fun unavailableFavorite(key: String): MediaFolder {
        val volume = key.substringBefore(':')
        val path = normalizePath(key.substringAfter(':', ""))
        return MediaFolder(
            key = key,
            parentKey = parentKey(key),
            name = path.substringAfterLast('/').ifBlank { volumeLabel(volume) },
            volume = volume,
            relativePath = path,
            mediaCount = 0,
            preview = null,
            available = false,
        )
    }

    fun index(media: List<LocalMedia>): Map<String, MediaFolder> {
        data class MutableFolder(
            val key: String,
            val parent: String?,
            val name: String,
            val volume: String,
            val path: String,
            var count: Int = 0,
            var preview: LocalMedia? = null,
        )
        val folders = linkedMapOf<String, MutableFolder>()
        for (item in media) {
            val volume = item.volumeName.ifBlank { "external_primary" }
            val segments = normalizePath(item.folderPath).split('/').filter { it.isNotBlank() }
            var parent: String? = null
            val paths = listOf("") + segments.indices.map { n ->
                segments.take(n + 1).joinToString("/")
            }
            for (path in paths) {
                val key = folderKey(volume, path)
                val name = if (path.isEmpty()) volumeLabel(volume) else path.substringAfterLast('/')
                val folder = folders.getOrPut(key) {
                    MutableFolder(key, parent, name, volume, path)
                }
                folder.count++
                if (folder.preview == null || item.createdAtMs > folder.preview!!.createdAtMs) {
                    folder.preview = item
                }
                parent = key
            }
        }
        return folders.mapValues { (_, folder) ->
            MediaFolder(
                key = folder.key,
                parentKey = folder.parent,
                name = folder.name,
                volume = folder.volume,
                relativePath = folder.path,
                mediaCount = folder.count,
                preview = folder.preview,
            )
        }
    }

    fun children(folders: Map<String, MediaFolder>, parent: String?): List<MediaFolder> =
        folders.values.filter { it.parentKey == parent }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

    fun directMedia(media: List<LocalMedia>, key: String): List<LocalMedia> =
        media.filter { folderKey(it) == key }
}
