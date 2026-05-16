package dev.anilbeesetti.nextplayer.feature.player.service

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

sealed class CastMediaSource {
    abstract val subtitleFile: File?
    abstract val subtitleUrl: String?
    data class LocalFile(
        val file: File,
        override val subtitleFile: File? = null,
        override val subtitleUrl: String? = null
    ) : CastMediaSource()

    data class RemoteUrl(
        val url: String,
        override val subtitleFile: File? = null,
        override val subtitleUrl: String? = null
    ) : CastMediaSource()
}

suspend fun resolveMediaSource(
    uri: String,
    getVideoPath: suspend (String) -> String?
): CastMediaSource? {
    if (uri.startsWith("http://") || uri.startsWith("https://")) {
        val subtitleUrl = buildSubtitleUrisFromStream(Uri.parse(uri))
            .firstOrNull()
            ?.toString()
        return CastMediaSource.RemoteUrl(url = uri, subtitleUrl = subtitleUrl)
    }

    val path = getVideoPath(uri) ?: return null
    val videoFile = File(path)
    val subtitleFile = listOf("srt", "vtt", "ssa", "ass").firstNotNullOfOrNull { ext ->
        File(videoFile.parent, "${videoFile.nameWithoutExtension}.$ext").takeIf { it.exists() }
    }
    return CastMediaSource.LocalFile(file = videoFile, subtitleFile = subtitleFile)
}

private suspend fun buildSubtitleUrisFromStream(videoUri: Uri): List<Uri> = withContext(Dispatchers.IO) {
    val subtitleExtensions = listOf(".srt", ".vtt", ".ass", ".ssa", ".ttml", ".xml", ".dfxp")
    val baseName = Uri.encode(videoUri.lastPathSegment?.substringBeforeLast(".") ?: return@withContext emptyList())
    val parentPath = videoUri.toString().substringBeforeLast("/")

    subtitleExtensions.map { ext ->
        Uri.parse("$parentPath/$baseName$ext")
    }.map { uri ->
        async {
            if (isRemoteFileExists(uri)) uri else null
        }
    }.awaitAll().filterNotNull()
}

private fun isRemoteFileExists(uri: Uri): Boolean {
    if (!uri.scheme.orEmpty().startsWith("http")) return false
    return try {
        val connection = (URL(uri.toString()).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Range", "bytes=0-0")
            connectTimeout = 3000
            readTimeout = 3000
        }
        val responseCode = connection.responseCode
        connection.disconnect()
        responseCode in 200..299
    } catch (e: Exception) {
        false
    }
}
