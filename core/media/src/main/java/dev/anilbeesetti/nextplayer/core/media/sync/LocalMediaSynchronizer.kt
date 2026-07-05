package dev.anilbeesetti.nextplayer.core.media.sync

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import androidx.core.net.toUri
import coil3.ImageLoader
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.anilbeesetti.nextplayer.core.common.di.ApplicationScope
import dev.anilbeesetti.nextplayer.core.common.extensions.getStorageVolumes
import dev.anilbeesetti.nextplayer.core.common.extensions.scanPaths
import dev.anilbeesetti.nextplayer.core.common.extensions.scanStorage
import dev.anilbeesetti.nextplayer.core.database.converter.UriListConverter
import dev.anilbeesetti.nextplayer.core.database.dao.MediumStateDao
import dev.anilbeesetti.nextplayer.core.database.dao.PlaylistDao
import dev.anilbeesetti.nextplayer.core.database.entities.MediumStateEntity
import dev.anilbeesetti.nextplayer.core.media.services.MediaService
import dev.anilbeesetti.nextplayer.core.media.services.MediaVideo
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LocalMediaSynchronizer @Inject constructor(
    private val mediumStateDao: MediumStateDao,
    private val imageLoader: ImageLoader,
    private val mediaService: MediaService,
    private val playlistDao: PlaylistDao,
    @ApplicationScope private val applicationScope: CoroutineScope,
    @ApplicationContext private val context: Context,
) : MediaSynchronizer {

    private var mediaSyncingJob: Job? = null

    override suspend fun refresh(path: String?): Boolean {
        return path?.let { context.scanPaths(listOf(path)) }
            ?: context.getStorageVolumes().all { context.scanStorage(it.path) }
    }

    override fun startSync() {
        if (mediaSyncingJob != null) return
        mediaSyncingJob = mediaService.observeVideos()
            .onEach { media -> updateMedia(media) }
            .launchIn(applicationScope)
    }

    override fun stopSync() {
        mediaSyncingJob?.cancel()
    }

    private suspend fun updateMedia(media: List<MediaVideo>) = withContext(Dispatchers.Default) {
        val currentMediaUris = media.map { it.uri.toString() }

        val (wantedMediaStates, unwantedMediaStates) = mediumStateDao.getAll().first().partition {
            it.uriString in currentMediaUris || !ContentResolver.SCHEME_CONTENT.equals(it.uriString.toUri().scheme, ignoreCase = true)
        }

        //mediumStateDao.delete(unwantedMediaStates.map { it.uriString })

        val unwantedUris = unwantedMediaStates.map { it.uriString }

        if (unwantedUris.isNotEmpty()) {
            mediumStateDao.delete(unwantedUris)
            playlistDao.removeDeletedMediaFromAllPlaylists(unwantedUris)
        }

        // Delete unwanted thumbnails
        unwantedMediaStates.forEach { mediaState ->
            try {
                imageLoader.diskCache?.remove(mediaState.uriString)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Release external subtitle uri permission if not used by any other media
        launch {
            val currentMediaExternalSubs = wantedMediaStates.flatMap {
                UriListConverter.fromStringToList(it.externalSubs)
            }.toSet()

            unwantedMediaStates.onEach { mediaState ->
                for (sub in UriListConverter.fromStringToList(mediaState.externalSubs)) {
                    if (sub !in currentMediaExternalSubs) {
                        try {
                            context.contentResolver.releasePersistableUriPermission(sub, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        }

        launch(Dispatchers.IO) {
            val hiddenVideos = media.filter {
                it.duration == 0L && it.uri.scheme == ContentResolver.SCHEME_FILE
            }

            if (hiddenVideos.isNotEmpty()) {
                val retriever = MediaMetadataRetriever()

                for (video in hiddenVideos) {
                    val uriString = video.uri.toString()

                    val existingState = mediumStateDao.get(uriString)
                    val needsUpdate = existingState == null ||
                            existingState.durationMs == null || existingState.durationMs == 0L ||
                            existingState.width == null ||
                            existingState.height == null

                    if (!needsUpdate) continue

                    try {
                        retriever.setDataSource(video.path)
                        val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        val duration = durationStr?.toLongOrNull()?.takeIf { it > 0 } ?: -1L
                        val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()?.takeIf { it > 0 } ?: -1
                        val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()?.takeIf { it > 0 } ?: -1

                        if (existingState != null) {
                            mediumStateDao.upsert(existingState.copy(durationMs = duration, width = width, height = height))
                        } else {
                            mediumStateDao.upsert(MediumStateEntity(uriString = uriString, durationMs = duration, width = width, height = height))
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        if (existingState != null) {
                            mediumStateDao.upsert(existingState.copy(durationMs = -1L, width = -1, height = -1))
                        } else {
                            mediumStateDao.upsert(MediumStateEntity(uriString = uriString, durationMs = -1L, width = -1, height = -1))
                        }
                    }
                }

                runCatching { retriever.release() }
            }
        }
    }
}
