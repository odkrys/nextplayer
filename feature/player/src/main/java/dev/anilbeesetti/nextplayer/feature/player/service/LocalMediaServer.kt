package dev.anilbeesetti.nextplayer.feature.player.service

import android.util.Log
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.FileInputStream

class LocalMediaServer(port: Int, private var file: File) : NanoHTTPD(port) {

    companion object {
        private const val TAG = "LocalMediaServer"
    }

    data class HttpRange(val start: Long, val end: Long, val length: Long)

    var subtitleFile: File? = null

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri

        if (uri == "/subtitle") {
            return serveSubtitle(session)
        }

        val method = session.method
        val rangeHeader = session.headers["range"]

        if (method == Method.OPTIONS) {
            return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, null, 0).apply {
                addHeader("Access-Control-Allow-Origin", "*")
                addHeader("Access-Control-Allow-Methods", "GET, OPTIONS")
                addHeader("Access-Control-Allow-Headers", "Range, Content-Type, Accept")
                addHeader("Access-Control-Max-Age", "86400")
            }
        }

        if (method == Method.HEAD) {
            val mimeType = getMimeType(file)
            return newFixedLengthResponse(Response.Status.OK, mimeType, null, file.length()).apply {
                addHeader("Content-Length", file.length().toString())
                addHeader("Accept-Ranges", "bytes")
                addHeader("Access-Control-Allow-Origin", "*")
                addHeader("Content-Disposition", "inline; filename=\"${file.name}\"")
                addHeader("transferMode.dlna.org", "Streaming")
                addHeader("contentFeatures.dlna.org", "DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000")
            }

        }

        return try {
            val mimeType = getMimeType(file)

            val response = if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                val range = parseRange(rangeHeader, file.length())

                if (range == null) {
                    Log.w(TAG, "Invalid range request: $rangeHeader")
                    return newFixedLengthResponse(
                        Response.Status.RANGE_NOT_SATISFIABLE,
                        MIME_PLAINTEXT,
                        "Invalid Range"
                    ).apply {
                        addHeader("Content-Range", "bytes */${file.length()}")
                    }
                }

                val fis = FileInputStream(file).apply { skip(range.start) }
                newFixedLengthResponse(Response.Status.PARTIAL_CONTENT, mimeType, fis, range.length).apply {
                    addHeader("Content-Range", "bytes ${range.start}-${range.end}/${file.length()}")
                }
            } else {
                newFixedLengthResponse(Response.Status.OK, mimeType, FileInputStream(file), file.length())
            }

            response.apply {
                addHeader("Accept-Ranges", "bytes")
                addHeader("Access-Control-Allow-Origin", "*")
                addHeader("Content-Disposition", "inline; filename=\"${file.name}\"")
                addHeader("transferMode.dlna.org", "Streaming")
                addHeader("contentFeatures.dlna.org", "DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Server error", e)
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Internal Server Error")
        }
    }

    private fun serveSubtitle(session: IHTTPSession): Response {
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
            else -> "application/octet-stream"
        }
    }

    fun updateFile(newFile: File, newSubtitle: File?) {
        this.file = newFile
        this.subtitleFile = newSubtitle
    }
}
