package dev.anilbeesetti.nextplayer.feature.videopicker.screens.remote

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
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

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    val folderCount = uiState.files.count { it.isDirectory }
    val fileCount = uiState.files.count { !it.isDirectory }

    val countText = remember(folderCount, fileCount) {
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
                    IconButton(onClick = viewModel::refresh) {
                        Icon(NextIcons.Refresh, contentDescription = "Refresh")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                        onDirectoryClick = viewModel::navigateTo,
                        onFileClick = { file ->
                            val playableFiles = uiState.files.filter { viewModel.isPlayable(it) }
                            val urls = playableFiles.map { buildFileUrl(server, it, uiState.files) }
                            val selectedIndex = playableFiles.indexOf(file).coerceAtLeast(0)
                            onPlayFile(urls, selectedIndex, server)
                        },
                        isPlayable = viewModel::isPlayable,
                    )
                }
            }
        }
    }
}

@Composable
private fun FileList(
    files: List<WebdavFile>,
    onDirectoryClick: (WebdavFile) -> Unit,
    onFileClick: (WebdavFile) -> Unit,
    isPlayable: (WebdavFile) -> Boolean,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        items(files, key = { it.href }) { file ->
            val playable = isPlayable(file)

            FileListItem(
                file = file,
                playable = playable,
                onClick = {
                    if (file.isDirectory) {
                        onDirectoryClick(file)
                    } else if (playable) {
                        onFileClick(file)
                    }
                },
            )
            HorizontalDivider(
                modifier = Modifier.padding(start = 72.dp),
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
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        modifier = modifier.clickable(onClick = onClick),
        leadingContent = {
            Icon(
                imageVector = file.icon(),
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = when {
                    file.isDirectory -> MaterialTheme.colorScheme.primary
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
            )
        },
        supportingContent = {
            val infoText = listOf(file.displaySize, file.lastModified)
                .filter { it.isNotEmpty() }
                .joinToString(" • ")

            if (infoText.isNotEmpty()) {
                Text(
                    text = infoText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            Spacer(Modifier.width(0.dp))
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

private fun buildFileUrl(server: WebdavServer, file: WebdavFile, allFiles: List<WebdavFile>): String {
    val base = server.baseUrl.trimEnd('/')
    val rawUrl = if (file.href.startsWith("http://") || file.href.startsWith("https://")) {
        file.href
    } else {
        "$base/${file.href.trimStart('/')}"
    }

    val uri = Uri.parse(rawUrl)
    val scheme = uri.scheme ?: (if (server.useSsl) "https" else "http")
    val hostAndPort = uri.authority ?: "${server.host}:${server.port}"

    val videoBaseName = file.name.substringBeforeLast(".")
    val subExtensions = setOf("srt", "ssa", "ass", "vtt", "ttml", "xml", "dfxp")
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
            "$base/${subFile.href.trimStart('/')}"
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