package dev.anilbeesetti.nextplayer.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import dev.anilbeesetti.nextplayer.core.database.entities.WebdavServerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WebdavServerDao {

    @Query("SELECT * FROM webdav_servers ORDER BY createdAt DESC")
    fun getAllServers(): Flow<List<WebdavServerEntity>>

    @Query("SELECT * FROM webdav_servers WHERE id = :id")
    suspend fun getServerById(id: Long): WebdavServerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertServer(server: WebdavServerEntity): Long

    @Update
    suspend fun updateServer(server: WebdavServerEntity)

    @Delete
    suspend fun deleteServer(server: WebdavServerEntity)

    @Query("DELETE FROM webdav_servers WHERE id = :id")
    suspend fun deleteServerById(id: Long)

    @Query("SELECT COUNT(*) FROM webdav_servers")
    suspend fun getServerCount(): Int
}
