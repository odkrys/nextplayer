package dev.anilbeesetti.nextplayer.core.domain.webdav
 
import dev.anilbeesetti.nextplayer.core.data.repository.WebdavServerRepository
import javax.inject.Inject
 
class DeleteWebdavServerUseCase @Inject constructor(
    private val repository: WebdavServerRepository
) {
    suspend operator fun invoke(serverId: Long): Result<Unit> = runCatching {
        repository.deleteServerById(serverId)
    }
}
