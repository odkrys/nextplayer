package dev.anilbeesetti.nextplayer.core.data.repository

import dev.anilbeesetti.nextplayer.core.model.Playlist
import dev.anilbeesetti.nextplayer.core.model.PlaylistSortOption
import kotlinx.coroutines.flow.Flow

interface PlaylistRepository {
    fun getPlaylistsFlow(): Flow<List<Playlist>>
    fun getPlaylistWithMediaFlow(playlistId: Long): Flow<Playlist?>
    suspend fun getPlaylist(playlistId: Long): Playlist?
    suspend fun createPlaylist(name: String, position: Int): Long
    suspend fun renamePlaylist(playlistId: Long, name: String)
    suspend fun deletePlaylist(playlistId: Long)
    suspend fun addMediumToPlaylist(playlistId: Long, mediumUri: String, position: Int = 0, size: Long = 0L)
    suspend fun addMediaToPlaylist(playlistId: Long, mediumUris: List<String>, sizes: List<Long> = emptyList())
    suspend fun removeMediumFromPlaylist(playlistId: Long, mediumUri: String)
    suspend fun clearPlaylist(playlistId: Long)
    suspend fun updateMediumPosition(playlistId: Long, mediumUri: String, position: Int)
    suspend fun updateLastPlayedUri(playlistId: Long, uri: String)
    suspend fun updateSortOption(playlistId: Long, sortOption: PlaylistSortOption)
    suspend fun reorderPlaylists(ids: List<Long>)
    suspend fun removeMediaByPrefix(prefix: String)
    suspend fun removeDeletedMediaFromPlaylist(playlistId: Long, mediumUris: List<String>)
    suspend fun removeDeletedMediaFromAllPlaylists(mediumUris: List<String>)
}
