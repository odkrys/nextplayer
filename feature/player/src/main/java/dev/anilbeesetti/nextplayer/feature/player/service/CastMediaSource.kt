package dev.anilbeesetti.nextplayer.feature.player.service

import android.net.Uri
import dev.anilbeesetti.nextplayer.feature.player.extensions.buildSubtitleUrisFromStream
import okhttp3.OkHttpClient
import java.io.File

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
    subtitleFile != null -> "http://$ip:$port/subtitle"
    subtitleUrl != null -> "http://$ip:$port/subtitle"
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
