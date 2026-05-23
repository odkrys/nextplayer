package dev.anilbeesetti.nextplayer.feature.videopicker.screens.remote

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.anilbeesetti.nextplayer.core.model.WebdavFile
import dev.anilbeesetti.nextplayer.core.model.WebdavServer
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebdavBrowserScreen(
    serverId: Long,
    onNavigateUp: () -> Unit,
    onPlayFile: (urls: List<String>, index: Int, server: WebdavServer) -> Unit,
    viewModel: WebdavBrowserViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(serverId) {
        viewModel.initServer(serverId)
    }

    LifecycleResumeEffect(Unit) {
        viewModel.refreshProgress()
        onPauseOrDispose {}
    }

    BackHandler {
        if (!viewModel.navigateUp()) onNavigateUp()
    }

    val server = uiState.server

    when {
        server == null -> {
            WebdavBrowserLoadingScreen(
                uiState = uiState,
                snackbarHostState = snackbarHostState,
                onNavigateUp = onNavigateUp,
                onClearError = viewModel::clearError,
            )
        }
        else -> {
            WebdavBrowserContent(
                server = server,
                uiState = uiState,
                snackbarHostState = snackbarHostState,
                onNavigateUp = onNavigateUp,
                onPlayFile = onPlayFile,
                viewModel = viewModel,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WebdavBrowserLoadingScreen(
    uiState: WebdavBrowserUiState,
    snackbarHostState: SnackbarHostState,
    onNavigateUp: () -> Unit,
    onClearError: () -> Unit,
) {
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            onClearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("WebDAV Browser") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(NextIcons.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center,
        ) {
            when {
                uiState.errorMessage != null -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Server not found",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "The requested server could not be loaded.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                else -> CircularProgressIndicator()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WebdavBrowserContent(
    server: WebdavServer,
    uiState: WebdavBrowserUiState,
    snackbarHostState: SnackbarHostState,
    onNavigateUp: () -> Unit,
    onPlayFile: (urls: List<String>, index: Int, server: WebdavServer) -> Unit,
    viewModel: WebdavBrowserViewModel,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    var showClearDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    val folderCount = uiState.files.count { it.isDirectory }
    val fileCount = uiState.files.count { !it.isDirectory }

    val countText = remember(folderCount, fileCount, uiState.isLoading) {
        buildString {
            if (folderCount > 0) {
                append("$folderCount folder")
                if (folderCount > 1) append("s")
            }
            if (folderCount > 0 && fileCount > 0) append(" · ")

            if (fileCount > 0) {
                append("$fileCount file")
                if (fileCount > 1) append("s")
            }
            if (folderCount == 0 && fileCount == 0 && !uiState.isLoading) {
                append("Empty")
            }
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "${server.name}  ($countText)",
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = viewModel.breadcrumb(),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (!viewModel.navigateUp()) onNavigateUp()
                    }) {
                        Icon(NextIcons.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showClearDialog = true }) {
                        Icon(NextIcons.History, contentDescription = "Clear Playback History")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },

        floatingActionButton = {
            if (uiState.lastPlayedUrl != null) {
                FloatingActionButton(
                    onClick = {
                        viewModel.playLastPlayed { urls, index ->
                            onPlayFile(urls, index, server)
                        }
                    },
                    modifier = Modifier.padding(end = 16.dp, bottom = 16.dp)
                ) {
                    Icon(
                        imageVector = NextIcons.Play,
                        contentDescription = "Resume Last Played",
                    )
                }
            }
        },
    ) { innerPadding ->

        PullToRefreshBox(
            isRefreshing = uiState.isLoading && uiState.files.isNotEmpty(),
            onRefresh = viewModel::refresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when {
                uiState.isLoading && uiState.files.isEmpty() -> {
                    Box(Modifier.fillMaxSize()) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                }

                uiState.files.isEmpty() && !uiState.isLoading -> {
                    EmptyDirectory(modifier = Modifier.fillMaxSize())
                }

                else -> {
                    FileList(
                        files = uiState.files,
                        playbackProgress = uiState.playbackProgress,
                        markLastPlayedMedia = uiState.markLastPlayedMedia,
                        serverBaseUrl = server.baseUrl,
                        lastPlayedUrl = uiState.lastPlayedUrl,
                        hasPlaybackHistory = uiState.hasPlaybackHistory,
                        onDirectoryClick = viewModel::navigateTo,
                        onFileClick = { file ->
                            val playableFiles = uiState.files.filter { viewModel.isPlayable(it) }
                            val urls = playableFiles.map { viewModel.buildFileUrl(server, it, uiState.files) }
                            val selectedIndex = playableFiles.indexOf(file).coerceAtLeast(0)
                            onPlayFile(urls, selectedIndex, server)
                        },
                        isPlayable = viewModel::isPlayable,
                    )
                }
            }
        }

        if (showClearDialog) {
            AlertDialog(
                onDismissRequest = { showClearDialog = false },
                title = { Text("Clear Playback History") },
                text = { Text("Are you sure you want to clear the playback history for all videos in this folder?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.clearPlaybackHistory()
                            showClearDialog = false
                        }
                    ) {
                        Text("Clear", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
private fun FileList(
    files: List<WebdavFile>,
    playbackProgress: Map<String, Float>,
    markLastPlayedMedia: Boolean,
    serverBaseUrl: String,
    lastPlayedUrl: String?,
    hasPlaybackHistory: Boolean,
    onDirectoryClick: (WebdavFile) -> Unit,
    onFileClick: (WebdavFile) -> Unit,
    isPlayable: (WebdavFile) -> Boolean,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        val base = serverBaseUrl.trimEnd('/')

        items(files, key = { it.href }) { file ->
            val playable = isPlayable(file)
            val progress = playbackProgress[file.href]
            val fileUrl = "$base/${file.path.trimStart('/')}"
            val isLastPlayed = hasPlaybackHistory &&
                    lastPlayedUrl != null &&
                    (lastPlayedUrl == fileUrl ||
                            lastPlayedUrl.startsWith(fileUrl.trimEnd('/') + "/"))

            FileListItem(
                file = file,
                playable = playable,
                progress = progress,
                markLastPlayedMedia = markLastPlayedMedia,
                isLastPlayed = isLastPlayed,
                onClick = {
                    if (file.isDirectory) {
                        onDirectoryClick(file)
                    } else if (playable) {
                        onFileClick(file)
                    }
                },
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
    }
}

@Composable
private fun FileListItem(
    file: WebdavFile,
    playable: Boolean,
    progress: Float?,
    markLastPlayedMedia: Boolean,
    isLastPlayed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        ListItem(
            modifier = Modifier.clickable(onClick = onClick),
            leadingContent = {
                Icon(
                    imageVector = file.icon(),
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = when {
                        file.isDirectory -> Color(0xFFFFD97F)
                        playable -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            },
            headlineContent = {
                Text(
                    text = file.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isLastPlayed && markLastPlayedMedia) MaterialTheme.colorScheme.primary
                    else Color.Unspecified,
                )
            },
            supportingContent = {
                val extension = file.name.substringAfterLast('.', "").uppercase()
                val infoText = listOf(extension, file.displaySize, file.lastModified)
                    .filter { it.isNotEmpty() }
                    .joinToString(" • ")

                if (infoText.isNotEmpty()) {
                    Text(
                        text = infoText,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isLastPlayed && markLastPlayedMedia) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
            trailingContent = if (playable) {
                {
                    Icon(
                        imageVector = NextIcons.Play,
                        contentDescription = "Playable",
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            } else null,
        )

        if (progress != null) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(3.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = StrokeCap.Round,
                gapSize = 0.dp
            )
        }
    }
}

@Composable
private fun EmptyDirectory(modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = NextIcons.Folder,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            )
            Text(
                text = "Folder is Empty",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun WebdavFile.icon(): ImageVector {
    if (this.isDirectory) return NextIcons.Folder

    val ext = this.name.substringAfterLast('.', "").lowercase()
    val mime = (this.contentType.ifBlank { this.mimeType }).lowercase()

    return when {
        mime.startsWith("video/") || ext in setOf(
            "mp4", "mkv", "avi", "mov", "webm", "ts", "m2ts", "flv", "wmv", "asf"
        ) -> NextIcons.Movie1

        mime.startsWith("audio/") || ext in setOf(
            "mp3", "flac", "wav", "m4a", "aac", "ogg", "wma", "ape", "opus"
        ) -> NextIcons.Music

        else -> NextIcons.Files
    }
}
