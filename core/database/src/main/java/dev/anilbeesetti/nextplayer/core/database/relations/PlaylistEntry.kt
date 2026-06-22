package dev.anilbeesetti.nextplayer.core.database.relations

import androidx.room.ColumnInfo

data class PlaylistEntry(
    @ColumnInfo(name = "medium_uri") val mediumUri: String,
    @ColumnInfo(name = "is_remote") val isRemote: Boolean,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "file_size") val fileSize: Long = 0L,
)
