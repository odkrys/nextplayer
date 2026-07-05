package dev.anilbeesetti.nextplayer.core.data.mappers

import dev.anilbeesetti.nextplayer.core.common.Utils
import dev.anilbeesetti.nextplayer.core.database.entities.MediumStateEntity
import dev.anilbeesetti.nextplayer.core.media.services.MediaVideo
import dev.anilbeesetti.nextplayer.core.model.Video
import java.util.Date
/*
internal fun MediaVideo.toVideo(mediaState: MediumStateEntity? = null) = Video(
    id = id,
    uriString = uri.toString(),
    duration = duration,
    height = height,
    width = width,
    path = path,
    size = size,
    nameWithExtension = title,
    parentPath = parentPath,
    dateModified = dateModified,
    formattedDuration = Utils.formatDurationMillis(duration),
    formattedFileSize = Utils.formatFileSize(size),
    playbackPosition = mediaState?.playbackPosition,
    lastPlayedAt = mediaState?.lastPlayedTime?.let { Date(it) },
)
*/
internal fun MediaVideo.toVideo(mediaState: MediumStateEntity? = null): Video {

    val finalDuration = if (duration > 0L) {
        duration
    } else {
        maxOf(mediaState?.durationMs ?: 0L, 0L)
    }

    return Video(
        id = id,
        uriString = uri.toString(),
        duration = finalDuration,
        height = if (height > 0) height else maxOf(mediaState?.height ?: 0, 0),
        width = if (width > 0) width else maxOf(mediaState?.width ?: 0, 0),
        path = path,
        size = size,
        nameWithExtension = displayName,
        parentPath = parentPath,
        dateModified = dateModified,
        formattedDuration = Utils.formatDurationMillis(finalDuration),
        formattedFileSize = Utils.formatFileSize(size),
        playbackPosition = mediaState?.playbackPosition,
        lastPlayedAt = mediaState?.lastPlayedTime?.let { Date(it) },
    )
}
