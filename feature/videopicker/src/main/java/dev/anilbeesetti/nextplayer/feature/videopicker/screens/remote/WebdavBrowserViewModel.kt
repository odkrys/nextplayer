package dev.anilbeesetti.nextplayer.feature.videopicker.screens.remote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.anilbeesetti.nextplayer.core.data.repository.MediaRepository
import dev.anilbeesetti.nextplayer.core.domain.webdav.GetWebdavServerByIdUseCase
import dev.anilbeesetti.nextplayer.core.domain.webdav.ListWebdavFilesUseCase
import dev.anilbeesetti.nextplayer.core.model.WebdavFile
import dev.anilbeesetti.nextplayer.core.model.WebdavServer
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
)

@HiltViewModel
class WebdavBrowserViewModel @Inject constructor(
    private val getWebdavServerByIdUseCase: GetWebdavServerByIdUseCase,
    private val listWebdavFilesUseCase: ListWebdavFilesUseCase,
    private val mediaRepository: MediaRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WebdavBrowserUiState())
    val uiState: StateFlow<WebdavBrowserUiState> = _uiState.asStateFlow()

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
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            listWebdavFilesUseCase(server, path)
                .onSuccess { files ->
                    val sortedFiles = files.sortedWith(
                        compareBy<WebdavFile> { !it.isDirectory }
                            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
                    )

                    _uiState.update {
                        it.copy(
                            currentPath = path,
                            files = sortedFiles,
                            isLoading = false,
                        )
                    }
                    remotePlaybackProgress(server, sortedFiles)
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = e.message ?: e.toString())
                    }
                }
        }
    }

    private fun remotePlaybackProgress(server: WebdavServer, files: List<WebdavFile>) {
        val playableFiles = files.filter { isPlayable(it) && !it.isDirectory }
        if (playableFiles.isEmpty()) return

        viewModelScope.launch {
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

            _uiState.update {
                it.copy(playbackProgress = progressMap)
            }
        }
    }

    private fun buildMediaId(server: WebdavServer, file: WebdavFile): String {
        val base = server.baseUrl.trimEnd('/')
        val rawUrl = if (file.href.startsWith("http://") || file.href.startsWith("https://")) {
            file.href
        } else {
            "$base/${file.href.trimStart('/')}"
        }

        val decodedUrl = runCatching {
            java.net.URLDecoder.decode(rawUrl, "UTF-8")
        }.getOrDefault(rawUrl)

        return decodedUrl.substringBefore("#")
    }

    fun refreshProgress() {
        val server = _uiState.value.server ?: return
        val files = _uiState.value.files
        if (files.isEmpty()) return
        remotePlaybackProgress(server, files)
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

                _uiState.update {
                    it.copy(playbackProgress = emptyMap())
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
