import SwiftUI

struct RootView: View {
    @Environment(AppModel.self) private var app

    var body: some View {
        Group {
            if app.connection == nil {
                ConnectView()
            } else if DebugLaunch.form == "widgets" {
                #if DEBUG
                NavigationStack { WidgetGallery() }
                #endif
            } else {
                MainShell()
                    .task(id: app.connection) { await app.refresh() }
                    // Editors live here, above the shells, so they stay open when the shell swaps.
                    .shellSheetHost()
            }
        }
        .animation(.default, value: app.connection == nil)
    }
}

/// Tab bar on compact widths (iPhone, folded), sidebar on regular widths (iPad, unfolded, landscape Max).
/// Both shells read and write `AppModel.selection`, so switching between them keeps the current screen.
struct MainShell: View {
    @Environment(\.horizontalSizeClass) private var sizeClass
    @Environment(AppModel.self) private var app

    var body: some View {
        if sizeClass == .regular {
            SidebarShell()
        } else {
            TabShell(initial: app.selection)
        }
    }
}

// MARK: - Compact

private struct TabShell: View {
    private static let moreTag = "more"

    @Environment(AppModel.self) private var app
    @State private var tab: String
    @State private var morePath: [Destination]

    /// A screen that lives under More opens More with that screen pushed.
    init(initial: Destination) {
        _tab = State(initialValue: initial.primary ? initial.id : Self.moreTag)
        _morePath = State(initialValue: initial.primary ? [] : [initial])
    }

    var body: some View {
        let destinations = app.destinations
        let primary = destinations.filter(\.primary)
        let badges = app.navBadges

        TabView(selection: $tab) {
            ForEach(primary) { destination in
                NavigationStack {
                    DestinationView(destination: destination)
                        .toolbar { ToolbarItem(placement: .topBarLeading) { ServerSwitcher(compact: true) } }
                }
                .id("\(destination.id)-\(app.generation)")
                .tabItem { Label(destination.label, systemImage: destination.systemImage) }
                .badge(tabBadge(badges[destination]))
                .tag(destination.id)
            }

            NavigationStack(path: $morePath) {
                MoreList(destinations: destinations.filter { !$0.primary })
                    .toolbar { ToolbarItem(placement: .topBarLeading) { ServerSwitcher(compact: true) } }
            }
            .id("more-\(app.generation)")
            .tabItem { Label("More", systemImage: "ellipsis.circle") }
            .tag(Self.moreTag)
        }
        .onChange(of: tab) { publishSelection() }
        .onChange(of: morePath) { publishSelection() }
        .onChange(of: destinations) {
            if tab != Self.moreTag && !primary.contains(where: { $0.id == tab }) {
                tab = Destination.home.id
            }
            morePath.removeAll { !destinations.contains($0) }
        }
    }

    /// On the More root nothing new is chosen, so the last screen stays selected for the sidebar.
    private func publishSelection() {
        if tab == Self.moreTag {
            if let last = morePath.last { app.selection = last }
        } else if let destination = Destination(rawValue: tab) {
            app.selection = destination
        }
    }
}

private struct MoreList: View {
    let destinations: [Destination]

    var body: some View {
        List {
            ForEach(DestinationSection.allCases) { section in
                let items = destinations.filter { $0.section == section }
                if !items.isEmpty {
                    Section(section.label) {
                        ForEach(items) { destination in
                            NavigationLink(value: destination) {
                                Label(destination.label, systemImage: destination.systemImage)
                            }
                        }
                    }
                }
            }
        }
        .navigationTitle("More")
        .navigationDestination(for: Destination.self) { DestinationView(destination: $0) }
    }
}

// MARK: - Regular

private struct SidebarShell: View {
    @Environment(AppModel.self) private var app

    var body: some View {
        let destinations = app.destinations
        let badges = app.navBadges
        let selection = Binding<Destination?>(
            get: { app.selection },
            set: { if let destination = $0 { app.selection = destination } }
        )

        NavigationSplitView {
            List(selection: selection) {
                Section {
                    ServerSwitcher()
                }
                ForEach(DestinationSection.allCases) { section in
                    let items = destinations.filter { $0.section == section }
                    if !items.isEmpty {
                        Section(section.label) {
                            ForEach(items) { destination in
                                Label(destination.label, systemImage: destination.systemImage)
                                    .badge(sidebarBadge(badges[destination]))
                                    .tag(destination)
                            }
                        }
                    }
                }
            }
            .navigationTitle("Traefik Manager")
            .navigationSplitViewColumnWidth(min: 240, ideal: 280)
        } detail: {
            NavigationStack {
                DestinationView(destination: app.selection)
            }
            .id("\(app.selection.id)-\(app.generation)")
        }
        .onChange(of: destinations) {
            if !destinations.contains(app.selection) { app.selection = .home }
        }
    }
}

private func sidebarBadge(_ badge: NavBadge?) -> Text? {
    guard let badge else { return nil }
    if badge.alerts > 0 { return Text(NavBadge.label(badge.alerts)).foregroundStyle(Palette.red) }
    if badge.count > 0 { return Text(NavBadge.label(badge.count)) }
    return nil
}

private func tabBadge(_ badge: NavBadge?) -> Text? {
    guard let badge, badge.alerts > 0 else { return nil }
    return Text(NavBadge.label(badge.alerts))
}

// MARK: - Routing

struct DestinationView: View {
    let destination: Destination

    var body: some View {
        switch destination {
        case .home: HomeView()
        case .routes: RoutesView()
        case .middlewares: MiddlewaresView()
        case .services: ServicesView()
        case .logs: LogsView()
        case .routeMap: RouteMapView()
        case .crowdsec: CrowdSecView()
        case .certificates: CertificatesView()
        case .plugins: PluginsView()
        case .backups: BackupsView()
        case .settings: SettingsView()
        default:
            if destination.isProvider {
                ProviderView(page: destination)
            } else {
                ComingSoonView(destination: destination)
            }
        }
    }
}
