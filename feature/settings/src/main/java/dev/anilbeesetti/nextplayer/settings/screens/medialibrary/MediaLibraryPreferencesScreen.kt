package dev.anilbeesetti.nextplayer.settings.screens.medialibrary

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.anilbeesetti.nextplayer.core.common.extensions.restartApplication
import dev.anilbeesetti.nextplayer.core.model.ThumbnailGenerationStrategy
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.components.ClickablePreferenceItem
import dev.anilbeesetti.nextplayer.core.ui.components.ListSectionTitle
import dev.anilbeesetti.nextplayer.core.ui.components.NextTopAppBar
import dev.anilbeesetti.nextplayer.core.ui.components.PreferenceSwitch
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons
import dev.anilbeesetti.nextplayer.core.ui.theme.NextPlayerTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun MediaLibraryPreferencesScreen(
    onNavigateUp: () -> Unit,
    onFolderSettingClick: () -> Unit = {},
    onThumbnailSettingClick: () -> Unit = {},
    viewModel: MediaLibraryPreferencesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    MediaLibraryPreferencesContent(
        uiState = uiState,
        onNavigateUp = onNavigateUp,
        onFolderSettingClick = onFolderSettingClick,
        onThumbnailSettingClick = onThumbnailSettingClick,
        onEvent = viewModel::onEvent,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MediaLibraryPreferencesContent(
    uiState: MediaLibraryPreferencesUiState,
    onNavigateUp: () -> Unit,
    onFolderSettingClick: () -> Unit,
    onThumbnailSettingClick: () -> Unit,
    onEvent: (MediaLibraryPreferencesUiEvent) -> Unit,
) {
    val preferences = uiState.preferences
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showCacheDialog by remember { mutableStateOf(false) }
    var showRestartDialog by remember { mutableStateOf(false) }
    val currentCacheSize = preferences.diskCacheSizeMb
    var pendingCacheSize by remember { mutableStateOf<Int?>(null) }

    Scaffold(
        topBar = {
            NextTopAppBar(
                title = stringResource(id = R.string.media_library),
                navigationIcon = {
                    FilledTonalIconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = NextIcons.ArrowBack,
                            contentDescription = stringResource(id = R.string.navigate_up),
                        )
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(state = rememberScrollState())
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            ListSectionTitle(text = stringResource(id = R.string.media_library))
            Column(
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                PreferenceSwitch(
                    title = stringResource(id = R.string.mark_last_played_media),
                    description = stringResource(
                        id = R.string.mark_last_played_media_desc,
                    ),
                    icon = NextIcons.Check,
                    isChecked = preferences.markLastPlayedMedia,
                    onClick = { onEvent(MediaLibraryPreferencesUiEvent.ToggleMarkLastPlayedMedia) },
                    isFirstItem = true,
                    isLastItem = true,
                )
            }

            ListSectionTitle(text = stringResource(id = R.string.scan))
            Column(
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                ClickablePreferenceItem(
                    title = stringResource(id = R.string.manage_folders),
                    description = stringResource(id = R.string.manage_folders_desc),
                    icon = NextIcons.FolderOff,
                    onClick = onFolderSettingClick,
                    isFirstItem = true,
                    //isLastItem = true,
                    isLastItem = false,
                )
                PreferenceSwitch(
                    title = stringResource(id = R.string.hide_excluded_media_in_playlists),
                    description = stringResource(id = R.string.hide_excluded_media_in_playlists_desc),
                    icon = NextIcons.VisibilityOff,
                    isChecked = preferences.hideExcludedMediaInPlaylists,
                    onClick = { onEvent(MediaLibraryPreferencesUiEvent.ToggleHideExcludedMediaInPlaylists) },
                    isFirstItem = false,
                    isLastItem = Build.VERSION.SDK_INT < Build.VERSION_CODES.R,
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    ClickablePreferenceItem(
                        title = stringResource(id = R.string.manage_external_storage),
                        description = stringResource(id = R.string.manage_external_storage_desc),
                        icon = NextIcons.Settings,
                        onClick = {
                            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                        },
                        isFirstItem = false,
                        isLastItem = true,
                    )
                }
            }


            ListSectionTitle(text = stringResource(id = R.string.thumbnail))
            Column(
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                ClickablePreferenceItem(
                    title = stringResource(id = R.string.thumbnail_generation),
                    description = when (preferences.thumbnailGenerationStrategy) {
                        ThumbnailGenerationStrategy.FIRST_FRAME -> stringResource(id = R.string.first_frame)
                        ThumbnailGenerationStrategy.FRAME_AT_PERCENTAGE -> stringResource(R.string.frame_at_position)
                        ThumbnailGenerationStrategy.HYBRID -> stringResource(id = R.string.hybrid)
                    },
                    icon = NextIcons.Image,
                    onClick = onThumbnailSettingClick,
                    isFirstItem = true,
                    isLastItem = true,
                )
            }

            ListSectionTitle(text = "Disk Cache")
            Column(
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                ClickablePreferenceItem(
                    title = "WebDAV Disk Cache",
                    description = when {
                        currentCacheSize == 0 -> "Off"
                        currentCacheSize >= 1024 -> "${"%.1f".format(currentCacheSize / 1024f)} GB"
                        else -> "$currentCacheSize MB"
                    },
                    icon = NextIcons.Storage,
                    onClick = { showCacheDialog = true },
                    isFirstItem = true,
                    isLastItem = true,
                )
            }
        }
    }

    var showCustomCacheInput by remember { mutableStateOf(false) }
    var customCacheInput by remember { mutableStateOf("") }

    if (showCacheDialog) {
        AlertDialog(
            onDismissRequest = { showCacheDialog = false },
            title = { Text("Select Cache Size") },
            text = {
                Column {
                    val options = listOf(0, 256, 512, 1024, 2048)
                    val isCustom = currentCacheSize !in listOf(0, 256, 512, 1024, 2048)

                    options.forEach { sizeMb ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (sizeMb != currentCacheSize) {
                                        pendingCacheSize = sizeMb
                                        showCacheDialog = false
                                        showRestartDialog = true
                                    } else {
                                        showCacheDialog = false
                                    }
                                }
                                .padding(vertical = 12.dp)
                        ) {
                            RadioButton(
                                selected = (sizeMb == currentCacheSize),
                                onClick = null
                            )
                            Text(
                                text = when {
                                    sizeMb == 0 -> "Off"
                                    sizeMb >= 1024 -> "${sizeMb / 1024} GB"
                                    else -> "$sizeMb MB"
                                },
                                modifier = Modifier.padding(start = 12.dp),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                customCacheInput = if (isCustom) {
                                    "%.1f".format(currentCacheSize / 1024f)
                                } else ""
                                showCacheDialog = false
                                showCustomCacheInput = true
                            }
                            .padding(vertical = 12.dp)
                    ) {
                        RadioButton(
                            selected = isCustom,
                            onClick = null
                        )
                        Text(
                            text = if (isCustom) {
                                "Custom (${"%.1f".format(currentCacheSize / 1024f)} GB)"
                            } else "Custom",
                            modifier = Modifier.padding(start = 12.dp),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCacheDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showCustomCacheInput) {
        val gb = customCacheInput.toFloatOrNull()
        val isValid = gb != null && gb in 0.1f..64f

        AlertDialog(
            onDismissRequest = {
                showCustomCacheInput = false
                customCacheInput = ""
            },
            title = { Text("Custom Cache Size") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Enter size in GB (e.g. 1.5) (Max 64GB)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = customCacheInput,
                        onValueChange = { input ->
                            if (input.matches(Regex("^\\d{0,2}(\\.\\d?)?\$"))) {
                                customCacheInput = input
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        suffix = { Text("GB") },
                        isError = customCacheInput.isNotEmpty() && !isValid,
                        supportingText = if (customCacheInput.isNotEmpty() && !isValid) {
                            { Text("Enter a value between 0.1 and 64 GB") }
                        } else null,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val gb = customCacheInput.toFloatOrNull()
                        if (gb != null && gb in 0.1f..64f) {
                            val sizeMb = (gb * 1024).toInt()
                            if (sizeMb != currentCacheSize) {
                                pendingCacheSize = sizeMb
                                showCustomCacheInput = false
                                showRestartDialog = true
                            } else {
                                showCustomCacheInput = false
                            }
                        }
                    },
                    enabled = isValid
                ) {
                    Text(stringResource(R.string.okay))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showCustomCacheInput = false
                    customCacheInput = ""
                }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showRestartDialog) {
        AlertDialog(
            onDismissRequest = {
                showRestartDialog = false
                pendingCacheSize = null
            },
            title = { Text("Restart Required") },
            text = { Text("The app needs to be restarted to apply the new cache size") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onEvent(MediaLibraryPreferencesUiEvent.UpdateMediaCacheSize(pendingCacheSize!!))
                        scope.launch {
                            delay(300L)
                            restartApplication(context)
                        }
                    },
                ) {
                    Text("Restart Now")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showRestartDialog = false
                        pendingCacheSize = null
                    }
                ) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@PreviewLightDark
@Composable
private fun MediaLibraryPreferencesScreenPreview() {
    NextPlayerTheme {
        MediaLibraryPreferencesContent(
            uiState = MediaLibraryPreferencesUiState(),
            onNavigateUp = {},
            onFolderSettingClick = {},
            onThumbnailSettingClick = {},
            onEvent = {},
        )
    }
}
