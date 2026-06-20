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
            mediaRepository.observeVideos(),
            preferencesRepository.applicationPreferences
        ) { playlist, allVideos, prefs ->
            if (playlist == null) return@combine null

            val videoMap = allVideos.associateBy { it.uriString }
            val excludedFolders = prefs.excludeFolders.toSet()
            val shouldFilter = prefs.hideExcludedMediaInPlaylists && excludedFolders.isNotEmpty()

            val filteredUris = playlist.mediaUris.filter { uri ->
                if (uri.startsWith("http")) {
                    true
                } else {
                    val video = videoMap[uri]
                    if (video == null) {
                        false
                    } else if (!shouldFilter) {
                        true
                    } else {
                        val parentPath = video.parentPath
                        val isExcluded = excludedFolders.any { excludedFolder ->
                            parentPath == excludedFolder || parentPath.startsWith("$excludedFolder/")
                        }
                        !isExcluded
                    }
                }
            }

            if (filteredUris.size != playlist.mediaUris.size) {
                playlist.copy(mediaUris = filteredUris)
            } else {
                playlist
            }
        }
    }
}
