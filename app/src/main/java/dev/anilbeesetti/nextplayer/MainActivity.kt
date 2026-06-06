package dev.anilbeesetti.nextplayer

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.SimpleCache
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import dagger.hilt.android.AndroidEntryPoint
import dev.anilbeesetti.nextplayer.core.media.services.MediaOperationsService
import dev.anilbeesetti.nextplayer.core.media.sync.MediaSynchronizer
import dev.anilbeesetti.nextplayer.core.model.ThemeConfig
import dev.anilbeesetti.nextplayer.core.ui.base.LocalBottomBarVisibility
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons
import dev.anilbeesetti.nextplayer.core.ui.theme.NextPlayerTheme
import dev.anilbeesetti.nextplayer.feature.videopicker.navigation.PLAYLIST_ROUTE
import dev.anilbeesetti.nextplayer.feature.videopicker.navigation.REMOTE_HOME_ROUTE
import dev.anilbeesetti.nextplayer.navigation.MediaRootRoute
import dev.anilbeesetti.nextplayer.navigation.mediaNavGraph
import dev.anilbeesetti.nextplayer.navigation.settingsNavGraph
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var synchronizer: MediaSynchronizer

    @Inject
    lateinit var mediaOperationsService: MediaOperationsService

    @UnstableApi
    @Inject
    lateinit var simpleCache: SimpleCache

    private val viewModel: MainViewModel by viewModels()

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mediaOperationsService.initialize(this@MainActivity)
        synchronizer.startSync()

        var uiState: MainActivityUiState by mutableStateOf(MainActivityUiState.Loading)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    uiState = state
                }
            }
        }

        installSplashScreen().setKeepOnScreenCondition {
            when (uiState) {
                MainActivityUiState.Loading -> true
                is MainActivityUiState.Success -> false
            }
        }

        setContent {
            val shouldUseDarkTheme = shouldUseDarkTheme(uiState = uiState)

            LaunchedEffect(shouldUseDarkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        lightScrim = Color.TRANSPARENT,
                        darkScrim = Color.TRANSPARENT,
                        detectDarkMode = { shouldUseDarkTheme },
                    ),
                    navigationBarStyle = SystemBarStyle.auto(
                        lightScrim = Color.TRANSPARENT,
                        darkScrim = Color.TRANSPARENT,
                        detectDarkMode = { shouldUseDarkTheme },
                    ),
                )
            }

            NextPlayerTheme(
                darkTheme = shouldUseDarkTheme,
                highContrastDarkTheme = shouldUseHighContrastDarkTheme(uiState = uiState),
                dynamicColor = shouldUseDynamicTheming(uiState = uiState),
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    val mainNavController = rememberNavController()
/*
                    NavHost(
                        navController = mainNavController,
                        startDestination = MediaRootRoute,
                        enterTransition = {
                            slideIntoContainer(
                                towards = AnimatedContentTransitionScope.SlideDirection.Start,
                                animationSpec = tween(
                                    durationMillis = 200,
                                    easing = LinearEasing,
                                ),
                            )
                        },
                        exitTransition = {
                            slideOutOfContainer(
                                towards = AnimatedContentTransitionScope.SlideDirection.Start,
                                animationSpec = tween(
                                    durationMillis = 200,
                                    easing = LinearEasing,
                                ),
                                targetOffset = { fullOffset -> (fullOffset * 0.3f).toInt() },
                            )
                        },
                        popEnterTransition = {
                            slideIntoContainer(
                                towards = AnimatedContentTransitionScope.SlideDirection.End,
                                animationSpec = tween(
                                    durationMillis = 200,
                                    easing = LinearEasing,
                                ),
                                initialOffset = { fullOffset -> (fullOffset * 0.3f).toInt() },
                            )
                        },
                        popExitTransition = {
                            slideOutOfContainer(
                                towards = AnimatedContentTransitionScope.SlideDirection.End,
                                animationSpec = tween(
                                    durationMillis = 200,
                                    easing = LinearEasing,
                                ),
                            )
                        },
                    ) {
                        mediaNavGraph(
                            context = this@MainActivity,
                            navController = mainNavController,
                        )
                        settingsNavGraph(navController = mainNavController)
                    }
*/
                    val isBottomBarVisible = remember { mutableStateOf(true) }
                    val navBackStackEntry by mainNavController.currentBackStackEntryAsState()
                    val currentRoute = navBackStackEntry?.destination?.route

                    fun isTopLevelRoute(route: String?): Boolean {
                        if (route == null) return false
                        return route.contains("MediaRootRoute") ||
                                route.contains("MediaPickerRoute") ||
                                route == PLAYLIST_ROUTE ||
                                route == REMOTE_HOME_ROUTE
                    }

                    val isTopLevel = isTopLevelRoute(currentRoute)

                    CompositionLocalProvider( LocalBottomBarVisibility provides isBottomBarVisible) {
                        Scaffold(
                            bottomBar = {
                                AnimatedVisibility(
                                    visible = isTopLevel && isBottomBarVisible.value,
                                    enter = androidx.compose.animation.slideInVertically(
                                        animationSpec = tween(durationMillis = 300),
                                        initialOffsetY = { it }
                                    ) + androidx.compose.animation.expandVertically(
                                        animationSpec = tween(durationMillis = 300),
                                        expandFrom = Alignment.Top
                                    ),
                                    exit = androidx.compose.animation.fadeOut(
                                        animationSpec = tween(0)
                                    )
                                ) {
                                    val isMediaSelected = currentRoute?.contains("MediaRootRoute") == true || currentRoute?.contains("MediaPickerRoute") == true
                                    val isPlaylistSelected = currentRoute?.startsWith("playlist") == true
                                    val isWebDavSelected = currentRoute?.contains("remote_home") == true || currentRoute?.contains("webdav_") == true

                                    NavigationBar {
                                        NavigationBarItem(
                                            selected = isMediaSelected,
                                            onClick = {
                                                val currentEntry = mainNavController.currentBackStackEntry
                                                if (currentEntry?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) != true) return@NavigationBarItem

                                                if (isMediaSelected) {
                                                    mainNavController.navigate(MediaRootRoute) {
                                                        popUpTo(mainNavController.graph.findStartDestination().id) { inclusive = false }
                                                        launchSingleTop = true
                                                    }
                                                } else {
                                                    mainNavController.navigate(MediaRootRoute) {
                                                        popUpTo(mainNavController.graph.findStartDestination().id) { saveState = true }
                                                        launchSingleTop = true
                                                        restoreState = true
                                                    }
                                                }
                                            },
                                            icon = { Icon(NextIcons.Movie, contentDescription = "Media") },
                                            label = { Text("Media") },
                                            colors = NavigationBarItemDefaults.colors(
                                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                            )
                                        )
                                        VerticalDivider(
                                            modifier = Modifier
                                                .height(48.dp)
                                                .align(Alignment.CenterVertically),
                                            color = MaterialTheme.colorScheme.outlineVariant,
                                            thickness = 2.dp,
                                        )
                                        NavigationBarItem(
                                            selected = isPlaylistSelected,
                                            onClick = {
                                                val currentEntry = mainNavController.currentBackStackEntry
                                                if (currentEntry?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) != true) return@NavigationBarItem

                                                if (isPlaylistSelected) {
                                                    mainNavController.navigate("playlist?selectedUris=") {
                                                        popUpTo(PLAYLIST_ROUTE) { inclusive = false }
                                                        launchSingleTop = true
                                                    }
                                                } else {
                                                    mainNavController.navigate("playlist?selectedUris=") {
                                                        popUpTo(mainNavController.graph.findStartDestination().id) { saveState = true }
                                                        launchSingleTop = true
                                                        restoreState = true
                                                    }
                                                }
                                            },
                                            icon = { Icon(NextIcons.Bookmarks, contentDescription = "Playlist") },
                                            label = { Text("Playlist") },
                                            colors = NavigationBarItemDefaults.colors(
                                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                            )
                                        )
                                        VerticalDivider(
                                            modifier = Modifier
                                                .height(48.dp)
                                                .align(Alignment.CenterVertically),
                                            color = MaterialTheme.colorScheme.outlineVariant,
                                            thickness = 2.dp,
                                        )
                                        NavigationBarItem(
                                            selected = isWebDavSelected,
                                            onClick = {
                                                val currentEntry = mainNavController.currentBackStackEntry
                                                if (currentEntry?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) != true) return@NavigationBarItem

                                                if (isWebDavSelected) {
                                                    mainNavController.navigate(REMOTE_HOME_ROUTE) {
                                                        popUpTo(REMOTE_HOME_ROUTE) { inclusive = false }
                                                        launchSingleTop = true
                                                    }
                                                } else {
                                                    mainNavController.navigate(REMOTE_HOME_ROUTE) {
                                                        popUpTo(mainNavController.graph.findStartDestination().id) { saveState = true }
                                                        launchSingleTop = true
                                                        restoreState = true
                                                    }
                                                }
                                            },
                                            icon = { Icon(NextIcons.Storage, contentDescription = "WebDAV") },
                                            label = { Text("WebDAV") },
                                            colors = NavigationBarItemDefaults.colors(
                                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                            )
                                        )
                                    }
                                }
                            },
                        ) { paddingValues ->

                            NavHost(
                                navController = mainNavController,
                                startDestination = MediaRootRoute,
                                modifier = Modifier.padding(bottom = paddingValues.calculateBottomPadding()),
                                enterTransition = {
                                    if (isTopLevelRoute(initialState.destination.route) && isTopLevelRoute(targetState.destination.route)) {
                                        androidx.compose.animation.EnterTransition.None
                                    } else {
                                        slideIntoContainer(
                                            towards = AnimatedContentTransitionScope.SlideDirection.Start,
                                            animationSpec = tween(
                                                durationMillis = 200,
                                                easing = LinearEasing,
                                            ),
                                        )
                                    }
                                },
                                exitTransition = {
                                    if (isTopLevelRoute(initialState.destination.route) && isTopLevelRoute(targetState.destination.route)) {
                                        androidx.compose.animation.ExitTransition.None
                                    } else {
                                        slideOutOfContainer(
                                            towards = AnimatedContentTransitionScope.SlideDirection.Start,
                                            animationSpec = tween(
                                                durationMillis = 200,
                                                easing = LinearEasing,
                                            ),
                                            targetOffset = { fullOffset -> (fullOffset * 0.3f).toInt() },
                                        )
                                    }
                                },
                                popEnterTransition = {
                                    if (isTopLevelRoute(initialState.destination.route) && isTopLevelRoute(targetState.destination.route)) {
                                        androidx.compose.animation.EnterTransition.None
                                    } else {
                                        slideIntoContainer(
                                            towards = AnimatedContentTransitionScope.SlideDirection.End,
                                            animationSpec = tween(
                                                durationMillis = 200,
                                                easing = LinearEasing,
                                            ),
                                            initialOffset = { fullOffset -> (fullOffset * 0.3f).toInt() },
                                        )
                                    }
                                },
                                popExitTransition = {
                                    if (isTopLevelRoute(initialState.destination.route) && isTopLevelRoute(targetState.destination.route)) {
                                        androidx.compose.animation.ExitTransition.None
                                    } else {
                                        slideOutOfContainer(
                                            towards = AnimatedContentTransitionScope.SlideDirection.End,
                                            animationSpec = tween(
                                                durationMillis = 200,
                                                easing = LinearEasing,
                                            ),
                                        )
                                    }
                                },
                            ) {
                                mediaNavGraph(
                                    context = this@MainActivity,
                                    navController = mainNavController,
                                )
                                settingsNavGraph(navController = mainNavController)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Returns `true` if dark theme should be used, as a function of the [uiState] and the
 * current system context.
 */
@Composable
fun shouldUseDarkTheme(
    uiState: MainActivityUiState,
): Boolean = when (uiState) {
    MainActivityUiState.Loading -> isSystemInDarkTheme()
    is MainActivityUiState.Success -> when (uiState.preferences.themeConfig) {
        ThemeConfig.SYSTEM -> isSystemInDarkTheme()
        ThemeConfig.OFF -> false
        ThemeConfig.ON -> true
    }
}

@Composable
fun shouldUseHighContrastDarkTheme(
    uiState: MainActivityUiState,
): Boolean = when (uiState) {
    MainActivityUiState.Loading -> false
    is MainActivityUiState.Success -> uiState.preferences.useHighContrastDarkTheme
}

/**
 * Returns `true` if the dynamic color is disabled, as a function of the [uiState].
 */
@Composable
fun shouldUseDynamicTheming(
    uiState: MainActivityUiState,
): Boolean = when (uiState) {
    MainActivityUiState.Loading -> false
    is MainActivityUiState.Success -> uiState.preferences.useDynamicColors
}
