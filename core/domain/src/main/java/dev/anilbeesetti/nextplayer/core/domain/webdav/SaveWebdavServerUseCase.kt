package dev.anilbeesetti.nextplayer.core.domain.webdav
 
import dev.anilbeesetti.nextplayer.core.data.repository.WebdavServerRepository
import dev.anilbeesetti.nextplayer.core.model.WebdavServer
import javax.inject.Inject
 
class SaveWebdavServerUseCase @Inject constructor(
    private val repository: WebdavServerRepository
) {
    suspend operator fun invoke(server: WebdavServer): Result<Long> = runCatching {
        require(server.name.isNotBlank()) { "Server name is required." }
        require(server.host.isNotBlank()) { "Host address is required." }
        require(server.port in 1..65535) { "Invalid port number. It must be between 1 and 65535." }
 
        if (server.id == 0L) {
            repository.saveServer(server)
        } else {
            repository.updateServer(server)
            server.id
        }
    }
}
