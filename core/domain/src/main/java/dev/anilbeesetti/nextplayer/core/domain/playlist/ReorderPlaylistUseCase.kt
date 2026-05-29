package dev.anilbeesetti.nextplayer.core.domain.playlist

import dev.anilbeesetti.nextplayer.core.data.repository.PlaylistRepository
import javax.inject.Inject

class ReorderPlaylistUseCase @Inject constructor(
    private val playlistRepository: PlaylistRepository,
) {
    suspend operator fun invoke(playlistId: Long, orderedUris: List<String>) {
        orderedUris.forEachIndexed { index, uri ->
            playlistRepository.updateMediumPosition(playlistId, uri, index)
        }
    }
}
