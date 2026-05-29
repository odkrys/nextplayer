package dev.anilbeesetti.nextplayer.core.database.relations

import androidx.room.Embedded
import androidx.room.Relation
import dev.anilbeesetti.nextplayer.core.database.entities.PlaylistEntity
import dev.anilbeesetti.nextplayer.core.database.entities.PlaylistMediumCrossEntity

data class PlaylistWithMedia(
    @Embedded
    val playlist: PlaylistEntity,

    @Relation(
        parentColumn = "id",
        entityColumn = "playlist_id"
    )
    val crossRefs: List<PlaylistMediumCrossEntity>
)