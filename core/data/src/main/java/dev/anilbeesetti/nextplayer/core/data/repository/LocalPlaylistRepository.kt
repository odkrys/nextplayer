package dev.anilbeesetti.nextplayer.core.data.repository

import dev.anilbeesetti.nextplayer.core.data.mappers.toPlaylist
import dev.anilbeesetti.nextplayer.core.database.dao.PlaylistDao
import dev.anilbeesetti.nextplayer.core.database.entities.PlaylistEntity
import dev.anilbeesetti.nextplayer.core.database.entities.PlaylistMediumCrossEntity
import dev.anilbeesetti.nextplayer.core.database.relations.PlaylistWithMedia
import dev.anilbeesetti.nextplayer.core.model.Playlist
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
            playlistDao.getOrderedMediaUrisForPlaylist(playlistId)
        ) { playlistEntity, uris ->
            if (playlistEntity == null) return@combine null

            Playlist(
                id = playlistEntity.id,
                name = playlistEntity.name,
                createdAt = playlistEntity.createdAt,
                updatedAt = playlistEntity.updatedAt,
                lastPlayedUri = playlistEntity.lastPlayedUri,
                mediaUris = uris
            )
        }
    }

    override suspend fun getPlaylist(playlistId: Long): Playlist? {
        return playlistDao.get(playlistId)?.toPlaylist()
    }

    override suspend fun createPlaylist(name: String): Long {
        return playlistDao.upsert(
            PlaylistEntity(name = name),
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
            ),
        )
    }

    override suspend fun addMediaToPlaylist(playlistId: Long, mediumUris: List<String>) {
        val currentMaxPosition = playlistDao.getMaxPosition(playlistId) ?: -1

        val crossRefs = mediumUris.mapIndexed { index, uri ->
            PlaylistMediumCrossEntity(
                playlistId = playlistId,
                mediumUri = uri,
                position = currentMaxPosition + 1 + index,
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
}
