package dev.anilbeesetti.nextplayer.core.data.repository

import android.net.Uri
import dev.anilbeesetti.nextplayer.core.data.mappers.toPlaylist
import dev.anilbeesetti.nextplayer.core.database.dao.PlaylistDao
import dev.anilbeesetti.nextplayer.core.database.entities.PlaylistEntity
import dev.anilbeesetti.nextplayer.core.database.entities.PlaylistMediumCrossEntity
import dev.anilbeesetti.nextplayer.core.database.relations.PlaylistWithMedia
import dev.anilbeesetti.nextplayer.core.model.Playlist
import dev.anilbeesetti.nextplayer.core.model.PlaylistSortOption
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class LocalPlaylistRepository @Inject constructor(
    private val playlistDao: PlaylistDao,
) : PlaylistRepository {

    override fun getPlaylistsFlow(): Flow<List<Playlist>> {
        return playlistDao.getAllWithMedia().map { it.map(PlaylistWithMedia::toPlaylist) }
    }

    override fun getPlaylistWithMediaFlow(playlistId: Long): Flow<Playlist?> {
        return combine(
            playlistDao.getAsFlow(playlistId),
            playlistDao.getOrderedEntriesForPlaylist(playlistId),
        ) { playlistEntity, entries ->
            if (playlistEntity == null) return@combine null

            Playlist(
                id = playlistEntity.id,
                name = playlistEntity.name,
                createdAt = playlistEntity.createdAt,
                updatedAt = playlistEntity.updatedAt,
                lastPlayedUri = playlistEntity.lastPlayedUri,
                sortOption = runCatching {
                    PlaylistSortOption.valueOf(playlistEntity.sortOption)
                }.getOrDefault(PlaylistSortOption.ADDED_ASC),
                mediaUris = entries.map { if (it.isRemote) it.fullUrl else it.mediumUri }
            )
        }
    }

    override suspend fun getPlaylist(playlistId: Long): Playlist? {
        return playlistDao.get(playlistId)?.toPlaylist()
    }

    override suspend fun createPlaylist(name: String, position: Int): Long {
        return playlistDao.upsert(
            PlaylistEntity(
                name = name,
                position = position
            )
        )
    }

    override suspend fun renamePlaylist(playlistId: Long, name: String) {
        playlistDao.rename(
            id = playlistId,
            name = name,
            updatedAt = System.currentTimeMillis(),
        )
    }

    override suspend fun deletePlaylist(playlistId: Long) {
        playlistDao.delete(playlistId)
    }

    override suspend fun addMediumToPlaylist(playlistId: Long, mediumUri: String, position: Int) {
        playlistDao.addMedium(
            PlaylistMediumCrossEntity(
                playlistId = playlistId,
                mediumUri = mediumUri,
                position = position,
                addedAt = System.currentTimeMillis(),
                isRemote = mediumUri.startsWith("http"),
                displayName = if (mediumUri.startsWith("http")) Uri.parse(mediumUri).lastPathSegment ?: "" else "",
                fullUrl = if (mediumUri.startsWith("http")) mediumUri else ""
            ),
        )
    }

    override suspend fun addMediaToPlaylist(playlistId: Long, mediumUris: List<String>) {
        val currentMaxPosition = playlistDao.getMaxPosition(playlistId) ?: -1

        val crossRefs = mediumUris.mapIndexed { index, uri ->
            val isRemote = uri.startsWith("http")
            val displayName = if (isRemote) {
                Uri.parse(uri).lastPathSegment ?: "Unknown"
            } else {
                ""
            }

            PlaylistMediumCrossEntity(
                playlistId = playlistId,
                mediumUri = uri,
                position = currentMaxPosition + 1 + index,
                addedAt = System.currentTimeMillis(),
                isRemote = isRemote,
                displayName = displayName,
                fullUrl = if (isRemote) uri else ""
            )
        }
        playlistDao.addMedia(crossRefs)
    }

    override suspend fun removeMediumFromPlaylist(playlistId: Long, mediumUri: String) {
        playlistDao.removeMedium(
            playlistId = playlistId,
            mediumUri = mediumUri,
        )
    }

    override suspend fun clearPlaylist(playlistId: Long) {
        playlistDao.clearPlaylist(playlistId)
    }

    override suspend fun updateMediumPosition(playlistId: Long, mediumUri: String, position: Int) {
        playlistDao.updatePosition(
            playlistId = playlistId,
            mediumUri = mediumUri,
            position = position,
        )
    }

    override suspend fun updateLastPlayedUri(playlistId: Long, uri: String) {
        playlistDao.updateLastPlayedUri(playlistId, uri)
    }

    override suspend fun updateSortOption(playlistId: Long, sortOption: PlaylistSortOption) {
        playlistDao.updateSortOption(playlistId, sortOption.name)
    }

    override suspend fun reorderPlaylists(ids: List<Long>) {
        ids.forEachIndexed { index, id ->
            playlistDao.updatePosition(id, index)
        }
    }

    override suspend fun removeMediaByPrefix(prefix: String) {
        playlistDao.removeMediaByPrefix(prefix)
    }

    override suspend fun removeDeletedMediaFromPlaylist(playlistId: Long, mediumUris: List<String>) {
        playlistDao.removeDeletedMediaFromPlaylist(playlistId, mediumUris)
    }

    override suspend fun removeDeletedMediaFromAllPlaylists(mediumUris: List<String>) {
        playlistDao.removeDeletedMediaFromAllPlaylists(mediumUris)
    }
}
