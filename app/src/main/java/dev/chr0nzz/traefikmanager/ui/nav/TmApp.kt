package dev.chr0nzz.traefikmanager.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.chr0nzz.traefikmanager.data.api.ApiState
import dev.chr0nzz.traefikmanager.ui.components.LoadingState
import dev.chr0nzz.traefikmanager.ui.components.PlaceholderScreen
import dev.chr0nzz.traefikmanager.ui.connect.ConnectScreen
import dev.chr0nzz.traefikmanager.ui.dashboard.DashboardScreen
import dev.chr0nzz.traefikmanager.ui.routes.RoutesScreen
import kotlinx.coroutines.launch

@Composable
fun TmApp(apiState: ApiState) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        when (apiState) {
            ApiState.Loading -> LoadingState()
            ApiState.Disconnected -> ConnectScreen()
            is ApiState.Ready -> ConnectedApp()
        }
    }
}

@Composable
private fun ConnectedApp(viewModel: RootViewModel = hiltViewModel()) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val badges by viewModel.badges.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val goTo: (TmDestination) -> Unit = { destination ->
        navController.navigate(destination.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(drawerState = drawerState) {
                Text(
                    text = "Traefik Manager",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 18.dp),
                )
                TmDestination.entries.forEach { destination ->
                    val alerts = badges.forRoute(destination.route)
                    NavigationDrawerItem(
                        label = { Text(destination.label) },
                        icon = { Icon(destination.icon, contentDescription = null) },
                        badge = if (alerts > 0) {
                            { Text(if (alerts > 99) "99+" else alerts.toString()) }
                        } else {
                            null
                        },
                        selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true,
                        onClick = {
                            scope.launch { drawerState.close() }
                            goTo(destination)
                        },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
            }
        },
    ) {

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            TmDestination.entries.forEach { destination ->
                val alerts = badges.forRoute(destination.route)
                item(
                    selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true,
                    onClick = { goTo(destination) },
                    icon = { Icon(destination.icon, contentDescription = null) },
                    label = { Text(destination.label) },
                    badge = if (alerts > 0) {
                        {
                            val plural = if (alerts == 1) "1 alert" else "$alerts alerts"
                            Badge(
                                modifier = Modifier.semantics { contentDescription = plural },
                            ) { Text(if (alerts > 99) "99+" else alerts.toString()) }
                        }
                    } else {
                        null
                    },
                )
            }
        },
    ) {
        val fade = tween<Float>(durationMillis = 200)
        NavHost(
            navController = navController,
            startDestination = TmDestination.Home.route,
            modifier = Modifier.fillMaxSize(),
            enterTransition = { fadeIn(fade) + scaleIn(initialScale = 0.94f, animationSpec = fade) },
            exitTransition = { fadeOut(fade) },
            popEnterTransition = { fadeIn(fade) + scaleIn(initialScale = 0.94f, animationSpec = fade) },
            popExitTransition = { fadeOut(fade) },
        ) {
            composable(TmDestination.Home.route) {
                DashboardScreen(
                    onOpenRoutes = { goTo(TmDestination.Routes) },
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                )
            }
            composable(TmDestination.Routes.route) {
                RoutesScreen(onOpenDrawer = { scope.launch { drawerState.open() } })
            }
            composable(TmDestination.Middlewares.route) {
                PlaceholderScreen(title = TmDestination.Middlewares.label)
            }
            composable(TmDestination.Services.route) {
                PlaceholderScreen(title = TmDestination.Services.label)
            }
        }
    }
    }
}
