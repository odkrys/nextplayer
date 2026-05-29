package dev.anilbeesetti.nextplayer.core.database.relations

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation
import dev.anilbeesetti.nextplayer.core.database.entities.MediumEntity
import dev.anilbeesetti.nextplayer.core.database.entities.PlaylistEntity
import dev.anilbeesetti.nextplayer.core.database.entities.PlaylistMediumCrossEntity

data class PlaylistWithMedia(
    @Embedded
    val playlist: PlaylistEntity,

    @Relation(
        parentColumn = "id",
        entityColumn = "uri",
        associateBy = Junction(
            value = PlaylistMediumCrossEntity::class,
            parentColumn = "playlist_id",
            entityColumn = "medium_uri",
        ),
    )
    val media: List<MediumEntity>,
)
