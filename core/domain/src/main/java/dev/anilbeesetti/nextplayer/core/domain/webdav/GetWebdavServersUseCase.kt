package dev.anilbeesetti.nextplayer.core.domain.webdav
 
import dev.anilbeesetti.nextplayer.core.data.repository.WebdavServerRepository
import dev.anilbeesetti.nextplayer.core.model.WebdavServer
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
 
class GetWebdavServersUseCase @Inject constructor(
    private val repository: WebdavServerRepository
) {
    operator fun invoke(): Flow<List<WebdavServer>> = repository.getAllServers()
}
