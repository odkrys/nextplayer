package dev.anilbeesetti.nextplayer.core.domain.playlist

import dev.anilbeesetti.nextplayer.core.data.repository.PlaylistRepository
import javax.inject.Inject

class AddMediumToPlaylistUseCase @Inject constructor(
    private val playlistRepository: PlaylistRepository,
) {
    suspend operator fun invoke(
        playlistId: Long,
        mediumUri: String,
        position: Int = 0,
    ) {
        playlistRepository.addMediumToPlaylist(playlistId, mediumUri, position)
    }

    suspend operator fun invoke(
        playlistId: Long,
        mediumUris: List<String>,
    ) {
        playlistRepository.addMediaToPlaylist(playlistId, mediumUris)
    }
}
