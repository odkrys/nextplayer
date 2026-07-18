package dev.anilbeesetti.nextplayer.core.media.services

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.anilbeesetti.nextplayer.core.common.di.ApplicationScope
import dev.anilbeesetti.nextplayer.core.common.extensions.VIDEO_COLLECTION_URI
import dev.anilbeesetti.nextplayer.core.common.extensions.prettyName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

/**
 * [MediaService] implementation that queries the Android MediaStore for video files.
 *
 * This implementation provides a simple, flat view of media:
 * - Videos are fetched recursively from the specified folder path
 * - Folders are derived from video parent directories
 */
class MediaStoreMediaService @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val applicationScope: CoroutineScope,
    private val scannerSettingsProvider: ScannerSettingsProvider,
) : MediaService {

    companion object {
        private const val OBSERVER_DEBOUNCE_MS = 250L

        private val VIDEO_PROJECTION = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_MODIFIED,
        )
    }

    private val videosFlowCache = ConcurrentHashMap<String, Flow<List<MediaVideo>>>()
    private val foldersFlowCache = ConcurrentHashMap<String, Flow<List<MediaFolder>>>()

    /**
     * A single, shared signal of MediaStore changes.
     *
     * One [ContentObserver] is registered for all observers (regardless of folder path),
     * and bursts of change notifications (common during media scans) are coalesced via
     * [debounce] so downstream collectors re-query at most once per quiet window.
     */
    @OptIn(FlowPreview::class)
    private val mediaChanges: Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        context.contentResolver.registerContentObserver(VIDEO_COLLECTION_URI, true, observer)
        trySend(Unit)
        awaitClose { context.contentResolver.unregisterContentObserver(observer) }
    }
        .debounce(OBSERVER_DEBOUNCE_MS.milliseconds)
        .shareIn(
            scope = applicationScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            replay = 1,
        )
/*
    override fun observeFolders(folderPath: String?): Flow<List<MediaFolder>> {
        return mediaChanges
            .map { fetchFolders(folderPath) }
            .flowOn(Dispatchers.IO)
            .distinctUntilChanged()
    }

    override fun observeVideos(folderPath: String?): Flow<List<MediaVideo>> {
        return mediaChanges
            .map { fetchVideos(folderPath) }
            .flowOn(Dispatchers.IO)
            .distinctUntilChanged()
    }
*/
    override fun observeFolders(folderPath: String?): Flow<List<MediaFolder>> {
        val cacheKey = folderPath ?: "ROOT_FOLDER"

        return foldersFlowCache.computeIfAbsent(cacheKey) {
            combine(
                mediaChanges,
                scannerSettingsProvider.observeSettings()
            ) { _, _ ->
                fetchFolders(folderPath)
            }
                .flowOn(Dispatchers.IO)
                .distinctUntilChanged()
                .shareIn(
                    scope = applicationScope,
                    started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
                    replay = 1
                )
        }
    }

    override fun observeVideos(folderPath: String?): Flow<List<MediaVideo>> {
        val cacheKey = folderPath ?: "ROOT_FOLDER"

        return videosFlowCache.computeIfAbsent(cacheKey) {
            combine(
                mediaChanges,
                scannerSettingsProvider.observeSettings()
            ) { _, settings ->
                fetchVideosInternal(folderPath, settings)
            }
                .flowOn(Dispatchers.IO)
                .distinctUntilChanged()
                .shareIn(
                    scope = applicationScope,
                    started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
                    replay = 1
                )
        }
    }

    override suspend fun fetchFolders(folderPath: String?): List<MediaFolder> = withContext(Dispatchers.IO) {
        val videos = fetchVideos(folderPath)

        val videosByFolder = videos.groupBy { File(it.path).parentFile }

        return@withContext videosByFolder.mapNotNull { (folder, folderVideos) ->
            folder?.let {
                MediaFolder(
                    path = it.path,
                    name = it.prettyName,
                    dateModified = folderVideos.maxOfOrNull { video -> video.dateModified } ?: 0L,
                    totalSize = folderVideos.sumOf { video -> video.size },
                    totalDuration = folderVideos.sumOf { video -> video.duration },
                    videosCount = folderVideos.size,
                    foldersCount = 0,
                )
            }
        }
    }
