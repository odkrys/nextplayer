package dev.anilbeesetti.nextplayer.core.domain.playlist

import dev.anilbeesetti.nextplayer.core.data.repository.MediaRepository
import dev.anilbeesetti.nextplayer.core.data.repository.PlaylistRepository
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.model.Playlist
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class GetPlaylistWithMediaUseCase @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val preferencesRepository: PreferencesRepository,
    private val mediaRepository: MediaRepository,
) {
    operator fun invoke(playlistId: Long): Flow<Playlist?> {
        return combine(
            playlistRepository.getPlaylistWithMediaFlow(playlistId),
            mediaRepository.getVideosFlow(),
            preferencesRepository.applicationPreferences
        ) { playlist, allVideos, preferences ->

            if (playlist == null) return@combine null

            if (!preferences.hideExcludedMediaInPlaylists || preferences.excludeFolders.isEmpty()) {
                return@combine playlist
            }

            val excludedFolders = preferences.excludeFolders.toSet()
            val videoMap = allVideos.associateBy { it.uriString }

            val indicesToKeep = playlist.mediaUris.mapIndexedNotNull { index, uriString ->
                val isRemote = uriString.startsWith("http")

                if (isRemote) {
                    index
                } else {
                    val video = videoMap[uriString]
                    val parentPath = video?.parentPath?.takeIf { it.isNotEmpty() } ?: return@mapIndexedNotNull index

                    if (parentPath in excludedFolders) {
                        null
                    } else {
                        index
                    }
                }
            }

            if (indicesToKeep.size != playlist.mediaUris.size) {
                playlist.copy(
                    mediaUris = indicesToKeep.map { playlist.mediaUris[it] },
                )
            } else {
                playlist
            }
        }
    }
}
