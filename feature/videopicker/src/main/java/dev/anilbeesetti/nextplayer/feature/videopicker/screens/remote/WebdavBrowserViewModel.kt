package dev.anilbeesetti.nextplayer.feature.videopicker.screens.remote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
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
)

@HiltViewModel
class WebdavBrowserViewModel @Inject constructor(
    private val getWebdavServerByIdUseCase: GetWebdavServerByIdUseCase,
    private val listWebdavFilesUseCase: ListWebdavFilesUseCase,
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
                            isLoading = false
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
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = e.message ?: e.toString())
                    }
                }
        }
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
            "mp3", "flac", "wav", "m4a", "aac", "ogg", "wma", "ape", "opus"
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
}
