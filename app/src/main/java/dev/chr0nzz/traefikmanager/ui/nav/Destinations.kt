package dev.chr0nzz.traefikmanager.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AltRoute
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.ui.graphics.vector.ImageVector

enum class TmDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    Home("home", "Home", Icons.Outlined.Home),
    Routes("routes", "Routes", Icons.Outlined.AltRoute),
    Middlewares("middlewares", "Middleware", Icons.Outlined.Layers),
    Services("services", "Services", Icons.Outlined.Dns),
}
