package dev.anilbeesetti.nextplayer.core.domain.webdav

import dev.anilbeesetti.nextplayer.core.data.repository.WebdavServerRepository
import javax.inject.Inject

class ReorderWebdavServersUseCase @Inject constructor(
    private val repository: WebdavServerRepository,
) {
    suspend operator fun invoke(ids: List<Long>) {
        repository.reorderServers(ids)
    }
}
