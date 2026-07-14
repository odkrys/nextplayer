package dev.anilbeesetti.nextplayer.core.domain.webdav

import dev.anilbeesetti.nextplayer.core.data.repository.WebdavServerRepository
import javax.inject.Inject

class UpdateWebdavSortOptionUseCase @Inject constructor(
    private val repository: WebdavServerRepository
) {
    suspend operator fun invoke(id: Long, sortOption: String) {
        repository.updateSortOption(id, sortOption)
    }
}
