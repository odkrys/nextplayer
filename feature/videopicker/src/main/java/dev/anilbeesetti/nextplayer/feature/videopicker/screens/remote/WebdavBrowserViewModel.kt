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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

data class WebdavBrowserUiState(
    val server: WebdavServer? = null,
    val currentPath: String = "/",
    val pathStack: List<String> = listOf("/"),
    val files: List<WebdavFile> = emptyList(),
    val isLoading: Boolean = false,
    val isFetching: Boolean = false,
    val errorMessage: String? = null,
    val playbackProgress: Map<String, Float> = emptyMap(),
    val lastPlayedUrl: String? = null,
    val hasPlaybackHistory: Boolean = false,
    val markLastPlayedMedia: Boolean = true,
    val isPreparingPlaylist: Boolean = false,
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

    private val _navigationEvent = Channel<List<String>>()
    val navigationEvent = _navigationEvent.receiveAsFlow()

    private var fileLoadingJob: Job? = null
    private var playlistJob: Job? = null

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
            _uiState.update { it.copy(errorMessage = null, isFetching = true) }

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
                            isFetching = false,
                        )
                    }
                }
                .onFailure { e ->
                    spinnerJob.cancel()
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isFetching = false,
                            errorMessage = e.message ?: e.toString()
                        )
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
                val url = buildFileUrl(server, file)
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
        val encodedPath = Uri.encode(normalizedPath, "/")
        val urlPrefix = "$base$encodedPath"
        val state = mediaRepository.getRecentUrlPrefix(urlPrefix)

        val hasHistory = state != null && state.lastPlayedTime != null
        var url: String? = null

        if (hasHistory) {
            val stateUrl = state!!.path
            val existsInFiles = files.any { file ->
                if (file.isDirectory) {
                    val folderMediaId = buildFileUrl(server, file)
                    stateUrl.startsWith(folderMediaId)
                } else {
                    buildFileUrl(server, file) == stateUrl
                }
            }
            if (existsInFiles) {
                url = stateUrl
            } else {
                mediaRepository.delete(listOf(stateUrl))
                val firstPlayable = files.firstOrNull { isPlayable(it) }
                url = firstPlayable?.let { buildFileUrl(server, it) }
            }
        } else {
            val firstPlayable = files.firstOrNull { isPlayable(it) }
            url = firstPlayable?.let { buildFileUrl(server, it) }
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

            val folderPath = relativePath.substringBeforeLast("/").ifEmpty { "/" }

            val files = listWebdavFilesUseCase(server, folderPath).getOrNull() ?: run {
                onPlay(listOf(lastUrl), 0)
                return@launch
            }

            val playableFiles = files
                .filter { isPlayable(it) }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

            if (playableFiles.isEmpty()) {
                onPlay(listOf(lastUrl), 0)
                return@launch
            }

            val urls = playableFiles.map { buildFileUrl(server, it) }

            val index = urls.indexOfFirst { it == lastUrl }.coerceAtLeast(0)

            onPlay(urls, index)
        }
    }

    fun buildFileUrl(server: WebdavServer, file: WebdavFile): String {
        val baseUri = Uri.parse(server.baseUrl)
        val scheme = baseUri.scheme ?: if (server.useSsl) "https" else "http"
        val authority = baseUri.authority ?: "${server.host}:${server.port}"

        val basePath = baseUri.path?.trimEnd('/') ?: ""

        val fullPath = "$basePath/${file.path.trimStart('/')}"

        val encodedPath = Uri.encode(fullPath, "/")

        return Uri.Builder()
            .scheme(scheme)
            .encodedAuthority(authority)
            .encodedPath(encodedPath)
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
            val urisToDelete = playableFiles.map { buildFileUrl(server, it) }

            try {
                mediaRepository.delete(urisToDelete)
                refreshProgress()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private suspend fun collectPlayableFiles(
        server: WebdavServer,
        files: List<WebdavFile>,
    ): List<WebdavFile> {
        val result = mutableListOf<WebdavFile>()
        for (file in files) {
            currentCoroutineContext().ensureActive()
            if (file.isDirectory) {
                val children = listWebdavFilesUseCase(server, file.path)
                    .getOrElse { emptyList() }
                    .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

                result += collectPlayableFiles(server, children)
            } else if (isPlayable(file)) {
                result += file
            }
        }
        return result
    }

    fun prepareMediaForPlaylist(
        server: WebdavServer,
        selectedFiles: List<WebdavFile>,
    ) {
        playlistJob?.cancel()
        playlistJob = viewModelScope.launch {
            _uiState.update { it.copy(isPreparingPlaylist = true) }
            try {
                val collectedFiles = collectPlayableFiles(
                    server = server,
                    files = selectedFiles,
                )

                val urls = collectedFiles.map { file ->
                    currentCoroutineContext().ensureActive()
                    buildFileUrl(server, file)
                }

                currentCoroutineContext().ensureActive()
                _navigationEvent.send(urls)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message ?: "Failed to prepare playlist") }
            } finally {
                _uiState.update { it.copy(isPreparingPlaylist = false) }
            }
        }
    }

    fun cancelPreparePlaylist() {
        playlistJob?.cancel()
        playlistJob = null
        _uiState.update { it.copy(isPreparingPlaylist = false) }
    }
}
