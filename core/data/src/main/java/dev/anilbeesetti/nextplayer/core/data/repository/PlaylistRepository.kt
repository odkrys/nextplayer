package dev.anilbeesetti.nextplayer.core.data.repository

import dev.anilbeesetti.nextplayer.core.model.Playlist
import dev.anilbeesetti.nextplayer.core.model.PlaylistSortOption
import kotlinx.coroutines.flow.Flow

interface PlaylistRepository {
    fun getPlaylistsFlow(): Flow<List<Playlist>>
    fun getPlaylistWithMediaFlow(playlistId: Long): Flow<Playlist?>
    suspend fun getPlaylist(playlistId: Long): Playlist?
    suspend fun createPlaylist(name: String): Long
    suspend fun renamePlaylist(playlistId: Long, name: String)
    suspend fun deletePlaylist(playlistId: Long)
    suspend fun addMediumToPlaylist(playlistId: Long, mediumUri: String, position: Int = 0)
    suspend fun addMediaToPlaylist(playlistId: Long, mediumUris: List<String>)
    suspend fun removeMediumFromPlaylist(playlistId: Long, mediumUri: String)
    suspend fun clearPlaylist(playlistId: Long)
    suspend fun updateMediumPosition(playlistId: Long, mediumUri: String, position: Int)
    suspend fun updateLastPlayedUri(playlistId: Long, uri: String)
    suspend fun updateSortOption(playlistId: Long, sortOption: PlaylistSortOption)
    suspend fun reorderPlaylists(ids: List<Long>)


}
