package dev.anilbeesetti.nextplayer.core.data.remote

import dev.anilbeesetti.nextplayer.core.model.WebdavFile
import dev.anilbeesetti.nextplayer.core.model.WebdavServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

sealed class WebdavResult<out T> {
    data class Success<T>(val data: T) : WebdavResult<T>()
    data class Error(val code: Int, val message: String) : WebdavResult<Nothing>()
    data class Exception(val throwable: Throwable) : WebdavResult<Nothing>()
}

@Singleton
class WebdavClient @Inject constructor() {

    private val defaultClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val unsafeClient: OkHttpClient by lazy {
        defaultClient.newBuilder().setupUnsafeSsl().build()
    }

    suspend fun listFiles(
        server: WebdavServer,
        remotePath: String = "/"
    ): WebdavResult<List<WebdavFile>> = withContext(Dispatchers.IO) {
        runCatching {
            val callClient = if (server.allowSelfSigned) unsafeClient else defaultClient

            val url = buildUrl(server, remotePath)
            val body = PROPFIND_BODY.toRequestBody("application/xml; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url(url)
                .method("PROPFIND", body)
                .header("Authorization", Credentials.basic(server.username, server.password))
                .header("Depth", "1")
                .build()

            val response = callClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@runCatching WebdavResult.Error(response.code, response.message)
            }

            val xml = response.body?.string() ?: return@runCatching WebdavResult.Error(-1, "Empty body")

            val files = parsePropfindResponse(xml, url, server.baseUrl)
            WebdavResult.Success(files)
        }.getOrElse { WebdavResult.Exception(it) }
    }

    suspend fun testConnection(server: WebdavServer): WebdavResult<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val callClient = if (server.allowSelfSigned) unsafeClient else defaultClient

            val body = PROPFIND_BODY.toRequestBody("application/xml; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url(server.baseUrl)
                .method("PROPFIND", body)
                .header("Authorization", Credentials.basic(server.username, server.password))
                .header("Depth", "0")
                .build()

            val response = callClient.newCall(request).execute()
            if (!response.isSuccessful) {
                WebdavResult.Error(response.code, response.message)
            } else {
                WebdavResult.Success(Unit)
            }
        }.getOrElse { WebdavResult.Exception(it) }
    }

    private fun buildUrl(server: WebdavServer, path: String): String {
        val base = server.baseUrl.trimEnd('/')
        val normalizedPath = if (path.startsWith("/")) path else "/$path"
        return "$base$normalizedPath"
    }

    private fun parsePropfindResponse(xml: String, requestUrl: String, baseUrl: String): List<WebdavFile> {
        val files = mutableListOf<WebdavFile>()

        val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xml))

        val requestPathNorm = android.net.Uri.parse(requestUrl).path?.trimEnd('/') ?: ""
        val baseUriPath = android.net.Uri.parse(baseUrl).path?.trimEnd('/') ?: ""

        var href = ""
        var displayName = ""
        var isDirectory = false
        var size = 0L
        var contentType = ""
        var lastModified = ""
        var createdAt = ""
        var etag = ""
        var currentTag = ""
        var insideResponse = false

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    currentTag = parser.name ?: ""
                    if (currentTag == "response") {
                        insideResponse = true
                        href = ""; displayName = ""; isDirectory = false
                        size = 0L; contentType = ""; lastModified = ""
                        createdAt = ""; etag = ""
                    } else if (insideResponse && currentTag == "collection") {
                        isDirectory = true
                    }
                }

                XmlPullParser.TEXT -> {
                    val text = parser.text?.trim() ?: ""
                    if (insideResponse && text.isNotEmpty()) {
                        when (currentTag) {
                            "href"            -> href = text
                            "displayname"     -> displayName = text
                            "getcontentlength"-> size = text.toLongOrNull() ?: 0L
                            "getcontenttype"  -> contentType = text
                            "getlastmodified" -> lastModified = text
                            "creationdate"    -> createdAt = text
                            "getetag"         -> etag = text
                        }
                    }
                }

                XmlPullParser.END_TAG -> {
                    if (parser.name == "response" && insideResponse) {
                        insideResponse = false
                        val decodedHref = android.net.Uri.decode(href)

                        val hrefPathNorm = if (decodedHref.startsWith("http://") || decodedHref.startsWith("https://")) {
                            android.net.Uri.parse(decodedHref).path ?: decodedHref
                        } else {
                            decodedHref
                        }.trimEnd('/')

                        if (hrefPathNorm == requestPathNorm) {
                            currentTag = ""
                            continue
                        }

                        val name = displayName.ifEmpty {
                            decodedHref.trimEnd('/').substringAfterLast('/')
                        }

                        var relativePath = if (baseUriPath.isNotEmpty() && hrefPathNorm.startsWith(baseUriPath)) {
                            hrefPathNorm.substring(baseUriPath.length)
                        } else {
                            hrefPathNorm
                        }
                        if (!relativePath.startsWith("/")) {
                            relativePath = "/$relativePath"
                        }

                        if (name.isNotEmpty()) {
                            files.add(
                                WebdavFile(
                                    href = decodedHref,
                                    name = name,
                                    path = relativePath,
                                    isDirectory = isDirectory,
                                    size = size,
                                    contentType = contentType,
                                    lastModified = formatWebdavDate(lastModified),
                                    createdAt = createdAt,
                                    etag = etag
                                )
                            )
                        }
                        currentTag = ""
                    }
                }
            }
            eventType = parser.next()
        }

        return files
    }

    private fun formatWebdavDate(rawDate: String): String {
        if (rawDate.isBlank()) return ""
        return try {
            val parser = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US)
            val parsedDate = parser.parse(rawDate) ?: return rawDate

            val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            formatter.format(parsedDate)
        } catch (e: Exception) {
            rawDate
        }
    }

    companion object {
        private val PROPFIND_BODY = """
            <?xml version="1.0" encoding="utf-8"?>
            <D:propfind xmlns:D="DAV:">
                <D:prop>
                    <D:displayname/>
                    <D:getcontentlength/>
                    <D:getcontenttype/>
                    <D:getlastmodified/>
                    <D:creationdate/>
                    <D:getetag/>
                    <D:resourcetype/>
                </D:prop>
            </D:propfind>
        """.trimIndent()
    }
}
