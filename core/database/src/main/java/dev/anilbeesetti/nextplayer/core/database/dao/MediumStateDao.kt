package dev.anilbeesetti.nextplayer.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.anilbeesetti.nextplayer.core.database.entities.MediumStateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MediumStateDao {

    @Upsert
    suspend fun upsert(mediumState: MediumStateEntity)

    @Upsert
    suspend fun upsertAll(mediaStates: List<MediumStateEntity>)

    @Query("SELECT * FROM media_state WHERE uri = :uri")
    suspend fun get(uri: String): MediumStateEntity?

    @Query("SELECT * FROM media_state WHERE uri = :uri")
    fun getAsFlow(uri: String): Flow<MediumStateEntity?>

    @Query("SELECT * FROM media_state")
    fun getAll(): Flow<List<MediumStateEntity>>

    @Query("DELETE FROM media_state WHERE uri in (:uris)")
    suspend fun delete(uris: List<String>)

    @Query("UPDATE media_state SET duration_ms = :durationMs WHERE uri = :uri")
    suspend fun updateDuration(uri: String, durationMs: Long)

    @Query("SELECT * FROM media_state WHERE uri LIKE :urlPrefix || '%' ORDER BY last_played_time DESC LIMIT 1")
    suspend fun getRecentUrlPrefix(urlPrefix: String): MediumStateEntity?
    @Query("DELETE FROM media_state WHERE uri LIKE :urlPrefix || '%'")
    suspend fun deleteByPrefix(urlPrefix: String)
}
