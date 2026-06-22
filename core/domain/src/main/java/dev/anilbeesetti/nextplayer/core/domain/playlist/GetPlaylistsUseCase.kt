package dev.anilbeesetti.nextplayer.core.domain.playlist

import dev.anilbeesetti.nextplayer.core.data.repository.MediaRepository
import dev.anilbeesetti.nextplayer.core.data.repository.PlaylistRepository
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.model.Playlist
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class GetPlaylistsUseCase @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val preferencesRepository: PreferencesRepository,
    private val mediaRepository: MediaRepository,
) {
    operator fun invoke(): Flow<List<Playlist>> {
        return combine(
            playlistRepository.getPlaylistsFlow(),
            mediaRepository.observeVideos(),
            preferencesRepository.applicationPreferences
        ) { playlists, allVideos, prefs ->

            val videoMap = allVideos.associateBy { it.uriString }
            val excludedFolders = prefs.excludeFolders.toSet()
            val shouldFilter = prefs.hideExcludedMediaInPlaylists && excludedFolders.isNotEmpty()

            playlists.map { playlist ->
                val filteredMedia = playlist.media.filter { mediaItem ->
                    if (mediaItem.uri.startsWith("http")) {
                        true
                    } else {
                        val video = videoMap[mediaItem.uri]
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

                if (filteredMedia.size != playlist.media.size) {
                    playlist.copy(media = filteredMedia)
                } else {
                    playlist
                }
            }
        }
    }
}
