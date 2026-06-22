package dev.anilbeesetti.nextplayer.feature.videopicker.navigation

import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import dev.anilbeesetti.nextplayer.feature.videopicker.screens.playlist.PlaylistRoute
import dev.anilbeesetti.nextplayer.feature.videopicker.screens.playlist.PlaylistDetailRoute
import dev.anilbeesetti.nextplayer.feature.videopicker.screens.playlist.PlaylistDetailViewModel
import dev.anilbeesetti.nextplayer.feature.videopicker.screens.playlist.PlaylistUiEvent
import dev.anilbeesetti.nextplayer.feature.videopicker.screens.playlist.PlaylistViewModel

const val SELECTED_URIS_ARG = "selectedUris"
const val SELECTED_SIZES_ARG = "selectedSizes"
const val PLAYLIST_ROUTE = "playlist"
const val PLAYLIST_DETAIL_ROUTE = "playlist/{${PLAYLIST_ID_ARG}}"

fun NavController.navigateToPlaylist(
    selectedUris: List<String> = emptyList(),
    selectedSizes: LongArray = LongArray(0)
) {
    currentBackStackEntry?.savedStateHandle?.set(SELECTED_URIS_ARG, selectedUris)
    currentBackStackEntry?.savedStateHandle?.set(SELECTED_SIZES_ARG, selectedSizes)
    navigate(PLAYLIST_ROUTE)
}

fun NavController.navigateToPlaylistDetail(playlistId: Long) {
    navigate("playlist/$playlistId")
}

fun NavGraphBuilder.playlistScreen(
    navController: NavController,
    onPlaylistClick: (playlistId: Long, selectedUris: List<String>) -> Unit,
    onBackClick: () -> Unit,
) {
    composable(
        route = PLAYLIST_ROUTE,
    ) { backStackEntry ->
        val viewModel: PlaylistViewModel = hiltViewModel()

        val previousEntry = navController.previousBackStackEntry
        if (previousEntry?.savedStateHandle?.contains(SELECTED_URIS_ARG) == true) {
            val uris = previousEntry.savedStateHandle.get<List<String>>(SELECTED_URIS_ARG)
            val sizes = previousEntry.savedStateHandle.get<LongArray>(SELECTED_SIZES_ARG) ?: LongArray(0)

            previousEntry.savedStateHandle.remove<List<String>>(SELECTED_URIS_ARG)
            previousEntry.savedStateHandle.remove<LongArray>(SELECTED_SIZES_ARG)

            backStackEntry.savedStateHandle.set(SELECTED_URIS_ARG, uris)
            backStackEntry.savedStateHandle.set(SELECTED_SIZES_ARG, sizes)
        }

        val selectedUris = backStackEntry.savedStateHandle.get<List<String>>(SELECTED_URIS_ARG) ?: emptyList()
        val selectedSizes = backStackEntry.savedStateHandle.get<LongArray>(SELECTED_SIZES_ARG) ?: LongArray(0)

        PlaylistRoute(
            selectedUris = selectedUris,
            selectedSizes = selectedSizes,
            viewModel = viewModel,
            onPlaylistClick = { playlistId, uris ->
                if (uris.isNotEmpty()) {
                    viewModel.onEvent(PlaylistUiEvent.AddMediaToPlaylist(playlistId, uris, selectedSizes.toList()))
                    backStackEntry.savedStateHandle.remove<List<String>>(SELECTED_URIS_ARG)
                    backStackEntry.savedStateHandle.remove<LongArray>(SELECTED_SIZES_ARG)
                    onBackClick()
                } else {
                    onPlaylistClick(playlistId, uris)
                }
            },
            onBackClick = {
                backStackEntry.savedStateHandle.remove<List<String>>(SELECTED_URIS_ARG)
                backStackEntry.savedStateHandle.remove<LongArray>(SELECTED_SIZES_ARG)
                onBackClick()
            },
        )
    }
}

fun NavGraphBuilder.playlistDetailScreen(
    onPlayClick: (playlistId: Long, uris: List<String>, startIndex: Int) -> Unit,
    onVideoClick: (playlistId: Long, uris: List<String>, index: Int) -> Unit,
    onBackClick: () -> Unit,
) {
    composable(
        route = PLAYLIST_DETAIL_ROUTE,
        arguments = listOf(
            navArgument(PLAYLIST_ID_ARG) { type = NavType.LongType },
        ),
    ) { backStackEntry ->
        val playlistId = backStackEntry.arguments?.getLong(PLAYLIST_ID_ARG) ?: -1L

        val viewModel: PlaylistDetailViewModel = hiltViewModel()

        PlaylistDetailRoute(
            viewModel = viewModel,
            onPlayClick = { uris, startIndex -> onPlayClick(playlistId, uris, startIndex) },
            onVideoClick = { uris, index -> onVideoClick(playlistId, uris, index) },
            onBackClick = onBackClick,
        )
    }
}
