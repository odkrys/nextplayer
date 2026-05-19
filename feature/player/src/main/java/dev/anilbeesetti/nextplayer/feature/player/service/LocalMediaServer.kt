package dev.anilbeesetti.nextplayer.feature.player.service

import android.util.Log
import fi.iki.elonen.NanoHTTPD
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL

class LocalMediaServer(
    port: Int,
    private var source: CastMediaSource,
    private val okHttpClient: OkHttpClient
) : NanoHTTPD(port) {

    companion object {
        private const val TAG = "LocalMediaServer"
    }

    data class HttpRange(val start: Long, val end: Long, val length: Long)

    @Volatile private var cachedLength: Long = -1L
    @Volatile private var cachedMimeType: String = "video/mp4"

    private val subtitleFile get() = source.subtitleFile

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        if (uri == "/subtitle") return serveSubtitle(session)

        val method = session.method
        val rangeHeader = session.headers["range"]

        if (method == Method.OPTIONS) {
            return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, null, 0).apply {
                addHeader("Access-Control-Allow-Origin", "*")
                addHeader("Access-Control-Allow-Methods", "GET, HEAD, OPTIONS")
                addHeader("Access-Control-Allow-Headers", "Range, Content-Type, Accept")
                addHeader("Access-Control-Max-Age", "86400")
            }
        }

        return when (val s = source) {
            is CastMediaSource.LocalFile -> serveLocalFile(s.file, method, rangeHeader)
            is CastMediaSource.RemoteUrl -> serveRemoteUrl(s.url, s.authHeaders, method, rangeHeader)
        }
    }

    private fun serveLocalFile(file: File, method: Method, rangeHeader: String?): Response {
        if (method == Method.HEAD) {
            return newFixedLengthResponse(
                Response.Status.OK, getMimeType(file), null, file.length()
            ).apply {
                addHeader("Content-Length", file.length().toString())
            }.applyDlnaHeaders()
        }

        return try {
            val mimeType = getMimeType(file)
            val response = if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                val range = parseRange(rangeHeader, file.length())
                    ?: return newFixedLengthResponse(
                        Response.Status.RANGE_NOT_SATISFIABLE, MIME_PLAINTEXT, "Invalid Range"
                    ).apply { addHeader("Content-Range", "bytes */${file.length()}") }

                val fis = FileInputStream(file).apply { skip(range.start) }
                newFixedLengthResponse(
                    Response.Status.PARTIAL_CONTENT, mimeType, fis, range.length
                ).apply {
                    addHeader("Content-Range", "bytes ${range.start}-${range.end}/${file.length()}")
                }
            } else {
                newFixedLengthResponse(
                    Response.Status.OK, mimeType, FileInputStream(file), file.length()
                )
            }
            response.applyDlnaHeaders()
        } catch (e: Exception) {
            Log.e(TAG, "Local file serve error", e)
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Internal Server Error"
            )
        }
    }

    private fun serveRemoteUrl(
        url: String,
        authHeaders: Map<String, String>,
        method: Method,
        rangeHeader: String?,
    ): Response {
        if (method == Method.HEAD) {
            return try {
                if (cachedLength < 0) {
                    val request = buildOkHttpRequest(url, "bytes=0-0", authHeaders, isHead = false)
                    val response = okHttpClient.newCall(request).execute()

                    cachedMimeType = response.header("Content-Type")
                        ?.takeIf { it != "application/octet-stream" }
                        ?: getMimeType(url)

                    cachedLength = when (response.code) {
                        206 -> response.header("Content-Range")
                            ?.substringAfterLast("/")
                            ?.toLongOrNull() ?: -1L
                        else -> response.body?.contentLength() ?: -1L
                    }
                    response.close()
                }

                val dummyStream = java.io.ByteArrayInputStream(ByteArray(0))
                val response = newFixedLengthResponse(
                    Response.Status.OK, cachedMimeType, dummyStream, cachedLength
                )
                if (cachedLength > 0) {
                    response.addHeader("Content-Length", cachedLength.toString())
                }
                response.applyDlnaHeaders()
            } catch (e: Exception) {
                Log.e(TAG, "HEAD error", e)
                newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "HEAD Error")
            }
        }

        return try {
            val request = buildOkHttpRequest(url, rangeHeader, authHeaders, isHead = false)
            val okResponse = okHttpClient.newCall(request).execute()

            val responseCode = okResponse.code
            val mimeType = okResponse.header("Content-Type")
                ?.takeIf { it != "application/octet-stream" }
                ?: cachedMimeType

            val contentLength = okResponse.body?.contentLength() ?: -1L
            val finalLength = if (contentLength > 0) {
                cachedLength = contentLength
                contentLength
            } else cachedLength

            val contentRange = okResponse.header("Content-Range")
            val status = if (rangeHeader != null && responseCode == 206)
                Response.Status.PARTIAL_CONTENT else Response.Status.OK

            val inputStream = okResponse.toManagedStream()
                ?: return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Empty Body")

            val response = if (finalLength > 0) {
                newFixedLengthResponse(status, mimeType, inputStream, finalLength)
            } else {
                newChunkedResponse(status, mimeType, inputStream)
            }

            if (rangeHeader != null) {
                contentRange?.let { response.addHeader("Content-Range", it) }
            }

            response.applyDlnaHeaders()
        } catch (e: Exception) {
            Log.e(TAG, "Relay error", e)
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Relay Error")
        }
    }

    private fun Response.applyDlnaHeaders() = apply {
        addHeader("Accept-Ranges", "bytes")
        addHeader("Access-Control-Allow-Origin", "*")
        addHeader("transferMode.dlna.org", "Streaming")
        addHeader("contentFeatures.dlna.org", "DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000")
    }

    fun updateSource(newSource: CastMediaSource) {
        this.source = newSource
        this.cachedLength = -1L
        this.cachedMimeType = "video/mp4"
    }

    private fun buildOkHttpRequest(
        url: String,
        rangeHeader: String?,
        authHeaders: Map<String, String>,
        isHead: Boolean
    ): Request {
        val host = try { java.net.URL(url).host } catch (_: Exception) { "" }
        val auth = authHeaders[host]

        return Request.Builder().url(url).apply {
            if (isHead) head() else get()
            rangeHeader?.let { header("Range", it) }
            auth?.let { header("Authorization", it) }

            header("Accept-Encoding", "identity")
            header("Connection", "keep-alive")
        }.build()
    }

    private fun serveSubtitle(session: IHTTPSession): Response {
        val remoteSource = source as? CastMediaSource.RemoteUrl
        val remoteSubUrl = remoteSource?.subtitleUrl

        if (remoteSubUrl != null) {
            return try {
                val request = buildOkHttpRequest(remoteSubUrl, null, remoteSource.authHeaders, isHead = false)
                val okResponse = okHttpClient.newCall(request).execute()

                val ext = remoteSubUrl.substringBefore("?").substringAfterLast(".").lowercase()
                val mimeType = when (ext) {
                    "srt" -> "text/srt"
                    "vtt" -> "text/vtt"
                    "ass", "ssa" -> "text/x-ssa"
                    "ttml", "dfxp", "xml" -> "application/ttml+xml"
                    else -> okResponse.header("Content-Type")?.substringBefore(";")?.trim() ?: "text/plain"
                }

                val contentLength = okResponse.body?.contentLength() ?: -1L
                val inputStream = okResponse.toManagedStream()
                    ?: return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Empty Body")

                if (contentLength > 0) {
                    newFixedLengthResponse(Response.Status.OK, mimeType, inputStream, contentLength)
                } else {
                    newChunkedResponse(Response.Status.OK, mimeType, inputStream)
                }.apply {
                    addHeader("Accept-Ranges", "bytes")
                    addHeader("Access-Control-Allow-Origin", "*")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Remote subtitle relay error", e)
                newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Subtitle Error")
            }
        }

        val sub = subtitleFile
        if (sub == null || !sub.exists()) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Subtitle not found")
        }

        val mimeType = when (sub.extension.lowercase()) {
            "srt" -> "text/srt"
            "vtt" -> "text/vtt"
            "ass", "ssa" -> "text/x-ssa"
            else -> "text/plain"
        }

        return newFixedLengthResponse(
            Response.Status.OK, mimeType, FileInputStream(sub), sub.length()
        ).apply {
            addHeader("Accept-Ranges", "bytes")
            addHeader("Access-Control-Allow-Origin", "*")
        }
    }

    private fun parseRange(rangeHeader: String, fileLength: Long): HttpRange? {
        val rangeStr = rangeHeader.removePrefix("bytes=").trim()
        val parts = rangeStr.split("-")
        if (parts.size != 2) return null

        val startStr = parts[0]
        val endStr = parts[1]
        val start: Long
        val end: Long

        if (startStr.isEmpty()) {
            val suffixLength = endStr.toLongOrNull() ?: return null
            if (suffixLength == 0L) return null
            start = (fileLength - suffixLength).coerceAtLeast(0L)
            end = fileLength - 1
        } else {
            start = startStr.toLongOrNull() ?: return null
            if (start >= fileLength) return null
            val parsedEnd = endStr.takeIf { it.isNotEmpty() }?.toLongOrNull()
            end = parsedEnd?.coerceAtMost(fileLength - 1) ?: (fileLength - 1)
        }

        if (start > end) return null
        return HttpRange(start, end, end - start + 1)
    }

    private fun okhttp3.Response.toManagedStream(): java.io.InputStream? {
        val raw = body?.byteStream() ?: run { close(); return null }
        return object : java.io.InputStream() {
            override fun read() = raw.read()
            override fun read(b: ByteArray, off: Int, len: Int) = raw.read(b, off, len)
            override fun close() { raw.close(); this@toManagedStream.close() }
        }
    }

    private fun getMimeType(file: File) = mimeTypeFromExt(file.extension)
    private fun getMimeType(url: String) =
        mimeTypeFromExt(url.substringBefore("?").substringAfterLast("."))
}
