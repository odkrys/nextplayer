package dev.anilbeesetti.nextplayer.feature.player.extensions

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

suspend fun buildSubtitleUrisFromStream(
    videoUri: Uri,
    okHttpClient: OkHttpClient
): List<Uri> = withContext(Dispatchers.IO) {
    val subtitleExtensions = listOf(".srt", ".vtt", ".ass", ".ssa", ".ttml", ".xml", ".dfxp")
    val lastSegment = videoUri.lastPathSegment ?: return@withContext emptyList()
    val baseName = Uri.encode(lastSegment.substringBeforeLast("."))
    val parentPath = videoUri.toString().substringBeforeLast("/")

    subtitleExtensions.map { ext ->
        Uri.parse("$parentPath/$baseName$ext")
    }.map { uri ->
        async {
            if (isRemoteFileExists(uri, okHttpClient)) uri else null
        }
    }.awaitAll().filterNotNull()
}

fun isRemoteFileExists(
    uri: Uri,
    okHttpClient: OkHttpClient
): Boolean {
    if (!uri.scheme.orEmpty().startsWith("http")) return false
    return try {
        val request = Request.Builder().url(uri.toString()).apply {
            get()
            header("Range", "bytes=0-0")
        }.build()

        val response = okHttpClient.newCall(request).execute()
        val isSuccessful = response.isSuccessful
        response.close()
        isSuccessful
    } catch (e: Exception) {
        false
    }
}
