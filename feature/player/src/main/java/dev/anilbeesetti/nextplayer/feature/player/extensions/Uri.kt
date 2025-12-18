package dev.anilbeesetti.nextplayer.feature.player.extensions

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import dev.anilbeesetti.nextplayer.core.common.extensions.convertToUTF8
import dev.anilbeesetti.nextplayer.core.common.extensions.getFilenameFromUri
import dev.anilbeesetti.nextplayer.feature.player.extensions.SubtitleTimeShifter.shiftAssSsa
import dev.anilbeesetti.nextplayer.feature.player.extensions.SubtitleTimeShifter.shiftSrt
import dev.anilbeesetti.nextplayer.feature.player.extensions.SubtitleTimeShifter.shiftTtml
import dev.anilbeesetti.nextplayer.feature.player.extensions.SubtitleTimeShifter.shiftVtt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLDecoder
import java.nio.charset.Charset

fun Uri.getSubtitleMime(context: Context): String {
    val extension = context.getFilenameFromUri(this)?.substringAfterLast('.', "")?.lowercase()
    return when (extension) {
        "ssa", "ass" -> MimeTypes.TEXT_SSA
        "srt" -> MimeTypes.APPLICATION_SUBRIP
        "vtt" -> MimeTypes.TEXT_VTT
        "ttml", "xml", "dfxp" -> MimeTypes.APPLICATION_TTML
        else -> MimeTypes.APPLICATION_SUBRIP
    }
}

val Uri.isSchemaContent: Boolean
    get() = ContentResolver.SCHEME_CONTENT.equals(scheme, ignoreCase = true)

suspend fun Context.uriToSubtitleConfiguration(
    uri: Uri,
    subtitleOffsetMs: Long,
    subtitleEncoding: String = "",
    isSelected: Boolean = true,
): MediaItem.SubtitleConfiguration {
    val charset = if (subtitleEncoding.isNotEmpty() && Charset.isSupported(subtitleEncoding)) {
        Charset.forName(subtitleEncoding)
    } else {
        null
    }
    val label = URLDecoder.decode(getFilenameFromUri(uri), "UTF-8")
    val mimeType = uri.getSubtitleMime(this)
    val utf8ConvertedUri = convertToUTF8(uri = uri, charset = charset)
    return MediaItem.SubtitleConfiguration.Builder(utf8ConvertedUri).apply {
        setId("${utf8ConvertedUri}_offset=$subtitleOffsetMs")
        setMimeType(mimeType)
        setLabel(label)
        if (isSelected) setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
    }.build()
}

@Suppress("DEPRECATION")
fun Bundle.getParcelableUriArray(key: String): Array<out Parcelable>? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableArray(key, Uri::class.java)
    } else {
        getParcelableArray(key)
    }
}

suspend fun Context.shiftSubtitleAndCache(
    uri: Uri,
    mimeType: String,
    offsetMs: Long
): Uri {
    if (offsetMs == 0L) return uri

    val text = withContext(Dispatchers.IO) {
        try {
            if (uri.scheme?.startsWith("http") == true) {
                java.net.URL(uri.toString())
                    .openConnection()
                    .apply {
                        connectTimeout = 5000
                        readTimeout = 5000
                    }
                    .getInputStream()
                    .bufferedReader()
                    .use { it.readText() }
            } else {
                contentResolver.openInputStream(uri)
                    ?.bufferedReader()
                    ?.use { it.readText() }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    } ?: return uri

    val shifted = when (mimeType) {
        MimeTypes.APPLICATION_SUBRIP -> shiftSrt(text, offsetMs)
        MimeTypes.TEXT_SSA -> shiftAssSsa(text, offsetMs)
        MimeTypes.TEXT_VTT -> shiftVtt(text, offsetMs)
        MimeTypes.APPLICATION_TTML -> shiftTtml(text, offsetMs)
        else -> text
    }

    return withContext(Dispatchers.IO) {
        writeShiftedSubtitleToCache(
            content = shifted,
            mimeType = mimeType
        )
    }
}