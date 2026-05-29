package dev.anilbeesetti.nextplayer.core.data.mappers

import dev.anilbeesetti.nextplayer.core.database.entities.PlaylistEntity
import dev.anilbeesetti.nextplayer.core.database.relations.PlaylistWithMedia
import dev.anilbeesetti.nextplayer.core.model.Playlist

fun PlaylistEntity.toPlaylist() = Playlist(
    id = id,
    name = name,
    createdAt = createdAt,
    updatedAt = updatedAt,
    lastPlayedUri = lastPlayedUri,
)

fun PlaylistWithMedia.toPlaylist() = Playlist(
    id = playlist.id,
    name = playlist.name,
    createdAt = playlist.createdAt,
    updatedAt = playlist.updatedAt,
    lastPlayedUri = playlist.lastPlayedUri,
    mediaUris = crossRefs.sortedBy { it.position }.map { it.mediumUri },
)
