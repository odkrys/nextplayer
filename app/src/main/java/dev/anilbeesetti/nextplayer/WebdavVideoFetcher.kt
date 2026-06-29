package dev.anilbeesetti.nextplayer

import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.decode.ImageSource
import coil3.request.Options
import dev.anilbeesetti.nextplayer.core.model.WebdavVideoRequest
import okio.Buffer
import okio.FileSystem

class WebdavVideoFetcher(
    private val url: String,
    private val username: String,
    private val password: String,
    private val allowSelfSigned: Boolean,
    private val options: Options,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val buffer = Buffer()
        return SourceFetchResult(
            source = ImageSource(
                source = buffer,
                fileSystem = FileSystem.SYSTEM,
                metadata = WebdavMetadata(
                    url = url,
                    username = username,
                    password = password,
                    allowSelfSigned = allowSelfSigned,
                ),
            ),
            mimeType = "video/mp4",
            dataSource = DataSource.NETWORK,
        )
    }

    class Factory : Fetcher.Factory<WebdavVideoRequest> {
        override fun create(data: WebdavVideoRequest, options: Options, imageLoader: ImageLoader): Fetcher {
            return WebdavVideoFetcher(
                url = data.url,
                username = data.username,
                password = data.password,
                allowSelfSigned = data.allowSelfSigned,
                options = options,
            )
        }
    }
}
