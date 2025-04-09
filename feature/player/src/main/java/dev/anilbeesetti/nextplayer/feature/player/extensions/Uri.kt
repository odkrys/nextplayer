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
    subtitleEncoding: String = "",
    isSelected: Boolean = true,
): MediaItem.SubtitleConfiguration {
    val charset = if (subtitleEncoding.isNotEmpty() && Charset.isSupported(subtitleEncoding)) {
        Charset.forName(subtitleEncoding)
    } else {
        null
    }
    val label = getFilenameFromUri(uri)
    val mimeType = uri.getSubtitleMime(this)
    val utf8ConvertedUri = convertToUTF8(uri = uri, charset = charset)
    return MediaItem.SubtitleConfiguration.Builder(utf8ConvertedUri).apply {
        setId(uri.toString())
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
