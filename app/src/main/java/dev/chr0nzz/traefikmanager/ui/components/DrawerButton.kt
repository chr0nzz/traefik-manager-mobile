package dev.chr0nzz.traefikmanager.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf

val LocalTopBarMenu = compositionLocalOf { true }

@Composable
fun ProvideTopBarMenu(show: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalTopBarMenu provides show, content = content)
}

@Composable
fun DrawerButton(onOpenDrawer: () -> Unit) {
    if (!LocalTopBarMenu.current) return
    IconButton(onClick = onOpenDrawer) {
        Icon(Icons.Outlined.Menu, contentDescription = "Open navigation menu")
    }
}
