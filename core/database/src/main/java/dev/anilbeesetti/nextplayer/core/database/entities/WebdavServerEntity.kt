package dev.anilbeesetti.nextplayer.core.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "webdav_servers")
data class WebdavServerEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val host: String,
    val port: Int = 443,
    val path: String = "/",
    val username: String,
    val password: String,
    val useSsl: Boolean = true,
    val allowSelfSigned: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
