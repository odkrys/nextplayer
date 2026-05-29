package dev.anilbeesetti.nextplayer.core.domain.playlist

import dev.anilbeesetti.nextplayer.core.data.repository.PlaylistRepository
import javax.inject.Inject

class DeletePlaylistUseCase @Inject constructor(
    private val playlistRepository: PlaylistRepository,
) {
    suspend operator fun invoke(playlistId: Long) {
        playlistRepository.deletePlaylist(playlistId)
    }
}
