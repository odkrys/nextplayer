package dev.anilbeesetti.nextplayer.core.model

data class Playlist(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val lastPlayedUri: String? = null,
    val mediaUris: List<String> = emptyList(),
    val sortOption: PlaylistSortOption = PlaylistSortOption.ADDED_ASC,
    val position: Int = 0
)
