package dev.anilbeesetti.nextplayer.core.model

fun Folder.flattenVideos(): List<Video> {
    return mediaList + folderList.flatMap { it.flattenVideos() }
}

fun Folder.SearchResultFolder(query: String): Folder {
    val matchedVideos = flattenVideos().filter { video ->
        video.displayName.contains(query, ignoreCase = true)
    }

    return copy(
        name = "Search Result",
        folderList = emptyList(),
        mediaList = matchedVideos
    )
}