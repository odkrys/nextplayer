package dev.anilbeesetti.nextplayer.core.domain.playlist

import dev.anilbeesetti.nextplayer.core.data.repository.PlaylistRepository
import javax.inject.Inject

class RemoveMediumFromPlaylistUseCase @Inject constructor(
    private val playlistRepository: PlaylistRepository,
) {
    suspend operator fun invoke(playlistId: Long, mediumUri: String) {
        playlistRepository.removeMediumFromPlaylist(playlistId, mediumUri)
    }
}
