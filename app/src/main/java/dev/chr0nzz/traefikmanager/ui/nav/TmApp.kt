package dev.chr0nzz.traefikmanager.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
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
import dev.chr0nzz.traefikmanager.ui.components.ProvideTopBarMenu
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItem
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
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
import androidx.navigation.NavType
import androidx.navigation.compose.rememberNavController
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.navigation.navArgument
import android.net.Uri
import dev.chr0nzz.traefikmanager.data.api.ApiState
import dev.chr0nzz.traefikmanager.ui.components.LoadingState
import dev.chr0nzz.traefikmanager.ui.components.PlaceholderScreen
import dev.chr0nzz.traefikmanager.ui.connect.ConnectScreen
import dev.chr0nzz.traefikmanager.ui.certs.CertificatesScreen
import dev.chr0nzz.traefikmanager.ui.backups.BackupsScreen
import dev.chr0nzz.traefikmanager.ui.crowdsec.CrowdSecScreen
import dev.chr0nzz.traefikmanager.ui.dashboard.DashboardScreen
import dev.chr0nzz.traefikmanager.ui.logs.LogsScreen
import dev.chr0nzz.traefikmanager.ui.plugins.PluginsScreen
import dev.chr0nzz.traefikmanager.ui.middlewares.MiddlewareFormScreen
import dev.chr0nzz.traefikmanager.ui.middlewares.MiddlewareTemplatesScreen
import dev.chr0nzz.traefikmanager.ui.middlewares.MiddlewaresScreen
import dev.chr0nzz.traefikmanager.ui.routes.RouteFormScreen
import dev.chr0nzz.traefikmanager.ui.routes.RouteRawScreen
import dev.chr0nzz.traefikmanager.ui.routes.RoutesScreen
import dev.chr0nzz.traefikmanager.ui.settings.AppearanceScreen
import dev.chr0nzz.traefikmanager.ui.settings.ConnectionSettingsScreen
import dev.chr0nzz.traefikmanager.ui.settings.SettingsRoutes
import dev.chr0nzz.traefikmanager.ui.settings.AboutScreen
import dev.chr0nzz.traefikmanager.ui.settings.AuthSettingsScreen
import dev.chr0nzz.traefikmanager.ui.settings.DiagnosticsScreen
import dev.chr0nzz.traefikmanager.ui.settings.AgentConfigScreen
import dev.chr0nzz.traefikmanager.ui.settings.NotificationHistoryScreen
import dev.chr0nzz.traefikmanager.ui.settings.NotificationsScreen
import dev.chr0nzz.traefikmanager.ui.settings.ServersScreen
import dev.chr0nzz.traefikmanager.ui.settings.SettingsScreen
import dev.chr0nzz.traefikmanager.ui.routemap.RouteMapScreen
import dev.chr0nzz.traefikmanager.ui.services.ServicesScreen
import kotlinx.coroutines.launch

private const val ROUTE_FORM = "route_form"
private const val ROUTE_RAW = "route_raw"
private const val MW_FORM = "middleware_form"
private const val MW_TEMPLATES = "middleware_templates"

@Composable
fun TmApp(
    apiState: ApiState,
    migrationNotice: String? = null,
    onNoticeShown: () -> Unit = {},
    widgetDestination: String? = null,
    widgetServerId: String? = null,
    onWidgetTargetHandled: () -> Unit = {},
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(migrationNotice) {
        val notice = migrationNotice ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message = notice, withDismissAction = true)
        onNoticeShown()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        when (apiState) {
            ApiState.Loading -> LoadingState()
            ApiState.Disconnected -> ConnectScreen()
            is ApiState.Ready -> ConnectedApp(
                widgetDestination = widgetDestination,
                widgetServerId = widgetServerId,
                onWidgetTargetHandled = onWidgetTargetHandled,
            )
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .safeDrawingPadding(),
        )
    }
}

