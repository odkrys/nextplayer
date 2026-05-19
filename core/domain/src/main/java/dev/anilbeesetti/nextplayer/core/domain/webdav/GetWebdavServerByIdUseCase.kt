package dev.anilbeesetti.nextplayer.core.domain.webdav

import dev.anilbeesetti.nextplayer.core.data.repository.WebdavServerRepository
import dev.anilbeesetti.nextplayer.core.model.WebdavServer
import javax.inject.Inject

class GetWebdavServerByIdUseCase @Inject constructor(
    private val repository: WebdavServerRepository
) {
    suspend operator fun invoke(id: Long): Result<WebdavServer> = runCatching {
        repository.getServerById(id) ?: throw Exception("Server not found")
    }
}