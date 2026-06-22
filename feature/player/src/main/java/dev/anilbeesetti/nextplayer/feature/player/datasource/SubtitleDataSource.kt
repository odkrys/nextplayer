package dev.anilbeesetti.nextplayer.feature.player.datasource

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.FileDataSource
import androidx.media3.datasource.TransferListener
import dev.anilbeesetti.nextplayer.core.common.extensions.convertToUTF8
import java.nio.charset.Charset
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient

@UnstableApi
class SubtitleDataSource(
    private val context: Context,
    private val upstream: DataSource,
    private val okHttpClient: OkHttpClient
) : DataSource {
    private var fileDataSource: FileDataSource? = null
    private var isProxyUri = false

    override fun addTransferListener(transferListener: TransferListener) {
        upstream.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val uri = dataSpec.uri
        isProxyUri = uri.scheme?.startsWith("nextsub-") == true

        if (isProxyUri) {
            val originalScheme = uri.scheme!!.removePrefix("nextsub-")
            val charsetName = uri.getQueryParameter("nextsub_charset")
            val charset = charsetName?.let { runCatching { Charset.forName(it) }.getOrNull() }

            val originalUriBuilder = uri.buildUpon().scheme(originalScheme)
            originalUriBuilder.clearQuery()
            uri.queryParameterNames.forEach { key ->
                if (key != "nextsub_charset") {
                    originalUriBuilder.appendQueryParameter(key, uri.getQueryParameter(key))
                }
            }
            val originalUri = originalUriBuilder.build()

            val localUtf8Uri = runBlocking {
                context.convertToUTF8(originalUri, charset, okHttpClient)
            }

            return if (localUtf8Uri.scheme == ContentResolver.SCHEME_FILE) {
                val fds = FileDataSource()
                fileDataSource = fds
                fds.open(dataSpec.buildUpon().setUri(localUtf8Uri).build())
            } else {
                isProxyUri = false
                upstream.open(dataSpec.buildUpon().setUri(
                    if (localUtf8Uri == originalUri) originalUri else localUtf8Uri
                ).build())
            }
        } else {
            return upstream.open(dataSpec)
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        return if (isProxyUri) {
            fileDataSource?.read(buffer, offset, length) ?: C.RESULT_END_OF_INPUT
        } else {
            upstream.read(buffer, offset, length)
        }
    }

    override fun getUri(): Uri? = if (isProxyUri) fileDataSource?.uri else upstream.uri

    override fun close() {
        if (isProxyUri) {
            fileDataSource?.close()
            fileDataSource = null
        } else {
            upstream.close()
        }
    }
}
