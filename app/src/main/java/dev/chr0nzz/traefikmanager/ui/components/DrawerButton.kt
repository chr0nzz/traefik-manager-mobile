package dev.chr0nzz.traefikmanager.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf

/**
 * True while the top bar owns the drawer button. A wide window shows the navigation rail, which
 * carries its own, and two of them on one screen is one too many.
 */
val LocalTopBarMenu = compositionLocalOf { true }

@Composable
fun ProvideTopBarMenu(show: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalTopBarMenu provides show, content = content)
}

/** The drawer button, drawn only where it belongs. */
@Composable
fun DrawerButton(onOpenDrawer: () -> Unit) {
    if (!LocalTopBarMenu.current) return
    IconButton(onClick = onOpenDrawer) {
        Icon(Icons.Outlined.Menu, contentDescription = "Open navigation menu")
    }
}
