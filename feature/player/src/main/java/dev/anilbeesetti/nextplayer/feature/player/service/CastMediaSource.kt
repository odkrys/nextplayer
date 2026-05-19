package dev.anilbeesetti.nextplayer.feature.player.service

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
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
        val host = parsedUri.host ?: ""
        val auth = authHeaders[host]

        val subtitleUrl = extractSubtitleUrlFromFragment(parsedUri, auth)
            ?: buildSubtitleUrisFromStream(parsedUri, auth, okHttpClient).firstOrNull()?.toString()

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

private fun extractSubtitleUrlFromFragment(uri: Uri, auth: String?): String? {
    val fragment = uri.fragment?.takeIf { it.isNotBlank() } ?: return null
    val priority = listOf("srt", "ssa", "ass", "vtt", "ttml", "xml", "dfxp")

    val subMap = fragment.split("&").mapNotNull { pair ->
        val kv = pair.split("=", limit = 2)
        if (kv.size == 2) kv[0].lowercase() to Uri.decode(kv[1]) else null
    }.toMap()

    return priority.firstNotNullOfOrNull { ext -> subMap[ext] }
}

private suspend fun buildSubtitleUrisFromStream(
    videoUri: Uri,
    auth: String?,
    okHttpClient: OkHttpClient,
): List<Uri> = withContext(Dispatchers.IO) {
    val subtitleExtensions = listOf(".srt", ".vtt", ".ass", ".ssa", ".ttml", ".xml", ".dfxp")
    val lastSegment = videoUri.lastPathSegment ?: return@withContext emptyList()
    val baseName = Uri.encode(lastSegment.substringBeforeLast("."))
    val parentPath = videoUri.toString().substringBeforeLast("/")

    subtitleExtensions.map { ext ->
        Uri.parse("$parentPath/$baseName$ext")
    }.map { uri ->
        async {
            if (isRemoteFileExists(uri, auth, okHttpClient)) uri else null
        }
    }.awaitAll().filterNotNull()
}

private fun isRemoteFileExists(uri: Uri, auth: String?, okHttpClient: OkHttpClient): Boolean {
    if (!uri.scheme.orEmpty().startsWith("http")) return false
    return try {
        val request = Request.Builder().url(uri.toString()).apply {
            get()
            header("Range", "bytes=0-0")
            auth?.let { header("Authorization", it) }
        }.build()

        val response = okHttpClient.newCall(request).execute()
        val isSuccessful = response.isSuccessful
        response.close()
        isSuccessful
    } catch (e: Exception) {
        false
    }
}
