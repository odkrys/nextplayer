package dev.anilbeesetti.nextplayer.core.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "playlist_medium_cross_entity",
    primaryKeys = ["playlist_id", "medium_uri"],
    indices = [
        Index(value = ["playlist_id"]),
        Index(value = ["medium_uri"]),
    ],
)
data class PlaylistMediumCrossEntity(
    @ColumnInfo(name = "playlist_id")
    val playlistId: Long,

    @ColumnInfo(name = "medium_uri")
    val mediumUri: String,

    @ColumnInfo(name = "position")
    val position: Int = 0,

    @ColumnInfo(name = "added_at")
    val addedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "is_remote")
    val isRemote: Boolean = false,

    @ColumnInfo(name = "display_name")
    val displayName: String = "",

    @ColumnInfo(name = "file_size")
    val fileSize: Long = 0L,
)
