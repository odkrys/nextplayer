package dev.anilbeesetti.nextplayer.feature.player.service

import android.content.Context
import android.net.Uri
import dev.anilbeesetti.nextplayer.core.common.extensions.getFilenameFromUri
import dev.anilbeesetti.nextplayer.core.common.extensions.subtitleCacheDir
import dev.anilbeesetti.nextplayer.feature.player.extensions.buildSubtitleUrisFromStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import java.net.URLDecoder

sealed class CastMediaSource {
    abstract val subtitleFile: File?
    abstract val subtitleUrl: String?

    data class LocalFile(
        val file: File,
        override val subtitleFile: File? = null,
        override val subtitleUrl: String? = null,
    ) : CastMediaSource()

    data class RemoteUrl(
        val url: String,
        override val subtitleFile: File? = null,
        override val subtitleUrl: String? = null,
        val authHeaders: Map<String, String> = emptyMap(),
    ) : CastMediaSource()
}

fun mimeTypeFromExt(ext: String) = when (ext.lowercase()) {
    "mp4" -> "video/mp4"
    "mkv" -> "video/x-matroska"
    "webm" -> "video/webm"
    "avi" -> "video/x-msvideo"
    "ts" -> "video/mp2t"
    "mp3" -> "audio/mpeg"
    "flac" -> "audio/flac"
    "aac" -> "audio/aac"
    else -> "application/octet-stream"
}

fun CastMediaSource.toMediaId(): String = when (this) {
    is CastMediaSource.LocalFile -> file.absolutePath
    is CastMediaSource.RemoteUrl -> url
}

fun CastMediaSource.toTitle(): String = when (this) {
    is CastMediaSource.LocalFile -> file.nameWithoutExtension
    is CastMediaSource.RemoteUrl -> url.substringAfterLast("/").substringBeforeLast(".")
}

fun CastMediaSource.toMimeType(): String {
    val ext = when (this) {
        is CastMediaSource.LocalFile -> file.extension.lowercase()
        is CastMediaSource.RemoteUrl -> {
            val lastSegment = url.substringBefore("?").substringAfterLast("/")
            if (lastSegment.contains(".")) lastSegment.substringAfterLast(".").lowercase() else ""
        }
    }
    return mimeTypeFromExt(ext)
}

fun CastMediaSource.toSubtitleUrl(ip: String, port: Int): String? = when {
    subtitleFile != null -> {
        "http://$ip:$port/subtitle/${Uri.encode(subtitleFile!!.name)}"
    }
    subtitleUrl != null -> {
        val ext = subtitleUrl!!.substringAfterLast(".", "srt").takeIf { it.length <= 4 } ?: "srt"
        "http://$ip:$port/subtitle.$ext"
    }
    else -> null
}

suspend fun resolveMediaSource(
    uri: String,
    authHeaders: Map<String, String> = emptyMap(),
    getVideoPath: suspend (String) -> String?,
    okHttpClient: OkHttpClient,
): CastMediaSource? {
    if (uri.startsWith("http://") || uri.startsWith("https://")) {
        val parsedUri = Uri.parse(uri)

        val subtitleUrl = extractSubtitleUrlFromFragment(parsedUri)
            ?: buildSubtitleUrisFromStream(parsedUri, okHttpClient).firstOrNull()?.toString()

        val cleanUrl = parsedUri.buildUpon().fragment(null).build().toString()

        return CastMediaSource.RemoteUrl(
            url = cleanUrl,
            subtitleUrl = subtitleUrl,
            authHeaders = authHeaders,
        )
    }

    val path = getVideoPath(uri) ?: return null
    val videoFile = File(path)
    val subtitleFile = listOf("srt", "vtt", "ssa", "ass").firstNotNullOfOrNull { ext ->
        File(videoFile.parent, "${videoFile.nameWithoutExtension}.$ext").takeIf { it.exists() }
    }
    return CastMediaSource.LocalFile(file = videoFile, subtitleFile = subtitleFile)
}

private fun extractSubtitleUrlFromFragment(uri: Uri): String? {
    val fragment = uri.fragment?.takeIf { it.isNotBlank() } ?: return null
    val priority = listOf("srt", "ssa", "ass", "vtt", "ttml", "xml", "dfxp")

    val subMap = fragment.split("&").mapNotNull { pair ->
        val kv = pair.split("=", limit = 2)
        if (kv.size == 2) kv[0].lowercase() to Uri.decode(kv[1]) else null
    }.toMap()

    return priority.firstNotNullOfOrNull { ext -> subMap[ext] }
}

suspend fun prepareDlnaSubtitle(context: Context, subtitleUri: Uri?, videoUriString: String?): File? = withContext(Dispatchers.IO) {
    if (subtitleUri == null) return@withContext null

    val scheme = subtitleUri.scheme?.lowercase()

    val decodedName = when (scheme) {
        "http", "https" -> subtitleUri.lastPathSegment ?: "subtitle.srt"
        "ftp" -> java.net.URL(subtitleUri.toString()).path.substringAfterLast('/')
        else -> context.getFilenameFromUri(subtitleUri)
    }

    val pureFileName = runCatching {
        if (decodedName.contains("%")) {
            URLDecoder.decode(decodedName, "UTF-8")
        } else {
            decodedName
        }
    }.getOrDefault(decodedName)

    if (pureFileName.isBlank()) return@withContext null

    val videoHash = videoUriString?.hashCode() ?: 0
    val subtitleHash = subtitleUri.toString().hashCode()

    val dlnaCacheFile = File(context.subtitleCacheDir, "cast_${videoHash}_${subtitleHash}_$pureFileName")
    val convertedCacheFile = File(context.subtitleCacheDir, "${subtitleHash}_$pureFileName")

    var sourceFile: File? = null
    var isConvertedUtf8 = false

    if (convertedCacheFile.exists()) {
        sourceFile = convertedCacheFile
        isConvertedUtf8 = true
    } else if (scheme == "file") {
        val originalFile = File(subtitleUri.path ?: "")
        if (originalFile.exists()) {
            sourceFile = originalFile
            isConvertedUtf8 = false
        }
    }

    return@withContext try {
        val utf8Bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())

        when {
            sourceFile != null -> {
                sourceFile.inputStream().use { inputStream ->
                    dlnaCacheFile.outputStream().use { outputStream ->
                        if (isConvertedUtf8) {
                            outputStream.write(utf8Bom)
                        }
                        inputStream.copyTo(outputStream)
                    }
                }
                dlnaCacheFile
            }

            scheme == "content" -> {
                context.contentResolver.openInputStream(subtitleUri)?.use { inputStream ->
                    dlnaCacheFile.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                dlnaCacheFile
            }

            else -> null
        }
    } catch (e: Exception) {
        null
    }
}
