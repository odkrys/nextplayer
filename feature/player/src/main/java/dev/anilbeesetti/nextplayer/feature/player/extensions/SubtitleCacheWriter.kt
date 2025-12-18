package dev.anilbeesetti.nextplayer.feature.player.extensions

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.media3.common.MimeTypes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

fun mimeToExt(mime: String): String = when (mime) {
    MimeTypes.APPLICATION_SUBRIP -> "srt"
    MimeTypes.TEXT_SSA -> "ass"
    MimeTypes.TEXT_VTT -> "vtt"
    MimeTypes.APPLICATION_TTML -> "ttml"
    else -> "srt"
}

suspend fun Context.writeShiftedSubtitleToCache(
    content: String,
    mimeType: String,
): Uri = withContext(Dispatchers.IO) {

    val context = this@writeShiftedSubtitleToCache
    val ext = mimeToExt(mimeType)

    val dir = context.getSubtitleCacheDir()
    val file = File(
        dir,
        "subtitle_shifted_${System.currentTimeMillis()}.$ext"
    )

    file.writeText(content)
    file.toUri()
}

fun Context.getSubtitleCacheDir(): File {
    val dir = File(cacheDir, "subtitles")
    if (!dir.exists()) dir.mkdirs()
    return dir
}
