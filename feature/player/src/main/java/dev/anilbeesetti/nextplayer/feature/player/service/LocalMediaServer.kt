package dev.anilbeesetti.nextplayer.feature.player.service

import android.util.Log
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL

class LocalMediaServer(port: Int, private var source: CastMediaSource) : NanoHTTPD(port) {

    companion object {
        private const val TAG = "LocalMediaServer"
    }

    data class HttpRange(val start: Long, val end: Long, val length: Long)
    @Volatile private var cachedLength: Long = -1L

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
            is CastMediaSource.RemoteUrl -> serveRemoteUrl(s.url, method, rangeHeader)
        }
    }

    private fun serveLocalFile(file: File, method: Method, rangeHeader: String?): Response {
        if (method == Method.HEAD) {
            return newFixedLengthResponse(Response.Status.OK, getMimeType(file), null, file.length()).apply {
                addHeader("Content-Length", file.length().toString())
                addHeader("Accept-Ranges", "bytes")
                addHeader("Access-Control-Allow-Origin", "*")
                addHeader("transferMode.dlna.org", "Streaming")
                addHeader("contentFeatures.dlna.org", "DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000")
            }
        }

        return try {
            val mimeType = getMimeType(file)
            val response = if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                val range = parseRange(rangeHeader, file.length())
                    ?: return newFixedLengthResponse(
                        Response.Status.RANGE_NOT_SATISFIABLE, MIME_PLAINTEXT, "Invalid Range"
                    ).apply { addHeader("Content-Range", "bytes */${file.length()}") }

                val fis = FileInputStream(file).apply { skip(range.start) }
                newFixedLengthResponse(Response.Status.PARTIAL_CONTENT, mimeType, fis, range.length).apply {
                    addHeader("Content-Range", "bytes ${range.start}-${range.end}/${file.length()}")
                }
            } else {
                newFixedLengthResponse(Response.Status.OK, mimeType, FileInputStream(file), file.length())
            }

            response.applyDlnaHeaders()
        } catch (e: Exception) {
            Log.e(TAG, "Local file serve error", e)
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Internal Server Error")
        }
    }

    private fun serveRemoteUrl(url: String, method: Method, rangeHeader: String?): Response {
        if (method == Method.HEAD) {
            return try {
                if (cachedLength < 0) {
                    val conn = openRemoteConnection(url, null).also {
                        it.requestMethod = "HEAD"
                    }
                    cachedLength = conn.contentLengthLong
                    conn.disconnect()
                }
                newFixedLengthResponse(Response.Status.OK, "video/mp4", null, cachedLength)
                    .applyDlnaHeaders()
            } catch (e: Exception) {
                Log.e(TAG, "HEAD error", e)
                newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "HEAD Error")
            }
        }

        return try {
            val conn = openRemoteConnection(url, rangeHeader)
            val responseCode = conn.responseCode
            val mimeType = conn.contentType ?: "video/mp4"
            val contentLength = conn.contentLengthLong.also {
                if (it > 0) cachedLength = it
            }

            val status = if (responseCode == 206) Response.Status.PARTIAL_CONTENT
            else Response.Status.OK

            val response = if (contentLength > 0) {
                newFixedLengthResponse(status, mimeType, conn.inputStream, contentLength)
            } else {
                newChunkedResponse(status, mimeType, conn.inputStream)
            }

            conn.getHeaderField("Content-Range")?.let {
                response.addHeader("Content-Range", it)
            }

            response.applyDlnaHeaders()
        } catch (e: Exception) {
            Log.e(TAG, "Relay error", e)
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Relay Error")
        }
    }

    private fun openRemoteConnection(url: String, rangeHeader: String?): java.net.HttpURLConnection {
        return (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
            rangeHeader?.let { setRequestProperty("Range", it) }
            connectTimeout = 10000
            readTimeout = 60000
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
    }

    private fun serveSubtitle(session: IHTTPSession): Response {
        val remoteSubUrl = (source as? CastMediaSource.RemoteUrl)?.subtitleUrl
        if (remoteSubUrl != null) {
            return try {
                val conn = (URL(remoteSubUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10000
                    readTimeout = 30000
                }
                val ext = remoteSubUrl.substringBefore("?").substringAfterLast(".").lowercase()
                val mimeType = when (ext) {
                    "srt" -> "text/srt"
                    "vtt" -> "text/vtt"
                    "ass", "ssa" -> "text/x-ssa"
                    "ttml", "dfxp", "xml" -> "application/ttml+xml"
                    else -> conn.contentType?.substringBefore(";")?.trim() ?: "text/plain"
                }
                val contentLength = conn.contentLengthLong
                if (contentLength > 0) {
                    newFixedLengthResponse(Response.Status.OK, mimeType, conn.inputStream, contentLength)
                } else {
                    newChunkedResponse(Response.Status.OK, mimeType, conn.inputStream)
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
            Response.Status.OK,
            mimeType,
            FileInputStream(sub),
            sub.length()
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
        var start: Long
        var end: Long

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

    private fun getMimeType(file: File): String {
        return when (file.extension.lowercase()) {
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "avi" -> "video/x-msvideo"
            "ts" -> "video/mp2t"
            "mp3" -> "audio/mpeg"
            "flac" -> "audio/flac"
            "aac"  -> "audio/aac"
            else  -> "video/mp4"
        }
    }
}
