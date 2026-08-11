package dev.chr0nzz.traefikmanager.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
private fun ConnectedApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            TmDestination.entries.forEach { destination ->
                item(
                    selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true,
                    onClick = {
                        navController.navigate(destination.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(destination.icon, contentDescription = destination.label) },
                    label = { Text(destination.label) },
                )
            }
        },
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets.safeDrawing,
        ) { insets ->
            NavHost(
                navController = navController,
                startDestination = TmDestination.Home.route,
                modifier = Modifier.fillMaxSize(),
            ) {
                composable(TmDestination.Home.route) {
                    DashboardScreen(
                        contentPadding = insets,
                        onOpenRoutes = {
                            navController.navigate(TmDestination.Routes.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
                composable(TmDestination.Routes.route) {
                    RoutesScreen(contentPadding = insets)
                }
                composable(TmDestination.Middlewares.route) {
                    PlaceholderScreen(title = TmDestination.Middlewares.label, contentPadding = insets)
                }
                composable(TmDestination.Services.route) {
                    PlaceholderScreen(title = TmDestination.Services.label, contentPadding = insets)
                }
            }
        }
    }
}