@Composable
private fun ConnectedApp(
    widgetDestination: String? = null,
    widgetServerId: String? = null,
    onWidgetTargetHandled: () -> Unit = {},
    viewModel: RootViewModel = hiltViewModel(),
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val badges by viewModel.badges.collectAsStateWithLifecycle()
    val destinations by viewModel.destinations.collectAsStateWithLifecycle()
    val servers by viewModel.servers.collectAsStateWithLifecycle()
    val activeServer by viewModel.activeServer.collectAsStateWithLifecycle()
    val switchingServer by viewModel.switching.collectAsStateWithLifecycle()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle(RootViewModel.DefaultPreferences)
    var serverListExpanded by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val biometricAvailable = remember(context) { AppLock.available(context) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Health goes stale fast, so re-probe whenever the drawer is opened.
    LaunchedEffect(drawerState.isOpen) {
        if (drawerState.isOpen) viewModel.loadServers()
    }

    val goTo: (TmDestination, String?) -> Unit = { destination, query ->
        navController.navigate(destination.route + query?.let { "?$it" }.orEmpty()) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    // Ask for the layout the suite will actually use: a short window puts the items in a bar even
    // when it is wide, and a bar has room for neither nine destinations nor a count on each.
    val suiteType = NavigationSuiteScaffoldDefaults.navigationSuiteType(currentWindowAdaptiveInfo())
    val bar = suiteType == NavigationSuiteType.ShortNavigationBarCompact ||
        suiteType == NavigationSuiteType.ShortNavigationBarMedium ||
        suiteType == NavigationSuiteType.NavigationBar

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(drawerState = drawerState) {
                ServerSwitcherHeader(
                    servers = servers,
                    active = activeServer,
                    expanded = serverListExpanded,
                    switching = switchingServer,
                    onToggle = { serverListExpanded = !serverListExpanded },
                    onSelect = { id ->
                        serverListExpanded = false
                        viewModel.switchServer(id)
                        scope.launch { drawerState.close() }
                    },
                )
                // A server with every tab on overflows the sheet, so the list scrolls under the
                // switcher rather than running off the bottom.
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                TmSection.entries.filter { section -> destinations.any { it.section == section } }.forEach { section ->
                    Text(
                        text = section.label.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 12.dp, bottom = 6.dp),
                    )
                    destinations.filter { it.section == section }.forEach { destination ->
                    val badge = badges.forRoute(destination.route)
                    NavigationDrawerItem(
                        label = { Text(destination.label) },
                        icon = { Icon(destination.icon, contentDescription = null) },
                        badge = drawerBadge(badge),
                        selected = currentDestination?.hierarchy?.any { it.route?.substringBefore('?') == destination.route } == true,
                        onClick = {
                            scope.launch { drawerState.close() }
                            goTo(destination, null)
                        },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    }
                }
                }
            }
        },
    ) {

    // A bar keeps five slots. Your own picks win; otherwise primaries first, then whatever else
    // the server exposes.
    val suiteDestinations = if (bar) {
        val chosen = preferences.navItems
            .mapNotNull { route -> destinations.firstOrNull { it.route == route } }
        chosen.ifEmpty {
            val primary = destinations.filter { it.primary }
            (primary + destinations.filterNot { it.primary })
        }.take(5)
    } else {
        destinations
    }
    var navEditorOpen by remember { mutableStateOf(false) }
    val editorRequested by viewModel.navEditorOpen.collectAsStateWithLifecycle()
    LaunchedEffect(editorRequested) {
        if (editorRequested) {
            navEditorOpen = true
            viewModel.consumeNavEditor()
        }
    }

    if (navEditorOpen) {
        NavBarEditorSheet(
            available = destinations,
            chosen = suiteDestinations.map { it.route },
            onSave = { routes ->
                navEditorOpen = false
                scope.launch { viewModel.setNavItems(routes) }
            },
            onDismiss = { navEditorOpen = false },
        )
    }

    // A widget tap names both a page and the server it was watching, so the app lands on what the
    // widget was actually showing rather than on whatever was last selected.
    LaunchedEffect(widgetDestination, widgetServerId) {
        val route = widgetDestination ?: return@LaunchedEffect
        if (widgetServerId != null) viewModel.switchServer(widgetServerId.takeIf { it.isNotEmpty() })
        TmDestination.entries.firstOrNull { it.route == route }?.let { goTo(it, null) }
        onWidgetTargetHandled()
    }

    LaunchedEffect(destinations, currentDestination?.route) {
        val route = currentDestination?.route ?: return@LaunchedEffect
        val known = TmDestination.entries.firstOrNull { it.route == route } ?: return@LaunchedEffect
        if (known !in destinations) goTo(TmDestination.Home, null)
    }

    NavigationSuiteScaffold(
        containerColor = MaterialTheme.colorScheme.background,
        // Hiding the bar leaves the drawer as the way around, which is why it stays reachable
        // from every top bar.
        navigationSuiteType = if (preferences.hideNavBar && bar) NavigationSuiteType.None else suiteType,
        navigationItemVerticalArrangement = Arrangement.Center,
        navigationItems = {
            // The rail carries the drawer itself, so the top bars do not need a second one.
            if (!bar) {
                NavigationSuiteItem(
                    selected = false,
                    onClick = { scope.launch { drawerState.open() } },
                    icon = { Icon(Icons.Outlined.Menu, contentDescription = null) },
                    label = { Text("Menu") },
                    navigationSuiteType = suiteType,
                )
            }
            suiteDestinations.forEach { destination ->
                NavigationSuiteItem(
                    selected = currentDestination?.hierarchy?.any { it.route?.substringBefore('?') == destination.route } == true,
                    onClick = { goTo(destination, null) },
                    icon = { Icon(destination.icon, contentDescription = null) },
                    label = { Text(destination.label) },
                    navigationSuiteType = suiteType,
                    badge = railBadge(badges.forRoute(destination.route), counted = !bar),
                )
            }
        },
    ) {
        val fade = tween<Float>(durationMillis = 200)
        ProvideTopBarMenu(bar) {
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
                    onOpenRoutes = { status, proto ->
                        val query = listOfNotNull(
                            status?.let { "status=$it" },
                            proto?.let { "proto=$it" },
                        ).joinToString("&")
                        goTo(TmDestination.Routes, query.ifEmpty { null })
                    },
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onOpenNotifications = { navController.navigate(SettingsRoutes.NOTIFICATION_HISTORY) },
                    onOpenServices = { status ->
                        goTo(TmDestination.Services, status?.let { "status=$it" })
                    },
                    onOpenMiddlewares = { goTo(TmDestination.Middlewares, null) },
                )
            }
            composable(
                route = "${TmDestination.Routes.route}?status={status}&proto={proto}&id={id}",
                arguments = listOf(
                    navArgument("status") { nullable = true; defaultValue = null },
                    navArgument("proto") { nullable = true; defaultValue = null },
                    navArgument("id") { nullable = true; defaultValue = null },
                ),
            ) { entry ->
                RoutesScreen(
                    initialStatus = entry.arguments?.getString("status"),
                    initialProto = entry.arguments?.getString("proto"),
                    initialRouteId = entry.arguments?.getString("id"),
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onCreateRoute = { navController.navigate(ROUTE_FORM) },
                    onEditRoute = { routeId ->
                        navController.navigate("$ROUTE_FORM?routeId=${Uri.encode(routeId)}")
                    },
                    onEditYaml = { routeId ->
                        navController.navigate("$ROUTE_RAW/${Uri.encode(routeId)}")
                    },
                )
            }
            composable(
                route = "$ROUTE_FORM?routeId={routeId}",
                arguments = listOf(
                    navArgument("routeId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) { entry ->
                RouteFormScreen(
                    routeId = entry.arguments?.getString("routeId"),
                    onClose = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() },
                )
            }
            composable(
                route = "$ROUTE_RAW/{routeId}",
                arguments = listOf(navArgument("routeId") { type = NavType.StringType }),
            ) { entry ->
                RouteRawScreen(
                    routeId = entry.arguments?.getString("routeId").orEmpty(),
                    onClose = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() },
                )
            }
            composable(TmDestination.Middlewares.route) {
                MiddlewaresScreen(
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onCreate = { navController.navigate(MW_FORM) },
                    onEdit = { name -> navController.navigate("$MW_FORM?name=${Uri.encode(name)}") },
                    onOpenTemplates = { navController.navigate(MW_TEMPLATES) },
                )
            }
            composable(
                route = "$MW_FORM?name={name}",
                arguments = listOf(
                    navArgument("name") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) { entry ->
                MiddlewareFormScreen(
                    middlewareName = entry.arguments?.getString("name"),
                    onClose = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() },
                )
            }
            composable(MW_TEMPLATES) {
                MiddlewareTemplatesScreen(onClose = { navController.popBackStack() })
            }
            composable(
                route = "${TmDestination.Services.route}?status={status}",
                arguments = listOf(navArgument("status") { nullable = true; defaultValue = null }),
            ) { entry ->
                ServicesScreen(
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    initialStatus = entry.arguments?.getString("status"),
                )
            }
            composable(TmDestination.Certificates.route) {
                CertificatesScreen(
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                )
            }
            composable(TmDestination.Plugins.route) {
                PluginsScreen(
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                )
            }
            composable(TmDestination.RouteMap.route) {
                RouteMapScreen(onOpenDrawer = { scope.launch { drawerState.open() } })
            }
            composable(TmDestination.Logs.route) {
                LogsScreen(
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                )
            }
            composable(TmDestination.Settings.route) {
                SettingsScreen(
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onOpen = { route -> navController.navigate(route) },
                    onDisconnect = { viewModel.disconnect() },
                    activeServerName = activeServer?.name ?: "Host",
                    hostSelected = activeServer?.isHost ?: true,
                )
            }
            composable(SettingsRoutes.DIAGNOSTICS) {
                DiagnosticsScreen(onClose = { navController.popBackStack() })
            }
            composable(SettingsRoutes.ABOUT) {
                AboutScreen(onClose = { navController.popBackStack() })
            }
            composable(SettingsRoutes.AUTH) {
                AuthSettingsScreen(onClose = { navController.popBackStack() })
            }
            composable(SettingsRoutes.NOTIFICATION_HISTORY) {
                NotificationHistoryScreen(onClose = { navController.popBackStack() })
            }
            composable(SettingsRoutes.NOTIFICATIONS) {
                NotificationsScreen(onClose = { navController.popBackStack() })
            }
            composable(SettingsRoutes.AGENTS) {
                ServersScreen(
                    onClose = { navController.popBackStack() },
                    onConfigure = { agentId -> navController.navigate(SettingsRoutes.agentConfig(agentId)) },
                )
            }
            composable(
                route = SettingsRoutes.AGENT_CONFIG,
                arguments = listOf(navArgument("agentId") { type = NavType.StringType }),
            ) {
                AgentConfigScreen(onClose = { navController.popBackStack() })
            }
            composable(SettingsRoutes.CONNECTION) {
                ConnectionSettingsScreen(onClose = { navController.popBackStack() })
            }
            composable(SettingsRoutes.APPEARANCE) {
                AppearanceScreen(
                    onClose = { navController.popBackStack() },
                    biometricAvailable = biometricAvailable,
                )
            }
            composable(TmDestination.Backups.route) {
                BackupsScreen(onOpenDrawer = { scope.launch { drawerState.open() } })
            }
            composable(TmDestination.CrowdSec.route) {
                CrowdSecScreen(
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                )
            }
        }
        }
    }
    }
}

