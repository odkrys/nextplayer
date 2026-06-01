package dev.anilbeesetti.nextplayer.feature.videopicker.screens.playlist

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.anilbeesetti.nextplayer.core.model.ApplicationPreferences
import dev.anilbeesetti.nextplayer.core.model.LinkErrorType
import dev.anilbeesetti.nextplayer.core.model.PlaylistSortOption
import dev.anilbeesetti.nextplayer.core.model.Video
import dev.anilbeesetti.nextplayer.core.ui.base.DataState
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons
import dev.anilbeesetti.nextplayer.feature.videopicker.composables.VideoItem
import kotlinx.coroutines.launch

@Composable
fun PlaylistDetailRoute(
    viewModel: PlaylistDetailViewModel,
    onPlayClick: (uris: List<String>, startIndex: Int) -> Unit,
    onVideoClick: (uris: List<String>, index: Int) -> Unit,
    onBackClick: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val remoteProgress by viewModel.remotePlaybackProgress.collectAsStateWithLifecycle()
    val remoteDurationMap by viewModel.remoteDurationMap.collectAsStateWithLifecycle()
    val isVerifying by viewModel.isVerifyingLinks.collectAsStateWithLifecycle()
    val deadLinks by viewModel.deadLinksFound.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.refreshRemoteProgress()
    }

    LaunchedEffect(viewModel.uiMessage) {
        viewModel.uiMessage.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    PlaylistDetailScreen(
        uiState = uiState,
        remoteProgress = remoteProgress,
        remoteDurationMap = remoteDurationMap,
        isVerifying = isVerifying,
        deadLinks = deadLinks,
        onVerifyLinks = viewModel::verifyWebdavLinks,
        onRemoveDeadLinks = viewModel::removeDeadLinks,
        onClearDeadLinks = viewModel::clearDeadLinksResult,
        onClearHistory = viewModel::clearPlaybackHistory,
        onPlayClick = {
            val uris = uiState.sortedVideos.map { it.uriString }
            if (uris.isEmpty()) return@PlaylistDetailScreen
            val startIndex = viewModel.getRecentVideoIndex()
            viewModel.saveLastPlayed(uris[startIndex])
            onPlayClick(uris, startIndex)
        },
        onVideoClick = { index ->
            val uris = uiState.sortedVideos.map { it.uriString }
            if (index >= uris.size) return@PlaylistDetailScreen
            viewModel.saveLastPlayed(uris[index])
            onVideoClick(uris, index)
        },
        onBackClick = onBackClick,
        onEvent = viewModel::onEvent,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    uiState: PlaylistDetailUiState,
    remoteProgress: Map<String, Float> = emptyMap(),
    remoteDurationMap: Map<String, Long> = emptyMap(),
    isVerifying: Boolean,
    deadLinks: List<DeadLink>,
    onVerifyLinks: () -> Unit,
    onRemoveDeadLinks: () -> Unit,
    onClearDeadLinks: () -> Unit,
    onClearHistory: () -> Unit,
    onPlayClick: () -> Unit,
    onVideoClick: (index: Int) -> Unit,
    onBackClick: () -> Unit,
    onEvent: (PlaylistDetailUiEvent) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val playlist = (uiState.dataState as? DataState.Success)?.value
    var isSelectionMode by rememberSaveable { mutableStateOf(false) }
    var selectedUris by rememberSaveable { mutableStateOf(emptySet<String>()) }
    var showClearHistoryDialog by rememberSaveable { mutableStateOf(false) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var showDeadLinksDialog by rememberSaveable { mutableStateOf(false) }
    var showSortMenu by rememberSaveable { mutableStateOf(false) }
    var isFabVisible by rememberSaveable { mutableStateOf(true) }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val isAllSelected = uiState.sortedVideos.isNotEmpty() && selectedUris.size == uiState.sortedVideos.size

    LaunchedEffect(uiState.sortOption) {
        if (uiState.sortedVideos.isNotEmpty()) {
            listState.scrollToItem(0)
        }
    }

    LaunchedEffect(deadLinks) {
        if (deadLinks.isNotEmpty()) {
            showDeadLinksDialog = true
        }
    }

    BackHandler {
        if (isSelectionMode) {
            isSelectionMode = false
            selectedUris = emptySet()
        } else {
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                isFabVisible = false
                onBackClick()
            }
        }
    }

    fun exitSelectionMode() {
        isSelectionMode = false
        selectedUris = emptySet()
    }

    fun applySort(newOption: PlaylistSortOption) {
        showSortMenu = false

        if (newOption == uiState.sortOption) return

        scope.launch {
            listState.scrollToItem(0)
            onEvent(PlaylistDetailUiEvent.UpdateSortOption(newOption))
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        if (isSelectionMode) {
                            Text("${selectedUris.size} / ${uiState.sortedVideos.size} selected")
                        } else {
                            Text(playlist?.name ?: "")
                        }
                    },
                    navigationIcon = {
                        if (isSelectionMode) {
                            IconButton(onClick = { exitSelectionMode() }) {
                                Icon(NextIcons.Close, contentDescription = "Clear selection")
                            }
                        } else {
                            IconButton(
                                onClick = {
                                    if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                                        isFabVisible = false
                                        onBackClick()
                                    }
                                },
                            ) {
                                Icon(NextIcons.ArrowBack, contentDescription = "Back")
                            }
                        }
                    },
                    actions = {
                        if (isSelectionMode) {
                            IconButton(
                                onClick = {
                                    if (isAllSelected) {
                                        selectedUris = emptySet()
                                    } else {
                                        selectedUris = uiState.sortedVideos.map { it.uriString }.toSet()
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (isAllSelected) NextIcons.DeselectAll else NextIcons.SelectAll,
                                    contentDescription = if (isAllSelected) "Select all" else "Deselect all"
                                )
                            }

                            IconButton(
                                onClick = { showDeleteDialog = true },
                                enabled = selectedUris.isNotEmpty(),
                            ) {
                                Icon(
                                    NextIcons.Delete,
                                    contentDescription = "Remove selected items",
                                    tint = if (selectedUris.isNotEmpty())
                                        MaterialTheme.colorScheme.error
                                    else
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                )
                            }
                        } else {
                            IconButton(
                                onClick = onVerifyLinks,
                                enabled = !isVerifying
                            ) {
                                Icon(NextIcons.CloudSync, contentDescription = "Verify Links")
                            }

                            Box {
                                IconButton(onClick = { showSortMenu = true }) {
                                    Icon(NextIcons.Sort, contentDescription = "Sort")
                                }
                                DropdownMenu(
                                    expanded = showSortMenu,
                                    onDismissRequest = { showSortMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Date added (Oldest)") },
                                        onClick = { applySort(PlaylistSortOption.ADDED_ASC) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Date added (Newest)") },
                                        onClick = { applySort(PlaylistSortOption.ADDED_DESC) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Name (A-Z)") },
                                        onClick = { applySort(PlaylistSortOption.NAME_ASC) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Name (Z-A)") },
                                        onClick = { applySort(PlaylistSortOption.NAME_DESC) }
                                    )
                                }
                            }
                            IconButton(onClick = { showClearHistoryDialog = true }) {
                                Icon(NextIcons.History, contentDescription = "Clear Playback History")
                            }
                        }
                    },
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        },
    ) { paddingValues ->
        val layoutDirection = LocalLayoutDirection.current

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = paddingValues.calculateTopPadding(),
                    start = paddingValues.calculateStartPadding(layoutDirection),
                    end = paddingValues.calculateEndPadding(layoutDirection),
                    bottom = 0.dp
                ),
        ) {
            if (isVerifying) {
                androidx.compose.material3.LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter),
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            when (val state = uiState.dataState) {
                is DataState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                is DataState.Success -> {
                    val playlistData = state.value
                    if (playlistData == null || playlistData.mediaUris.isEmpty()) {
                        Text(
                            text = "Playlist is empty",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    } else {
                        PlaylistDetailContent(
                            videos = uiState.sortedVideos,
                            lastPlayedUri = playlistData.lastPlayedUri,
                            deadLinks = deadLinks,
                            preferences = uiState.preferences,
                            remoteProgress = remoteProgress,
                            remoteDurationMap = remoteDurationMap,
                            isSelectionMode = isSelectionMode,
                            selectedUris = selectedUris,
                            listState = listState,
                            onVideoClick = { index ->
                                if (isSelectionMode) {
                                    val uri = uiState.sortedVideos[index].uriString
                                    selectedUris = if (uri in selectedUris) selectedUris - uri else selectedUris + uri
                                    if (selectedUris.isEmpty()) exitSelectionMode()
                                } else {
                                    onVideoClick(index)
                                }
                            },
                            onVideoLongClick = { index ->
                                if (!isSelectionMode) {
                                    isSelectionMode = true
                                    selectedUris = setOf(uiState.sortedVideos[index].uriString)
                                }
                            },
                        )
                    }
                }

                is DataState.Error -> {
                    Text(
                        text = "Failed to load playlist",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }

            if (!isSelectionMode && uiState.sortedVideos.isNotEmpty() && isFabVisible) {
                FloatingActionButton(
                    onClick = { onPlayClick() },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 32.dp, bottom = 32.dp),
                ) {
                    Icon(
                        imageVector = NextIcons.Play,
                        contentDescription = "Play playlist",
                    )
                }
            }
        }

        if (showClearHistoryDialog) {
            AlertDialog(
                onDismissRequest = { showClearHistoryDialog = false },
                title = { Text("Clear Playback History") },
                text = { Text("Are you sure you want to clear the playback history for all videos in this playlist?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onClearHistory()
                            showClearHistoryDialog = false
                        }
                    ) {
                        Text("Clear", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearHistoryDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text("Confirm removal") },
                text = {
                    Text(
                        if (selectedUris.size == 1) {
                            "Remove the selected item from this playlist?"
                        } else {
                            "Remove ${selectedUris.size} selected items from this playlist?"
                        },
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            selectedUris.forEach { uri ->
                                onEvent(PlaylistDetailUiEvent.RemoveMedium(uri))
                            }
                            showDeleteDialog = false
                            exitSelectionMode()
                        },
                    ) {
                        Text("Remove", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text("Cancel")
                    }
                },
            )
        }

        if (showDeadLinksDialog && deadLinks.isNotEmpty()) {
            val notFoundCount = deadLinks.count { it.errorType == LinkErrorType.NOT_FOUND }
            val unauthCount = deadLinks.count { it.errorType == LinkErrorType.UNAUTHORIZED }
            val networkCount = deadLinks.count { it.errorType == LinkErrorType.NETWORK_ERROR }

            val messageBuilder = buildString {
                append("Found ${deadLinks.size} unplayable file(s)\n\n")
                if (notFoundCount > 0) append("• Deleted from server: ${notFoundCount}\n")
                if (unauthCount > 0) append("• Unauthorized (password, etc.): ${unauthCount}\n")
                if (networkCount > 0) append("• Server unresponsive: ${networkCount}\n")
                append("\nWould you like to remove them from the playlist?")
            }

            AlertDialog(
                onDismissRequest = { showDeadLinksDialog = false },
                title = { Text("Link Verification Results") },
                text = { Text(messageBuilder) },
                confirmButton = {
                    TextButton(onClick = {
                        onRemoveDeadLinks()
                        showDeadLinksDialog = false
                    }) {
                        Text("Remove", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showDeadLinksDialog = false
                    }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
private fun PlaylistDetailContent(
    videos: List<Video>,
    lastPlayedUri: String?,
    deadLinks: List<DeadLink> = emptyList(),
    preferences: ApplicationPreferences,
    remoteProgress: Map<String, Float> = emptyMap(),
    remoteDurationMap: Map<String, Long> = emptyMap(),
    isSelectionMode: Boolean,
    selectedUris: Set<String>,
    onVideoClick: (index: Int) -> Unit,
    onVideoLongClick: (index: Int) -> Unit,
    listState: LazyListState = rememberLazyListState()
) {
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp, start = 8.dp, end = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        itemsIndexed(items = videos, key = { _, v -> v.uriString }) { index, video ->
            val isSelected = video.uriString in selectedUris
            val isRecentlyPlayed = video.uriString == lastPlayedUri
            val isDeadLink = deadLinks.any { it.url == video.uriString }
            val effectiveVideo = if (video.duration == 0L) {
                val realDuration = remoteDurationMap[video.uriString] ?: 0L
                val realPosition = if (realDuration > 0L) {
                    ((remoteProgress[video.uriString] ?: 0f) * realDuration).toLong()
                } else 0L
                video.copy(
                    duration = realDuration,
                    playbackPosition = realPosition,
                    formattedDuration = if (realDuration > 0L) formatVideoDuration(realDuration) else "00:00",
                )
            } else {
                video
            }

            VideoItem(
                video = effectiveVideo,
                isRecentlyPlayedVideo = isRecentlyPlayed,
                isDeadLink = isDeadLink,
                preferences = preferences,
                modifier = Modifier
                    .animateItem()
                    .fillMaxWidth(),
                isFirstItem = index == 0,
                isLastItem = index == videos.lastIndex,
                selected = isSelected,
                onClick = { onVideoClick(index) },
                onLongClick = { onVideoLongClick(index) },
            )
        }
    }
}

private fun formatVideoDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%02d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
