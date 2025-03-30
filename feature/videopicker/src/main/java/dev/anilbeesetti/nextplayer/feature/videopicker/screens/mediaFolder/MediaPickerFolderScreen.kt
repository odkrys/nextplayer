package dev.anilbeesetti.nextplayer.feature.videopicker.screens.mediaFolder

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.anilbeesetti.nextplayer.core.common.extensions.prettyName
import dev.anilbeesetti.nextplayer.core.model.ApplicationPreferences
import dev.anilbeesetti.nextplayer.core.model.Folder
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.components.NextTopAppBar
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons
import dev.anilbeesetti.nextplayer.feature.videopicker.composables.MediaView
import dev.anilbeesetti.nextplayer.feature.videopicker.screens.MediaState
import java.io.File

@Composable
fun MediaPickerFolderRoute(
    viewModel: MediaPickerFolderViewModel = hiltViewModel(),
    onVideoClick: (uri: Uri) -> Unit,
    onFolderClick: (folderPath: String) -> Unit,
    onNavigateUp: () -> Unit,
) {
    // The app experiences jank when videosState updates before the initial render finishes.
    // By adding Lifecycle.State.RESUMED, we ensure that we wait until the first render completes.
    val mediaState by viewModel.mediaState.collectAsStateWithLifecycle(minActiveState = Lifecycle.State.RESUMED)
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    MediaPickerFolderScreen(
        folderPath = viewModel.folderPath,
        mediaState = mediaState,
        preferences = preferences,
        isRefreshing = uiState.refreshing,
        onPlayVideo = onVideoClick,
        onNavigateUp = onNavigateUp,
        onFolderClick = onFolderClick,
        onDeleteVideoClick = { viewModel.deleteVideos(listOf(it)) },
        onAddToSync = viewModel::addToMediaInfoSynchronizer,
        onRenameVideoClick = viewModel::renameVideo,
        onRefreshClicked = viewModel::onRefreshClicked,
        onDeleteFolderClick = { viewModel.deleteFolders(listOf(it)) },
        viewModel = viewModel
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MediaPickerFolderScreen(
    folderPath: String,
    mediaState: MediaState,
    preferences: ApplicationPreferences,
    isRefreshing: Boolean = false,
    onNavigateUp: () -> Unit,
    onPlayVideo: (Uri) -> Unit,
    onFolderClick: (String) -> Unit = {},
    onDeleteVideoClick: (String) -> Unit,
    onRenameVideoClick: (Uri, String) -> Unit = { _, _ -> },
    onAddToSync: (Uri) -> Unit,
    onRefreshClicked: () -> Unit = {},
    onDeleteFolderClick: (Folder) -> Unit = {},
    viewModel: MediaPickerFolderViewModel
) {
    val prefs = viewModel.preferences.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val pullToRefreshState = rememberPullToRefreshState()

    Scaffold(
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
        topBar = {
            NextTopAppBar(
                title = File(folderPath).prettyName,
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = NextIcons.ArrowBack,
                            contentDescription = stringResource(id = R.string.navigate_up),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                if (prefs.value.isShuffleOn) {
                    Toast.makeText(context, R.string.shuffle_disabled, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, R.string.shuffle_enabled, Toast.LENGTH_SHORT).show()
                }
                viewModel.toggleShuffle()
            }) {
                if (prefs.value.isShuffleOn) {
                    Icon(
                        imageVector = NextIcons.ShuffleOn,
                        contentDescription = stringResource(id = R.string.shuffle_enabled),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                } else {
                    Icon(
                        imageVector = NextIcons.Shuffle,
                        contentDescription = stringResource(id = R.string.shuffle_disabled),
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
        },
    ) { paddingValues ->
        PullToRefreshBox(
            modifier = Modifier.padding(paddingValues),
            state = pullToRefreshState,
            isRefreshing = isRefreshing,
            onRefresh = onRefreshClicked,
            contentAlignment = Alignment.Center,
        ) {
            MediaView(
                isLoading = mediaState is MediaState.Loading,
                rootFolder = (mediaState as? MediaState.Success)?.data,
                preferences = preferences,
                onFolderClick = onFolderClick,
                onDeleteFolderClick = onDeleteFolderClick,
                onVideoClick = onPlayVideo,
                onDeleteVideoClick = onDeleteVideoClick,
                onVideoLoaded = onAddToSync,
                onRenameVideoClick = onRenameVideoClick,
            )
        }
    }
}
