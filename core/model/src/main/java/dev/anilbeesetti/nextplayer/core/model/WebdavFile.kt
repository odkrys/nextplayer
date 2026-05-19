package dev.anilbeesetti.nextplayer.core.model

data class WebdavFile(
    val href: String,
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long = 0L,
    val contentType: String = "",
    val lastModified: String = "",
    val createdAt: String = "",
    val etag: String = ""
) {
    val extension: String
        get() = if (isDirectory) "" else name.substringAfterLast(".", "")

    val mimeType: String
        get() {
            if (isDirectory) return "vnd.android.document/directory"

            if (contentType.isNotBlank() && contentType != "application/octet-stream") {
                return contentType
            }

            return getMimeType(extension.lowercase())
        }

    val displaySize: String
        get() = when {
            isDirectory -> ""
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "${"%.1f".format(size / 1024.0)} KB"
            size < 1024 * 1024 * 1024 -> "${"%.1f".format(size / (1024.0 * 1024))} MB"
            else -> "${"%.1f".format(size / (1024.0 * 1024 * 1024))} GB"
        }
}

private fun getMimeType(url: String) = mimeTypeFromExt(url.substringBefore("?").substringAfterLast("."))
private fun mimeTypeFromExt(ext: String) = when (ext.lowercase()) {
    "mp4" -> "video/mp4"
    "mkv" -> "video/x-matroska"
    "webm" -> "video/webm"
    "avi" -> "video/x-msvideo"
    "ts"  -> "video/mp2t"
    "mp3" -> "audio/mpeg"
    "flac" -> "audio/flac"
    "aac"  -> "audio/aac"
    else  -> "application/octet-stream"
}
