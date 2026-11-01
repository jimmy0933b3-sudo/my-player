package com.jimmy0933.myplayer

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

enum class MediaFileAction { COPY, MOVE, RENAME, SHARE }

data class FileCopyResult(
    val copiedSources: List<LocalMedia>,
    val failures: List<String>,
)

/**
 * Use the Android-granted document tree for destinations. Copy source bytes
 * completely and close/flush the stream before returning. MOVE may request
 * deletion of the original only after every selected copy succeeds.
 */
object MediaFileOperations {
    suspend fun copyIntoFolder(
        context: Context,
        items: List<LocalMedia>,
        folderUri: Uri,
    ): FileCopyResult = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val root = DocumentsContract.buildDocumentUriUsingTree(
            folderUri, DocumentsContract.getTreeDocumentId(folderUri),
        )
        val copied = mutableListOf<LocalMedia>()
        val errors = mutableListOf<String>()
        for (media in items.distinctBy { it.uri.toString() }) {
            var destination: Uri? = null
            try {
                val mime = resolver.getType(media.uri)
                    ?: if (media.isVideo) "video/mp4" else "image/jpeg"
                destination = DocumentsContract.createDocument(
                    resolver, root, mime, media.name,
                ) ?: throw IOException("目標目錄拒絕建立檔案")
                val output = resolver.openOutputStream(destination, "w")
                    ?: throw IOException("無法寫入目標檔案")
                val transferred = output.use { sink ->
                    val source = resolver.openInputStream(media.uri)
                        ?: throw IOException("無法讀取來源檔案")
                    source.use { input -> input.copyTo(sink) }
                        .also { sink.flush() }
                }
                if (media.bytes > 0 && transferred.toLong() != media.bytes) {
                    throw IOException("寫入長度與來源大小不一致")
                }
                // Verify the destination's reported size when its provider supports it.
                val outputSize = resolver.openFileDescriptor(destination, "r")
                    ?.use { it.statSize } ?: -1L
                if (outputSize >= 0 && transferred.toLong() != outputSize) {
                    throw IOException("目標檔案驗證失敗")
                }
                copied += media
            } catch (e: Exception) {
                destination?.let { runCatching { DocumentsContract.deleteDocument(resolver, it) } }
                errors += "${media.name}：${e.message ?: "無法複製"}"
            }
        }
        FileCopyResult(copied, errors)
    }
}

/** Conservative filename checks. MediaStore controls the final extension. */
internal fun isValidMediaFilename(name: String): Boolean {
    val value = name.trim()
    return value.isNotEmpty() && value != "." && value != ".." &&
        value.length <= 200 && value.none { it == '/' || it == '\\' || it == '\u0000' }
}
