package dev.anilbeesetti.nextplayer.feature.videopicker.navigation

import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import dev.anilbeesetti.nextplayer.core.model.WebdavServer
import dev.anilbeesetti.nextplayer.feature.videopicker.screens.remote.RemoteHomeScreen
import dev.anilbeesetti.nextplayer.feature.videopicker.screens.remote.RemoteViewModel
import dev.anilbeesetti.nextplayer.feature.videopicker.screens.remote.WebdavBrowserScreen
import dev.anilbeesetti.nextplayer.feature.videopicker.screens.remote.WebdavBrowserViewModel
import dev.anilbeesetti.nextplayer.feature.videopicker.screens.remote.WebdavServerFormScreen

const val REMOTE_HOME_ROUTE = "remote_home"
const val WEBDAV_FORM_ROUTE = "webdav_form?serverId={serverId}"
const val WEBDAV_BROWSER_ROUTE = "webdav_browser/{serverId}"

fun NavController.navigateToRemoteHome() {
    navigate(REMOTE_HOME_ROUTE)
}

fun NavController.navigateToWebdavForm(serverId: Long? = null) {
    if (serverId != null) {
        navigate("webdav_form?serverId=$serverId")
    } else {
        navigate("webdav_form")
    }
}

fun NavController.navigateToWebdavBrowser(serverId: Long) {
    navigate("webdav_browser/$serverId")
}

fun NavGraphBuilder.webdavNavGraph(
    navController: NavController,
    onPlayFile: (urls: List<String>, index: Int, server: WebdavServer) -> Unit,
) {
    composable(
        route = REMOTE_HOME_ROUTE
    ) { backStackEntry ->
        val viewModel: RemoteViewModel = hiltViewModel()

        RemoteHomeScreen(
            viewModel = viewModel,
            onAddServer = { navController.navigateToWebdavForm() },
            onEditServer = { server -> navController.navigateToWebdavForm(server.id) },
            onBrowseServer = { server ->
                if (backStackEntry.lifecycle.currentState == Lifecycle.State.RESUMED) {
                    navController.navigateToWebdavBrowser(server.id)
                }
            },
        )
    }

    composable(
        route = WEBDAV_FORM_ROUTE,
        arguments = listOf(
            navArgument("serverId") {
                type = NavType.LongType
                defaultValue = 0L
            }
        ),
    ) { backStackEntry ->
        val serverId = backStackEntry.arguments?.getLong("serverId") ?: 0L

        WebdavServerFormScreen(
            serverId = serverId,
            onNavigateUp = { navController.navigateUp() },
        )
    }

    composable(
        route = WEBDAV_BROWSER_ROUTE,
        arguments = listOf(
            navArgument("serverId") { type = NavType.LongType }
        ),
    ) { backStackEntry ->
        val serverId = backStackEntry.arguments?.getLong("serverId") ?: 0L
        val viewModel: WebdavBrowserViewModel = hiltViewModel()

        WebdavBrowserScreen(
            serverId = serverId,
            viewModel = viewModel,
            onNavigateUp = { navController.navigateUp() },
            onPlayFile = onPlayFile,
            onAddToPlaylistClick = { uris, sizes: LongArray ->
                navController.navigateToPlaylist(uris, sizes)
            },
        )
    }
}
