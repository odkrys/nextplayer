package dev.anilbeesetti.nextplayer.feature.videopicker.screens.remote

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.anilbeesetti.nextplayer.core.data.repository.MediaRepository
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.domain.webdav.GetWebdavServerByIdUseCase
import dev.anilbeesetti.nextplayer.core.domain.webdav.ListWebdavFilesUseCase
import dev.anilbeesetti.nextplayer.core.model.WebdavFile
import dev.anilbeesetti.nextplayer.core.model.WebdavServer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WebdavBrowserUiState(
    val server: WebdavServer? = null,
    val currentPath: String = "/",
    val pathStack: List<String> = listOf("/"),
    val files: List<WebdavFile> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val playbackProgress: Map<String, Float> = emptyMap(),
    val lastPlayedUrl: String? = null,
    val hasPlaybackHistory: Boolean = false,
    val markLastPlayedMedia: Boolean = true,
)

@HiltViewModel
class WebdavBrowserViewModel @Inject constructor(
    private val getWebdavServerByIdUseCase: GetWebdavServerByIdUseCase,
    private val listWebdavFilesUseCase: ListWebdavFilesUseCase,
    private val mediaRepository: MediaRepository,
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WebdavBrowserUiState())
    val uiState: StateFlow<WebdavBrowserUiState> = _uiState.asStateFlow()

    private var fileLoadingJob: Job? = null

    init {
        viewModelScope.launch {
            preferencesRepository.applicationPreferences.collect { prefs ->
                _uiState.update { it.copy(markLastPlayedMedia = prefs.markLastPlayedMedia) }
            }
        }
    }

    fun initServer(serverId: Long) {
        if (_uiState.value.server?.id == serverId) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            getWebdavServerByIdUseCase(serverId)
                .onSuccess { server ->
                    _uiState.update {
                        it.copy(
                            server = server,
                            currentPath = "/",
                            pathStack = listOf("/"),
                            files = emptyList(),
                            isLoading = false,

                            lastPlayedUrl = null,
                            hasPlaybackHistory = false,
                            playbackProgress = emptyMap()
                        )
                    }
                    loadFiles("/")
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = e.message ?: "Failed to load server")
                    }
                }
        }
    }

    fun loadFiles(path: String) {
        val server = _uiState.value.server ?: return

        fileLoadingJob?.cancel()

        fileLoadingJob = viewModelScope.launch {
            _uiState.update { it.copy(errorMessage = null) }

            val spinnerJob = launch {
                delay(250)
                _uiState.update { it.copy(isLoading = true) }
            }

            listWebdavFilesUseCase(server, path)
                .onSuccess { files ->
                    spinnerJob.cancel()

                    val sortedFiles = files.sortedWith(
                        compareBy<WebdavFile> { !it.isDirectory }
                            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
                    )

                    val progressMap = remotePlaybackProgress(server, sortedFiles)
                    val (lastPlayed, hasHistory) = loadLastPlayedFile(server, path, sortedFiles)

                    _uiState.update {
                        it.copy(
                            currentPath = path,
                            files = sortedFiles,
                            playbackProgress = progressMap,
                            lastPlayedUrl = lastPlayed,
                            hasPlaybackHistory = hasHistory,
                            isLoading = false,
                        )
                    }
                }
                .onFailure { e ->
                    spinnerJob.cancel()
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = e.message ?: e.toString())
                    }
                }
        }
    }

    private suspend fun remotePlaybackProgress(server: WebdavServer, files: List<WebdavFile>): Map<String, Float> {
        val playableFiles = files.filter { isPlayable(it) && !it.isDirectory }
        if (playableFiles.isEmpty()) return emptyMap()

        val progressMap = mutableMapOf<String, Float>()
        playableFiles.forEach { file ->
            try {
                val url = buildMediaId(server, file)
                val state = mediaRepository.getVideoState(url) ?: return@forEach

                val position = state.position ?: return@forEach
                if (position <= 0L) return@forEach

                val duration = state.durationMs ?: return@forEach
                if (duration <= 0L) return@forEach

                progressMap[file.href] = (position.toFloat() / duration).coerceIn(0f, 1f)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return progressMap
    }

    private fun buildMediaId(server: WebdavServer, file: WebdavFile): String {
        val base = server.baseUrl.trimEnd('/')
        val rawUrl = if (file.href.startsWith("http://") || file.href.startsWith("https://")) {
            file.href
        } else {
            "$base/${file.path.trimStart('/')}"
        }

        val uri = Uri.parse(rawUrl)
        val scheme = uri.scheme ?: (if (server.useSsl) "https" else "http")
        val hostAndPort = uri.authority ?: "${server.host}:${server.port}"

        return uri.buildUpon()
            .scheme(scheme)
            .encodedAuthority(hostAndPort)
            .build()
            .toString()
            .substringBefore("#")
    }

    fun refreshProgress() {
        val server = _uiState.value.server ?: return
        val files = _uiState.value.files
        val path = _uiState.value.currentPath
        if (files.isEmpty()) return

        viewModelScope.launch {
            val progressMap = remotePlaybackProgress(server, files)
            val (lastPlayed, hasHistory) = loadLastPlayedFile(server, path, files)

            _uiState.update {
                it.copy(
                    playbackProgress = progressMap,
                    lastPlayedUrl = lastPlayed,
                    hasPlaybackHistory = hasHistory,
                )
            }
        }
    }

    private suspend fun loadLastPlayedFile(
        server: WebdavServer,
        currentPath: String,
        files: List<WebdavFile>
    ): Pair<String?, Boolean> {
        val base = server.baseUrl.trimEnd('/')
        val normalizedPath = if (currentPath == "/") "" else currentPath.trimEnd('/')
        val urlPrefix = "$base$normalizedPath"
        val state = mediaRepository.getRecentUrlPrefix(urlPrefix)

        val hasHistory = state != null && state.lastPlayedTime != null
        var url: String? = null

        if (hasHistory) {
            val stateUrl = state!!.path
            val existsInFiles = files.any { file ->
                if (file.isDirectory) {
                    val folderMediaId = buildMediaId(server, file)
                    stateUrl.startsWith(folderMediaId)
                } else {
                    buildMediaId(server, file) == stateUrl
                }
            }
            if (existsInFiles) {
                url = stateUrl
            } else {
                mediaRepository.delete(listOf(stateUrl))
                val firstPlayable = files.firstOrNull { isPlayable(it) }
                url = firstPlayable?.let { buildMediaId(server, it) }
            }
        } else {
            val firstPlayable = files.firstOrNull { isPlayable(it) }
            url = firstPlayable?.let { buildMediaId(server, it) }
        }

        return Pair(url, hasHistory && url == state?.path)
    }

    fun playLastPlayed(
        onPlay: (urls: List<String>, index: Int) -> Unit,
    ) {
        val server = _uiState.value.server ?: return
        val lastUrl = _uiState.value.lastPlayedUrl ?: return

        viewModelScope.launch {
            val uri = Uri.parse(lastUrl)
            val fullPath = uri.path ?: "/"

            val basePath = Uri.parse(server.baseUrl).path?.trimEnd('/') ?: ""
            val relativePath = if (basePath.isNotEmpty() && fullPath.startsWith(basePath)) {
                fullPath.substring(basePath.length)
            } else {
                fullPath
            }
            val folderPath = relativePath.substringBeforeLast("/")

            val files = listWebdavFilesUseCase(server, folderPath)
                .getOrNull() ?: run {
                onPlay(listOf(lastUrl), 0)
                return@launch
            }

            val sortedFiles = files.sortedWith(
                compareBy<WebdavFile> { !it.isDirectory }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            )

            val playableFiles = sortedFiles.filter { isPlayable(it) }
            if (playableFiles.isEmpty()) {
                onPlay(listOf(lastUrl), 0)
                return@launch
            }

            val urls = playableFiles.map { buildFileUrl(server,it, sortedFiles) }
            val index = urls.indexOfFirst {
                it.substringBefore("#") == lastUrl.substringBefore("#")
            }.coerceAtLeast(0)

            onPlay(urls, index)
        }
    }

    fun buildFileUrl(server: WebdavServer, file: WebdavFile, allFiles: List<WebdavFile>): String {
        val base = server.baseUrl.trimEnd('/')
        val rawUrl = if (file.href.startsWith("http://") || file.href.startsWith("https://")) {
            file.href
        } else {
            "$base/${file.path.trimStart('/')}"
        }

        val uri = Uri.parse(rawUrl)
        val scheme = uri.scheme ?: (if (server.useSsl) "https" else "http")
        val hostAndPort = uri.authority ?: "${server.host}:${server.port}"

        val videoBaseName = file.name.substringBeforeLast(".")
        val subExtensions = listOf("srt", "ssa", "ass", "vtt", "ttml", "xml", "dfxp")
        val existingSubs = allFiles
            .filter { f ->
                !f.isDirectory &&
                        f.name.substringBeforeLast(".") == videoBaseName &&
                        f.name.substringAfterLast(".").lowercase() in subExtensions
            }
            .sortedBy { f ->
                subExtensions.indexOf(f.name.substringAfterLast(".").lowercase())
            }

        val fragmentBuilder = StringBuilder()
        existingSubs.forEach { subFile ->
            val subRawUrl = if (subFile.href.startsWith("http://") || subFile.href.startsWith("https://")) {
                subFile.href
            } else {
                "$base/${subFile.path.trimStart('/')}"
            }
            val subFullUrl = Uri.parse(subRawUrl)
                .buildUpon()
                .scheme(scheme)
                .encodedAuthority(hostAndPort)
                .build()
                .toString()

            if (fragmentBuilder.isNotEmpty()) fragmentBuilder.append("&")
            fragmentBuilder.append("${subFile.name.substringAfterLast(".")}=${Uri.encode(subFullUrl)}")
        }

        return uri.buildUpon()
            .scheme(scheme)
            .encodedAuthority(hostAndPort)
            .apply { if (fragmentBuilder.isNotEmpty()) fragment(fragmentBuilder.toString()) }
            .build()
            .toString()
    }

    fun navigateTo(directory: WebdavFile) {
        check(directory.isDirectory) { "You can only browse folders." }
        _uiState.update { state ->
            state.copy(pathStack = state.pathStack + directory.path)
        }
        loadFiles(directory.path)
    }

    fun navigateUp(): Boolean {
        val stack = _uiState.value.pathStack
        if (stack.size <= 1) return false

        val newStack = stack.dropLast(1)
        val prevPath = newStack.last()
        _uiState.update { it.copy(pathStack = newStack) }
        loadFiles(prevPath)
        return true
    }

    fun refresh() {
        loadFiles(_uiState.value.currentPath)
    }

    fun isPlayable(file: WebdavFile): Boolean {
        if (file.isDirectory) return false
        val ext = file.name.substringAfterLast('.', "").lowercase()
        val playableExt = setOf(
            "mp4", "mkv", "avi", "mov", "webm", "ts", "m2ts", "flv", "wmv", "asf",
            "mp3", "flac", "wav", "m4a", "aac", "ogg", "wma", "ape", "opus",
        )
        return ext in playableExt
    }

    fun breadcrumb(): String {
        val path = _uiState.value.currentPath
        if (path == "/") return "/"

        return path.split("/")
            .filter { it.isNotEmpty() }
            .joinToString(" / ", prefix = "/ ")
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun clearPlaybackHistory() {
        val server = _uiState.value.server ?: return
        val files = _uiState.value.files

        val playableFiles = files.filter { isPlayable(it) && !it.isDirectory }
        if (playableFiles.isEmpty()) return

        viewModelScope.launch {
            val urisToDelete = playableFiles.map { buildMediaId(server, it) }

            try {
                mediaRepository.delete(urisToDelete)
                refreshProgress()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun prepareMediaForPlaylist(
        server: WebdavServer,
        files: List<WebdavFile>,
        onReady: (List<String>) -> Unit,
    ) {
        viewModelScope.launch {
            val fullUrls = files.map { file ->
                buildFileUrl(server, file, uiState.value.files)
            }

            val dbUrls = fullUrls.map { url ->
                Uri.parse(url).buildUpon().fragment(null).build().toString()
            }

            files.forEachIndexed { index, file ->
                mediaRepository.upsertRemoteMedia(
                    uriString = dbUrls[index],
                    name = file.name,
                    parentPath = server.name,
                    size = file.size ?: 1024L,
                    format = file.name.substringAfterLast('.', "").lowercase(),
                )
            }

            onReady(fullUrls)
        }
    }
}