private fun badgeLabel(value: Int): String = if (value > 999) "999+" else value.toString()

/**
 * The drawer prints the total the way the web sidebar does, as a quiet trailing number, and
 * swaps in a filled badge when that entry has something wrong with it.
 */
private fun drawerBadge(badge: NavBadge): (@Composable () -> Unit)? = when {
    badge.alerts > 0 -> {
        {
            Badge(modifier = Modifier.semantics { contentDescription = alertLabel(badge.alerts) }) {
                Text(badgeLabel(badge.alerts))
            }
        }
    }
    badge.count > 0 -> {
        {
            Text(
                text = badgeLabel(badge.count),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { contentDescription = "${badge.count} total" },
            )
        }
    }
    else -> null
}

/**
 * The rail has room for a number, the bottom bar does not: only alerts survive the compact
 * layout, and totals ride a neutral badge so a big count never reads as a big problem.
 */
private fun railBadge(badge: NavBadge, counted: Boolean): (@Composable () -> Unit)? = when {
    badge.alerts > 0 -> {
        {
            Badge(modifier = Modifier.semantics { contentDescription = alertLabel(badge.alerts) }) {
                Text(badgeLabel(badge.alerts))
            }
        }
    }
    counted && badge.count > 0 -> {
        {
            Badge(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.semantics { contentDescription = "${badge.count} total" },
            ) {
                Text(badgeLabel(badge.count))
            }
        }
    }
    else -> null
}

private fun alertLabel(alerts: Int): String = if (alerts == 1) "1 alert" else "$alerts alerts"
