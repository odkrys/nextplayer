package dev.anilbeesetti.nextplayer.core.domain.webdav
 
import dev.anilbeesetti.nextplayer.core.data.remote.WebdavClient
import dev.anilbeesetti.nextplayer.core.data.remote.WebdavResult
import dev.anilbeesetti.nextplayer.core.model.WebdavServer
import javax.inject.Inject
 
class TestWebdavConnectionUseCase @Inject constructor(
    private val client: WebdavClient
) {
    suspend operator fun invoke(server: WebdavServer): Result<Unit> =
        when (val result = client.testConnection(server)) {
            is WebdavResult.Success -> Result.success(Unit)
            is WebdavResult.Error -> Result.failure(
                Exception("Connection failed (${result.code}): ${result.message}")
            )
            is WebdavResult.Exception -> Result.failure(result.throwable)
        }
}
