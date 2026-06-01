package dev.anilbeesetti.nextplayer.feature.videopicker.screens.remote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.anilbeesetti.nextplayer.core.data.repository.MediaRepository
import dev.anilbeesetti.nextplayer.core.data.repository.PlaylistRepository
import dev.anilbeesetti.nextplayer.core.domain.webdav.DeleteWebdavServerUseCase
import dev.anilbeesetti.nextplayer.core.domain.webdav.GetWebdavServersUseCase
import dev.anilbeesetti.nextplayer.core.domain.webdav.ReorderWebdavServersUseCase
import dev.anilbeesetti.nextplayer.core.domain.webdav.SaveWebdavServerUseCase
import dev.anilbeesetti.nextplayer.core.domain.webdav.TestWebdavConnectionUseCase
import dev.anilbeesetti.nextplayer.core.model.WebdavServer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RemoteUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val serverToDelete: WebdavServer? = null,
)

@HiltViewModel
class RemoteViewModel @Inject constructor(
    getWebdavServersUseCase: GetWebdavServersUseCase,
    private val saveWebdavServerUseCase: SaveWebdavServerUseCase,
    private val deleteWebdavServerUseCase: DeleteWebdavServerUseCase,
    private val testWebdavConnectionUseCase: TestWebdavConnectionUseCase,
    private val reorderWebdavServersUseCase: ReorderWebdavServersUseCase,
    private val mediaRepository: MediaRepository,
    private val playlistRepository: PlaylistRepository,
    ) : ViewModel() {

    private val _dbServers = getWebdavServersUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    val isServersLoaded: StateFlow<Boolean> = _dbServers
        .map { it != null }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false
        )

    val servers: StateFlow<List<WebdavServer>> = _dbServers
        .map { it ?: emptyList() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    private val _uiState = MutableStateFlow(RemoteUiState())
    val uiState: StateFlow<RemoteUiState> = _uiState.asStateFlow()

    fun saveServer(server: WebdavServer, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            val serverToSave = if (server.id == 0L) {
                val maxPosition = servers.value.maxOfOrNull { it.position } ?: -1
                server.copy(position = maxPosition + 1)
            } else {
                server
            }

            saveWebdavServerUseCase(serverToSave)
                .onSuccess {
                    _uiState.update { it.copy(isLoading = false) }
                    onSuccess()
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = e.message ?: e.toString())
                    }
                }
        }
    }

    fun requestDelete(server: WebdavServer) {
        _uiState.update { it.copy(serverToDelete = server) }
    }

    fun cancelDelete() {
        _uiState.update { it.copy(serverToDelete = null) }
    }


    fun confirmDelete() {
        val server = _uiState.value.serverToDelete ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, serverToDelete = null) }
            deleteWebdavServerUseCase(server.id)
                .onSuccess {
                    val scheme = if (server.useSsl) "https" else "http"
                    val portSuffix = if (server.port > 0) ":${server.port}" else ""
                    val pathSuffix = if (server.path.isNotEmpty() && !server.path.startsWith("/")) "/${server.path}" else server.path
                    val urlPrefix = "$scheme://${server.host}$portSuffix$pathSuffix"

                    mediaRepository.deleteByPrefix(urlPrefix)

                    playlistRepository.removeMediaByPrefix(urlPrefix)

                    _uiState.update {
                        it.copy(isLoading = false)
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = e.message)
                    }
                }
        }
    }

    fun testConnection(server: WebdavServer) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, successMessage = null) }
            testWebdavConnectionUseCase(server)
                .onSuccess {
                    _uiState.update {
                        it.copy(isLoading = false, successMessage = "Connected successfully")
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = e.message)
                    }
                }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }

    fun reorderServers(ids: List<Long>) {
        viewModelScope.launch {
            reorderWebdavServersUseCase(ids)
        }
    }
}
