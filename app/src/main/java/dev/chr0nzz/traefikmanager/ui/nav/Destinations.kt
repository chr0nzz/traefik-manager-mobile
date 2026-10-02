package dev.chr0nzz.traefikmanager.ui.nav

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.AltRoute
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.BubbleChart
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.ContactPage
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Traffic
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.material.icons.outlined.Workspaces
import androidx.compose.ui.graphics.vector.ImageVector
import dev.chr0nzz.traefikmanager.R
import dev.chr0nzz.traefikmanager.data.model.ProviderPage
import dev.chr0nzz.traefikmanager.data.model.UiText

enum class TmSection(@StringRes val label: Int) {
    Traffic(R.string.nav_section_traffic),
    Observability(R.string.nav_section_observability),
    Infrastructure(R.string.nav_section_infrastructure),
    Providers(R.string.nav_section_providers),
    System(R.string.nav_section_system),
}

enum class TmDestination(
    val route: String,
    val label: UiText,
    val icon: ImageVector,
    val section: TmSection,
    val primary: Boolean = false,
    val serverTab: String? = null,
    val provider: ProviderPage? = null,
) {
    Home("home", UiText.of(R.string.nav_dashboard), Icons.Outlined.Home, TmSection.Traffic, primary = true),
    Routes("routes", UiText.of(R.string.nav_routes), Icons.Outlined.AltRoute, TmSection.Traffic, primary = true),
    Middlewares("middlewares", UiText.of(R.string.nav_middlewares), Icons.Outlined.Layers, TmSection.Traffic, primary = true),
    Services("services", UiText.of(R.string.nav_services), Icons.Outlined.Dns, TmSection.Traffic),
    RouteMap(
        route = "routemap",
        label = UiText.of(R.string.nav_route_map),
        icon = Icons.Outlined.AccountTree,
        section = TmSection.Traffic,
        serverTab = "routemap",
    ),
    Logs(
        route = "logs",
        label = UiText.of(R.string.nav_logs),
        icon = Icons.AutoMirrored.Outlined.ReceiptLong,
        section = TmSection.Observability,
        primary = true,
        serverTab = "logs",
    ),
    CrowdSec(
        route = "crowdsec",
        label = UiText.Raw("CrowdSec"),
        icon = Icons.Outlined.Shield,
        section = TmSection.Observability,
        primary = true,
        serverTab = "crowdsec",
    ),
    Certificates(
        route = "certificates",
        label = UiText.of(R.string.nav_certificates),
        icon = Icons.Outlined.VerifiedUser,
        section = TmSection.Infrastructure,
        serverTab = "certs",
    ),
    Plugins(
        route = "plugins",
        label = UiText.of(R.string.nav_plugins),
        icon = Icons.Outlined.Extension,
        section = TmSection.Infrastructure,
        serverTab = "plugins",
    ),
    Docker(ProviderPage.Docker, Icons.Outlined.ViewInAr),
    Kubernetes(ProviderPage.Kubernetes, Icons.Outlined.Hub),
    Swarm(ProviderPage.Swarm, Icons.Outlined.BubbleChart),
    Nomad(ProviderPage.Nomad, Icons.Outlined.Workspaces),
    Ecs(ProviderPage.Ecs, Icons.Outlined.Cloud),
    ConsulCatalog(ProviderPage.ConsulCatalog, Icons.Outlined.ContactPage),
    Redis(ProviderPage.Redis, Icons.Outlined.Storage),
    Etcd(ProviderPage.Etcd, Icons.Outlined.Storage),
    Consul(ProviderPage.Consul, Icons.Outlined.Storage),
    ZooKeeper(ProviderPage.ZooKeeper, Icons.Outlined.Storage),
    HttpProvider(ProviderPage.HttpProvider, Icons.Outlined.Link),
    FileExternal(ProviderPage.FileExternal, Icons.Outlined.Description),
    Internal(ProviderPage.Internal, Icons.Outlined.Traffic),
    Backups(
        route = "backups",
        label = UiText.of(R.string.nav_backups),
        icon = Icons.Outlined.Inventory2,
        section = TmSection.System,
    ),
    Settings("settings", UiText.of(R.string.nav_settings), Icons.Outlined.Settings, TmSection.System),
    ;

    constructor(page: ProviderPage, icon: ImageVector) : this(
        route = page.route,
        label = UiText.Raw(page.label),
        icon = icon,
        section = TmSection.Providers,
        serverTab = page.tab,
        provider = page,
    )
}
