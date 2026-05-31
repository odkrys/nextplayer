package dev.anilbeesetti.nextplayer.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import dev.anilbeesetti.nextplayer.core.database.entities.MediumEntity
import dev.anilbeesetti.nextplayer.core.database.entities.PlaylistEntity
import dev.anilbeesetti.nextplayer.core.database.entities.PlaylistMediumCrossEntity
import dev.anilbeesetti.nextplayer.core.database.relations.PlaylistEntry
import dev.anilbeesetti.nextplayer.core.database.relations.PlaylistWithMedia
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Upsert
    suspend fun upsert(playlist: PlaylistEntity): Long

    @Query("SELECT * FROM playlists ORDER BY position ASC, created_at ASC")
    fun getAll(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun get(id: Long): PlaylistEntity?

    @Query("SELECT * FROM playlists WHERE id = :id")
    fun getAsFlow(id: Long): Flow<PlaylistEntity?>

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE playlists SET name = :name, updated_at = :updatedAt WHERE id = :id")
    suspend fun rename(id: Long, name: String, updatedAt: Long = System.currentTimeMillis())

    @Transaction
    @Query("SELECT * FROM playlists ORDER BY position ASC, created_at ASC")
    fun getAllWithMedia(): Flow<List<PlaylistWithMedia>>

    @Transaction
    @Query("SELECT * FROM playlists WHERE id = :id")
    fun getWithMedia(id: Long): Flow<PlaylistWithMedia?>

    @Upsert
    suspend fun addMedium(crossRef: PlaylistMediumCrossEntity)

    @Upsert
    suspend fun addMedia(crossRefs: List<PlaylistMediumCrossEntity>)

    @Query("DELETE FROM playlist_medium_cross_entity WHERE playlist_id = :playlistId AND medium_uri = :mediumUri")
    suspend fun removeMedium(playlistId: Long, mediumUri: String)

    @Query("DELETE FROM playlist_medium_cross_entity WHERE playlist_id = :playlistId")
    suspend fun clearPlaylist(playlistId: Long)

    @Query("UPDATE playlist_medium_cross_entity SET position = :position WHERE playlist_id = :playlistId AND medium_uri = :mediumUri")
    suspend fun updatePosition(playlistId: Long, mediumUri: String, position: Int)

    @Query("SELECT * FROM playlist_medium_cross_entity WHERE playlist_id = :playlistId ORDER BY position ASC")
    fun getMembers(playlistId: Long): Flow<List<PlaylistMediumCrossEntity>>

    @Query("""SELECT m.* FROM media m INNER JOIN playlist_medium_cross_entity p ON m.uri = p.medium_uri WHERE p.playlist_id = :playlistId ORDER BY p.position ASC""")
    fun getOrderedMediaForPlaylist(playlistId: Long): Flow<List<MediumEntity>>

    @Query("SELECT MAX(position) FROM playlist_medium_cross_entity WHERE playlist_id = :playlistId")
    suspend fun getMaxPosition(playlistId: Long): Int?

    @Query("UPDATE playlists SET last_played_uri = :uri, updated_at = :updatedAt WHERE id = :playlistId")
    suspend fun updateLastPlayedUri(playlistId: Long, uri: String, updatedAt: Long = System.currentTimeMillis())

    @Query("""SELECT medium_uri, is_remote, display_name FROM playlist_medium_cross_entity WHERE playlist_id = :playlistId ORDER BY position ASC""")
    fun getOrderedEntriesForPlaylist(playlistId: Long): Flow<List<PlaylistEntry>>

    @Query("UPDATE playlists SET sort_option = :sortOption WHERE id = :playlistId")
    suspend fun updateSortOption(playlistId: Long, sortOption: String)

    @Query("UPDATE playlists SET position = :position WHERE id = :id")
    suspend fun updatePosition(id: Long, position: Int)

    @Query("DELETE FROM playlist_medium_cross_entity WHERE medium_uri LIKE :prefix || '%'")
    suspend fun removeMediaByPrefix(prefix: String)

    @Query("DELETE FROM playlist_medium_cross_entity WHERE playlist_id = :playlistId AND medium_uri IN (:mediumUris)")
    suspend fun removeDeletedMediaFromPlaylist(playlistId: Long, mediumUris: List<String>)

    @Query("DELETE FROM playlist_medium_cross_entity WHERE medium_uri IN (:mediumUris)")
    suspend fun removeDeletedMediaFromAllPlaylists(mediumUris: List<String>)
}
