package dev.anilbeesetti.nextplayer.core.domain.playlist

import dev.anilbeesetti.nextplayer.core.data.repository.PlaylistRepository
import javax.inject.Inject

class RenamePlaylistUseCase @Inject constructor(
    private val playlistRepository: PlaylistRepository,
) {
    suspend operator fun invoke(playlistId: Long, name: String): Result<Unit> {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return Result.failure(IllegalArgumentException("Playlist name cannot be blank"))
        return runCatching { playlistRepository.renamePlaylist(playlistId, trimmed) }
    }
}
