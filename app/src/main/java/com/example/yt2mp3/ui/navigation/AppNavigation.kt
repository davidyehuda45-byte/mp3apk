package com.example.yt2mp3.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.yt2mp3.ui.downloads.DownloadsScreen
import com.example.yt2mp3.ui.history.HistoryScreen
import com.example.yt2mp3.ui.home.HomeScreen
import com.example.yt2mp3.ui.player.PlayerScreen
import com.example.yt2mp3.ui.settings.SettingsScreen
import com.example.yt2mp3.ui.theme.AppMotion
import com.example.yt2mp3.ui.theme.rememberReducedMotion

sealed class Route(val path: String, val label: String, val icon: ImageVector) {
    data object Home : Route("home", "Home", Icons.Filled.Home)
    data object Downloads : Route("downloads", "Downloads", Icons.Filled.Download)
    data object History : Route("history", "History", Icons.Filled.History)
    data object Settings : Route("settings", "Settings", Icons.Filled.Settings)
    data object Player : Route("player/{id}", "Player", Icons.Filled.Home)
}

private fun goTab(nav: NavHostController, tab: Route) {
    nav.navigate(tab.path) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Subtle tab transitions: short fade + small slide, 150–250ms (§10, §28). */
private fun AnimatedContentTransitionScope<*>.tabEnter(reduced: Boolean): EnterTransition =
    if (reduced) fadeIn(tween(1))
    else fadeIn(tween(AppMotion.NORMAL_MS, easing = AppMotion.Standard)) +
        slideIntoContainer(
            AnimatedContentTransitionScope.SlideDirection.Up,
            tween(AppMotion.NORMAL_MS, easing = AppMotion.Emphasized),
            initialOffset = { it / 12 }, // small vertical movement only
        )

private fun tabExit(reduced: Boolean): ExitTransition =
    if (reduced) fadeOut(tween(1))
    else fadeOut(tween(AppMotion.FAST_MS, easing = AppMotion.Standard))

@Composable
fun AppNavigation(sharedUrl: String?, onSharedUrlConsumed: () -> Unit) {
    val nav = rememberNavController()
    val reduced = rememberReducedMotion()
    val context = androidx.compose.ui.platform.LocalContext.current
    var locked by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Boolean?>(null) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        val app = context.applicationContext as com.example.yt2mp3.Yt2Mp3App
        val enabled = runCatching { app.settings.getLockEnabled() }.getOrDefault(false)
        val hash = runCatching { app.settings.getPinHash() }.getOrDefault("")
        locked = enabled && hash.isNotEmpty()
    }
    if (locked == null) return
    if (locked == true) {
        com.example.yt2mp3.ui.lock.LockScreen(onUnlocked = { locked = false })
        return
    }
    val tabs = listOf(Route.Home, Route.Downloads, Route.History, Route.Settings)
    Scaffold(
        bottomBar = {
            NavigationBar {
                val entry by nav.currentBackStackEntryAsState()
                val dest = entry?.destination
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = dest?.hierarchy?.any { it.route == tab.path } == true,
                        onClick = { goTab(nav, tab) },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { inner ->
        NavHost(
            nav,
            startDestination = Route.Home.path,
            Modifier.padding(inner),
            enterTransition = { tabEnter(reduced) },
            exitTransition = { tabExit(reduced) },
            popEnterTransition = { tabEnter(reduced) },
            popExitTransition = { tabExit(reduced) },
        ) {
            composable(Route.Home.path) {
                HomeScreen(sharedUrl = sharedUrl, onSharedUrlConsumed = onSharedUrlConsumed)
            }
            composable(Route.Downloads.path) {
                DownloadsScreen(onStartDownloading = { goTab(nav, Route.Home) })
            }
            composable(Route.History.path) {
                HistoryScreen(
                    onStartDownloading = { goTab(nav, Route.Home) },
                    onPlay = { id -> nav.navigate("player/$id") },
                )
            }
            composable(Route.Settings.path) { SettingsScreen() }
            composable(
                Route.Player.path,
                arguments = listOf(androidx.navigation.NavType.LongType.let {
                    androidx.navigation.navArgument("id") { type = it }
                }),
            ) { entry ->
                PlayerScreen(
                    itemId = entry.arguments?.getLong("id") ?: -1L,
                    onBack = { nav.popBackStack() },
                )
            }
        }
    }
}
