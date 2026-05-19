package dev.anilbeesetti.nextplayer.core.domain.webdav
 
import dev.anilbeesetti.nextplayer.core.data.remote.WebdavClient
import dev.anilbeesetti.nextplayer.core.data.remote.WebdavResult
import dev.anilbeesetti.nextplayer.core.model.WebdavFile
import dev.anilbeesetti.nextplayer.core.model.WebdavServer
import javax.inject.Inject
 
class ListWebdavFilesUseCase @Inject constructor(
    private val client: WebdavClient
) {
    suspend operator fun invoke(
        server: WebdavServer,
        path: String = "/"
    ): Result<List<WebdavFile>> = when (val result = client.listFiles(server, path)) {
        is WebdavResult.Success -> Result.success(
            result.data.sortedWith(compareByDescending<WebdavFile> { it.isDirectory }.thenBy { it.name })
        )
        is WebdavResult.Error -> Result.failure(
            Exception("Server Error (${result.code}): ${result.message}")
        )
        is WebdavResult.Exception -> Result.failure(result.throwable)
    }
}