/*
    override suspend fun fetchVideos(folderPath: String?): List<MediaVideo> = withContext(Dispatchers.IO) {
        return@withContext runMediaStoreQuery {
            val mediaVideos = mutableListOf<MediaVideo>()

            // A null folderPath scans every storage volume (e.g. SD cards / USB OTG). For a specific
            // folder, match it and its descendants, escaping LIKE metacharacters ('%', '_') in the path.
            val selection = if (folderPath == null) null else "${MediaStore.Video.Media.DATA} LIKE ? ESCAPE '\\'"
            val selectionArgs = if (folderPath == null) null else arrayOf("${folderPath.escapeLike()}/%")
            val sortOrder = "${MediaStore.Video.Media.DISPLAY_NAME} ASC"

            context.contentResolver.query(
                VIDEO_COLLECTION_URI,
                VIDEO_PROJECTION,
                selection,
                selectionArgs,
                sortOrder,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val video = cursor.toMediaVideo() ?: continue
                    mediaVideos.add(video)
                }
            }
            mediaVideos
        }
    }
*/
    override suspend fun fetchVideos(folderPath: String?): List<MediaVideo> {
        val settings = scannerSettingsProvider.observeSettings().first()
        return fetchVideosInternal(folderPath, settings)
    }

    private suspend fun fetchVideosInternal(
        folderPath: String?,
        settings: ScannerSettings
    ): List<MediaVideo> = withContext(Dispatchers.IO) {
        val mediaVideos = mutableListOf<MediaVideo>()

        // A null folderPath scans every storage volume (e.g. SD cards / USB OTG). For a specific
        // folder, match it and its descendants, escaping LIKE metacharacters ('%', '_') in the path.
        val selection = if (folderPath == null) null else "${MediaStore.Video.Media.DATA} LIKE ? ESCAPE '\\'"
        val selectionArgs = if (folderPath == null) null else arrayOf("${folderPath.escapeLike()}/%")
        val sortOrder = "${MediaStore.Video.Media.DISPLAY_NAME} ASC"

        context.contentResolver.query(
            VIDEO_COLLECTION_URI,
            VIDEO_PROJECTION,
            selection,
            selectionArgs,
            sortOrder,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val video = cursor.toMediaVideo() ?: continue
                mediaVideos.add(video)
            }
        }

        if (settings.scanNomedia || settings.scanHidden) {
            val hiddenVideos = scanFilesWithJavaFileApi(
                folderPath = folderPath,
                scanNomedia = settings.scanNomedia,
                scanHidden = settings.scanHidden
            )

            val existingPaths = mediaVideos.map { it.path }.toMutableSet()

            hiddenVideos.forEach { hiddenVideo ->
                if (existingPaths.add(hiddenVideo.path)) {
                    mediaVideos.add(hiddenVideo)
                }
            }
        }

        return@withContext mediaVideos
    }
