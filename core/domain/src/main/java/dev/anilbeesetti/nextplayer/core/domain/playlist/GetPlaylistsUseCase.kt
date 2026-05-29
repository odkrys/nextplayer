package dev.anilbeesetti.nextplayer.core.domain.playlist

import dev.anilbeesetti.nextplayer.core.data.repository.PlaylistRepository
import dev.anilbeesetti.nextplayer.core.model.Playlist
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class GetPlaylistsUseCase @Inject constructor(
    private val playlistRepository: PlaylistRepository,
) {
    operator fun invoke(): Flow<List<Playlist>> {
        return playlistRepository.getPlaylistsFlow()
    }
}
