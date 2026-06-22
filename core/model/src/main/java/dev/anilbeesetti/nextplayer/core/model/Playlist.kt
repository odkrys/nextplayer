package dev.anilbeesetti.nextplayer.core.model

data class PlaylistMedia(
    val uri: String,
    val displayName: String,
    val fileSize: Long,
)

data class Playlist(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val lastPlayedUri: String? = null,
    val sortOption: PlaylistSortOption = PlaylistSortOption.ADDED_ASC,
    val position: Int = 0,
    val media: List<PlaylistMedia> = emptyList(),
)
