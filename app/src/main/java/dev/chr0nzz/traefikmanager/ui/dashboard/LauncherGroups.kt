package dev.chr0nzz.traefikmanager.ui.dashboard

import androidx.compose.material.icons.outlined.Apps
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import dev.chr0nzz.traefikmanager.R

private data class BuiltInGroup(@StringRes val label: Int, val icon: ImageVector)

private val BUILT_IN_GROUPS = mapOf(
    "Media" to BuiltInGroup(R.string.dashboard_group_media, Icons.Outlined.Movie),
    "Monitoring" to BuiltInGroup(R.string.dashboard_group_monitoring, Icons.Outlined.ShowChart),
    "Infrastructure" to BuiltInGroup(R.string.dashboard_group_infrastructure, Icons.Outlined.Build),
    "Security" to BuiltInGroup(R.string.dashboard_group_security, Icons.Outlined.Shield),
    "Home" to BuiltInGroup(R.string.dashboard_group_home, Icons.Outlined.Home),
    "Files & Data" to BuiltInGroup(R.string.dashboard_group_files, Icons.Outlined.Folder),
    "Network" to BuiltInGroup(R.string.dashboard_group_network, Icons.Outlined.Lan),
    "Dev" to BuiltInGroup(R.string.dashboard_group_dev, Icons.Outlined.Code),
    "Servers" to BuiltInGroup(R.string.dashboard_group_servers, Icons.Outlined.Dns),
    "Other" to BuiltInGroup(R.string.dashboard_group_other, Icons.Outlined.Apps),
)

fun builtInGroupIcon(name: String): ImageVector? = BUILT_IN_GROUPS[name]?.icon

@Composable
fun builtInGroupLabel(name: String): String = BUILT_IN_GROUPS[name]?.let { stringResource(it.label) } ?: name
