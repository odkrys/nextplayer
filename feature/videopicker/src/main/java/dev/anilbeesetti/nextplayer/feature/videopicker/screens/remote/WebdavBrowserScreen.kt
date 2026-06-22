package dev.anilbeesetti.nextplayer.feature.videopicker.screens.remote

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.ListItemDefaults
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.anilbeesetti.nextplayer.core.model.WebdavFile
import dev.anilbeesetti.nextplayer.core.model.WebdavServer
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebdavBrowserScreen(
    serverId: Long,
    viewModel: WebdavBrowserViewModel,
    onNavigateUp: () -> Unit,
    onPlayFile: (urls: List<String>, index: Int, server: WebdavServer) -> Unit,
    onAddToPlaylistClick: (List<String>, LongArray) -> Unit,
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
                onAddToPlaylistClick = onAddToPlaylistClick,
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
    onAddToPlaylistClick: (List<String>, LongArray) -> Unit,
    viewModel: WebdavBrowserViewModel,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    var showClearDialog by rememberSaveable { mutableStateOf(false) }
    var isSelectionMode by rememberSaveable { mutableStateOf(false) }
    var selectedHrefs by rememberSaveable { mutableStateOf(emptySet<String>()) }

    val selectedFiles = uiState.files.filter { it.href in selectedHrefs }.toSet()
    val playableFilesCount = uiState.files.count { viewModel.isPlayable(it) }
    val selectableFolderCount = uiState.files.count { it.isDirectory }
    val totalSelectableCount = playableFilesCount + selectableFolderCount
    val selectedFolders = selectedFiles.count { it.isDirectory }
    val selectedPlayable = selectedFiles.count { !it.isDirectory }
    val isAllSelected = selectedHrefs.size == totalSelectableCount && totalSelectableCount > 0
    var isFabVisible by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.navigationEvent.collect { mediaPairs ->
            val uris = mediaPairs.map { it.first }
            val sizes = mediaPairs.map { it.second }.toLongArray()

            onAddToPlaylistClick(uris, sizes)

            isSelectionMode = false
            selectedHrefs = emptySet()
        }
    }

    BackHandler {
        if (uiState.isPreparingPlaylist) {
            viewModel.cancelPreparePlaylist()
            return@BackHandler
        }
        if (isSelectionMode) {
            isSelectionMode = false
            selectedHrefs = emptySet()
        } else {
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                val navigatedUp = viewModel.navigateUp()
                if (!navigatedUp) {
                    isFabVisible = false
                    onNavigateUp()
                }
            }
        }
    }

    val folderCount = uiState.files.count { it.isDirectory }
    val fileCount = uiState.files.count { !it.isDirectory }

    val countText = remember(folderCount, fileCount, uiState.isLoading, uiState.isFetching) {
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
            if (folderCount == 0 && fileCount == 0 && !uiState.isLoading && !uiState.isFetching) {
                append("Empty")
            }
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            Column {
                if (isSelectionMode) {
                    TopAppBar(
                        title = {
                            Column {
                                Text("${selectedFiles.size} / $totalSelectableCount selected")
                                if (selectedFolders > 0) {
                                    Text(
                                        text = "$selectedFolders folder${if (selectedFolders > 1) "s" else ""} · $selectedPlayable file${if (selectedPlayable > 1) "s" else ""}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        },
                        navigationIcon = {
                            IconButton(
                                onClick = {
                                    if (uiState.isPreparingPlaylist) {
                                        viewModel.cancelPreparePlaylist()
                                    }
                                    isSelectionMode = false
                                    selectedHrefs = emptySet()
                                }
                            ) {
                                Icon(NextIcons.Close, contentDescription = "Clear selection")
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = {
                                    if (isAllSelected) {
                                        selectedHrefs = emptySet()
                                    } else {
                                        selectedHrefs = uiState.files.filter { viewModel.isPlayable(it) || it.isDirectory }.map { it.href }.toSet()
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (isAllSelected) NextIcons.DeselectAll else NextIcons.SelectAll,
                                    contentDescription = if (isAllSelected) "Deselect All" else "Select All"
                                )
                            }

                            IconButton(
                                onClick = {
                                    viewModel.prepareMediaForPlaylist(server, selectedFiles.toList())
                                },
                                enabled = selectedFiles.isNotEmpty() && !uiState.isPreparingPlaylist,
                            ) {
                                if (uiState.isPreparingPlaylist) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp,
                                    )
                                } else {
                                    Icon(NextIcons.Bookmarks, contentDescription = "Add to Playlist")
                                }
                            }
                        },
                        scrollBehavior = scrollBehavior,
                    )
                } else {
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
                            IconButton(
                                onClick = {
                                    if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                                        val navigatedUp = viewModel.navigateUp()
                                        if (!navigatedUp) {
                                            isFabVisible = false
                                            onNavigateUp()
                                        }
                                    }
                                },
                            ) {
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
                }
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        val layoutDirection = LocalLayoutDirection.current

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = innerPadding.calculateTopPadding(),
                    start = innerPadding.calculateStartPadding(layoutDirection),
                    end = innerPadding.calculateEndPadding(layoutDirection),
                    bottom = 0.dp
                ),
        ) {
            PullToRefreshBox(
                isRefreshing = uiState.isLoading && uiState.files.isNotEmpty(),
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    uiState.isLoading && uiState.files.isEmpty() -> {
                        Box(Modifier.fillMaxSize()) {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        }
                    }

                    uiState.files.isEmpty() && !uiState.isFetching -> {
                        EmptyDirectory(modifier = Modifier.fillMaxSize())
                    }

                    else -> {
                        FileList(
                            files = uiState.files,
                            selectedFiles = selectedFiles,
                            isSelectionMode = isSelectionMode,
                            onToggleSelection = { file ->
                                if (!isSelectionMode) isSelectionMode = true
                                selectedHrefs = if (file.href in selectedHrefs) {
                                    selectedHrefs - file.href
                                } else {
                                    selectedHrefs + file.href
                                }
                                if (selectedHrefs.isEmpty()) isSelectionMode = false
                            },
                            playbackProgress = uiState.playbackProgress,
                            markLastPlayedMedia = uiState.markLastPlayedMedia,
                            serverBaseUrl = server.baseUrl,
                            lastPlayedUrl = uiState.lastPlayedUrl,
                            hasPlaybackHistory = uiState.hasPlaybackHistory,
                            onDirectoryClick = viewModel::navigateTo,
                            onFileClick = { file ->
                                if (isSelectionMode) {
                                    if (!viewModel.isPlayable(file) || file.isDirectory) return@FileList
                                    selectedHrefs = if (file.href in selectedHrefs) {
                                        selectedHrefs - file.href
                                    } else {
                                        selectedHrefs + file.href
                                    }
                                    if (selectedHrefs.isEmpty()) {
                                        isSelectionMode = false
                                    }
                                } else {
                                    val playableFiles = uiState.files.filter { viewModel.isPlayable(it) }
                                    val urls = playableFiles.map { viewModel.buildFileUrl(server, it) }
                                    val selectedIndex = playableFiles.indexOf(file).coerceAtLeast(0)
                                    onPlayFile(urls, selectedIndex, server)
                                }
                            },
                            isPlayable = viewModel::isPlayable,
                            buildFileUrl = { viewModel.buildFileUrl(server, it) },
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

            if (uiState.lastPlayedUrl != null && !isSelectionMode && isFabVisible) {
                FloatingActionButton(
                    onClick = {
                        viewModel.playLastPlayed { urls, index ->
                            onPlayFile(urls, index, server)
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 32.dp, bottom = 32.dp)
                ) {
                    Icon(
                        imageVector = NextIcons.Play,
                        contentDescription = "Resume Last Played",
                    )
                }
            }
        }
    }
}


@Composable
private fun FileList(
    files: List<WebdavFile>,
    selectedFiles: Set<WebdavFile>,
    isSelectionMode: Boolean,
    onToggleSelection: (WebdavFile) -> Unit,
    playbackProgress: Map<String, Float>,
    markLastPlayedMedia: Boolean,
    serverBaseUrl: String,
    lastPlayedUrl: String?,
    hasPlaybackHistory: Boolean,
    onDirectoryClick: (WebdavFile) -> Unit,
    onFileClick: (WebdavFile) -> Unit,
    isPlayable: (WebdavFile) -> Boolean,
    buildFileUrl: (WebdavFile) -> String,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(top = 4.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        items(files, key = { it.href }) { file ->
            val playable = isPlayable(file)
            val isSelected = file in selectedFiles
            val progress = playbackProgress[file.href]
            val fileUrl = buildFileUrl(file)
            val isLastPlayed = hasPlaybackHistory &&
                    lastPlayedUrl != null &&
                    (lastPlayedUrl == fileUrl ||
                            lastPlayedUrl.startsWith(fileUrl.trimEnd('/') + "/"))

            FileListItem(
                file = file,
                playable = playable,
                isSelected = isSelected,
                isSelectionMode = isSelectionMode,
                onToggleSelection = { if (playable || file.isDirectory) onToggleSelection(file) },
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
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onToggleSelection: () -> Unit,
    progress: Float?,
    markLastPlayedMedia: Boolean,
    isLastPlayed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        ListItem(
            colors = ListItemDefaults.colors(
                containerColor = if (isSelected) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f) else Color.Transparent
            ),
            modifier = Modifier.combinedClickable(
                onClick = {
                    if (isSelectionMode && (playable || file.isDirectory)) onToggleSelection()
                    else onClick()
                },
                onLongClick = {
                    if (playable || file.isDirectory) onToggleSelection()                }
            ),
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
                val nameDisplay = if (file.isDirectory) {
                    file.name
                } else {
                    file.name.substringBeforeLast('.', file.name)
                }
                val displayTitle = nameDisplay.replace(".", ".\u200B")
                Text(
                    text = displayTitle,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isLastPlayed && markLastPlayedMedia) MaterialTheme.colorScheme.primary
                    else Color.Unspecified,
                )
            },
            supportingContent = {
                val extension = if (file.isDirectory) "" else file.name.substringAfterLast('.', "").uppercase()
                val otherInfo = listOf(file.displaySize, file.lastModified).filter { it.isNotEmpty() }

                val infoText = buildAnnotatedString {
                    if (extension.isNotEmpty()) {
                        val extensionColor = if (isLastPlayed && markLastPlayedMedia) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.secondary

                        withStyle(style = SpanStyle(color = extensionColor)) {
                            append(extension)
                        }
                    }

                    if (otherInfo.isNotEmpty()) {
                        val infoColor = if (isLastPlayed && markLastPlayedMedia) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)

                        withStyle(style = SpanStyle(color = infoColor)) {
                            if (extension.isNotEmpty()) {
                                append(" • ")
                            }
                            append(otherInfo.joinToString(" • "))
                        }
                    }
                }

                if (infoText.isNotEmpty()) {
                    Text(
                        text = infoText,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
            trailingContent = {
                if (playable) {
                    Icon(
                        imageVector = if (isSelectionMode && isSelected) NextIcons.CheckBox else NextIcons.Play,
                        contentDescription = if (isSelected) "Selected" else "Playable",
                        modifier = Modifier.size(28.dp),
                        tint = if (isSelectionMode && isSelected)
                            MaterialTheme.colorScheme.tertiary
                        else
                            MaterialTheme.colorScheme.primary,
                    )
                } else if (isSelectionMode && isSelected && file.isDirectory) {
                    Icon(
                        imageVector = NextIcons.CheckBox,
                        contentDescription = "Selected",
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
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