/*
    override suspend fun findVideo(uri: Uri): MediaVideo? = withContext(Dispatchers.IO) {
        return@withContext try {
            context.contentResolver.query(
                uri,
                VIDEO_PROJECTION,
                null,
                null,
                null,
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@withContext null
                cursor.toMediaVideo()
            }
        } catch (e: Exception) {
            // uri isn't a MediaStore video uri (e.g. a vault FileProvider uri) and the
            // provider rejected the MediaStore-specific projection outright. Not finding a
            // MediaStore row for it is the correct outcome, not a crash.
            null
        }
    }
*/
    override suspend fun findVideo(uri: Uri): MediaVideo? = withContext(Dispatchers.IO) {
        if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            val selection = "${MediaStore.Video.Media._ID} = ?"
            val selectionArgs = arrayOf(uri.lastPathSegment ?: return@withContext null)

            context.contentResolver.query(
                VIDEO_COLLECTION_URI,
                VIDEO_PROJECTION,
                selection,
                selectionArgs,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    return@withContext cursor.toMediaVideo()
                }
            }
        }

        if (uri.scheme == ContentResolver.SCHEME_FILE) {
            val path = uri.path ?: return@withContext null
            val file = File(path)

            if (file.exists() && file.isFile) {
                return@withContext MediaVideo(
                    id = file.absolutePath.toStableId(),
                    path = file.absolutePath,
                    title = file.nameWithoutExtension,
                    parentPath = file.parent ?: "/",
                    uri = uri,
                    displayName = file.name,
                    duration = 0L,
                    width = 0,
                    height = 0,
                    size = file.length(),
                    dateModified = file.lastModified() / 1000
                )
            }
        }

        return@withContext null
    }

    override suspend fun findFolder(path: String): MediaFolder? = withContext(Dispatchers.IO) {
        val folderFile = File(path)
        if (!folderFile.exists()) return@withContext null

        val allVideos = fetchVideos(path)
        if (allVideos.isEmpty()) return@withContext null

        val directVideos = allVideos.filter { File(it.path).parent == path }
        val directSubfolders = allVideos
            .mapNotNull { video ->
                File(video.path).parentFile?.walkUp()?.firstOrNull { it.parent == path }
            }
            .distinctBy { it.path }

        return@withContext MediaFolder(
            path = folderFile.path,
            name = folderFile.prettyName,
            dateModified = allVideos.maxOfOrNull { it.dateModified } ?: folderFile.lastModified(),
            totalSize = allVideos.sumOf { it.size },
            totalDuration = allVideos.sumOf { it.duration },
            videosCount = directVideos.size,
            foldersCount = directSubfolders.size,
        )
    }

    /**
     * Walks up the directory tree from this file's parent to the root.
     */
    private fun File.walkUp() = generateSequence(parentFile) { it.parentFile }

    /**
     * Escapes SQL LIKE metacharacters so a path is matched literally (used with `ESCAPE '\'`).
     */
    private fun String.escapeLike(): String =
        replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    /**
     * Converts the current row to a [MediaVideo], or null if this cursor doesn't actually
     * expose MediaStore's video columns.
     *
     * [findVideo] runs this against whatever content uri it's given, which isn't always a
     * MediaStore uri - vault playback, for example, passes a [androidx.core.content.FileProvider]
     * uri whose cursor only ever has DISPLAY_NAME/SIZE columns. Querying MediaStore-specific
     * columns (DATA, DURATION, etc) against that cursor has no matching column, and
     * [Cursor.getColumnIndexOrThrow] throws IllegalArgumentException in that case. That
     * exception was previously unhandled here, which crashed the coroutine awaiting this result
     * and made vault playback close immediately. Resolving column indices safely up front avoids
     * that crash and simply reports "not found" for any uri that isn't a MediaStore video uri.
     */
    private fun Cursor.toMediaVideo(): MediaVideo? {
        val dataIndex = getColumnIndex(MediaStore.Video.Media.DATA).takeIf { it >= 0 } ?: return null
        val idIndex = getColumnIndex(MediaStore.Video.Media._ID).takeIf { it >= 0 } ?: return null
        val displayNameIndex = getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME).takeIf { it >= 0 } ?: return null
        val durationIndex = getColumnIndex(MediaStore.Video.Media.DURATION).takeIf { it >= 0 } ?: return null
        val widthIndex = getColumnIndex(MediaStore.Video.Media.WIDTH).takeIf { it >= 0 } ?: return null
        val heightIndex = getColumnIndex(MediaStore.Video.Media.HEIGHT).takeIf { it >= 0 } ?: return null
        val sizeIndex = getColumnIndex(MediaStore.Video.Media.SIZE).takeIf { it >= 0 } ?: return null
        val dateModifiedIndex = getColumnIndex(MediaStore.Video.Media.DATE_MODIFIED).takeIf { it >= 0 } ?: return null

        val path = getString(dataIndex) ?: return null
        val file = File(path)
        if (!file.exists()) return null
        val id = getLong(idIndex)
        return MediaVideo(
            id = id,
            path = path,
            title = file.name,
            parentPath = file.parent ?: "/",
            uri = ContentUris.withAppendedId(VIDEO_COLLECTION_URI, id),
            displayName = getString(displayNameIndex) ?: file.name,
            duration = getLong(durationIndex),
            width = getInt(widthIndex),
            height = getInt(heightIndex),
            size = getLong(sizeIndex),
            dateModified = getLong(dateModifiedIndex),
        )
    }

    private suspend fun scanFilesWithJavaFileApi(
        folderPath: String?,
        scanNomedia: Boolean,
        scanHidden: Boolean,
    ): List<MediaVideo> = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            return@withContext emptyList()
        }

        val hiddenVideos = mutableListOf<MediaVideo>()

        val skipFolders = setOf("android", "cache", "temp", "logs", "obb", ".trash")
        val videoExtensions = setOf(
            "mp4", "m4v", "mkv", "webm", "avi", "mov", "wmv", "flv", "mpeg", "mpg",
            "3gp", "ts", "m2ts", "mts", "vob", "ogv", "rmvb", "asf", "divx", "f4v",
        )

        val searchRoots = if (folderPath != null) {
            listOf(File(folderPath))
        } else {
            context.getExternalFilesDirs(null).mapNotNull { file ->
                val rootPath = file?.absolutePath?.substringBefore("/Android")
                rootPath?.let { File(it) }
            }
                .distinctBy { it.absolutePath }
                .ifEmpty {
                    listOf(Environment.getExternalStorageDirectory())
                }
        }

        searchRoots.forEach { root ->
            if (root.exists() && root.isDirectory) {
                root.walkTopDown()
                    .onEnter { dir ->
                        val dirName = dir.name.lowercase()
                        if (dirName in skipFolders) return@onEnter false

                        val isHiddenDir = dir.name.startsWith(".")
                        if (isHiddenDir && !scanHidden) {
                            return@onEnter false
                        }

                        val hasNomedia = File(dir, ".nomedia").exists()
                        if (hasNomedia && !scanNomedia) {
                            return@onEnter false
                        }

                        true
                    }
                    .forEach { file ->
                        if (file.isFile && file.extension.lowercase() in videoExtensions) {
                            val isHiddenFile = file.name.startsWith(".")
                            if (isHiddenFile && !scanHidden) return@forEach

                            hiddenVideos.add(
                                MediaVideo(
                                    id = file.absolutePath.toStableId(),
                                    path = file.absolutePath,
                                    title = file.nameWithoutExtension,
                                    parentPath = file.parent ?: "/",
                                    uri = Uri.fromFile(file),
                                    displayName = file.name,
                                    duration = 0L,
                                    width = 0,
                                    height = 0,
                                    size = file.length(),
                                    dateModified = file.lastModified() / 1000
                                )
                            )
                        }
                    }
            }
        }

        return@withContext hiddenVideos
    }

    private fun String.toStableId(): Long {
        return this.hashCode().toLong() and 0xFFFFFFFFL
    }
}
