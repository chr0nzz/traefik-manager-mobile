import SwiftUI

struct RootView: View {
    @Environment(AppModel.self) private var app

    var body: some View {
        Group {
            if app.connection == nil {
                ConnectView()
            } else {
                MainShell()
                    .task(id: app.connection) { await app.refresh() }
            }
        }
        .animation(.default, value: app.connection == nil)
    }
}

/// Tab bar on compact widths (iPhone, folded), sidebar on regular widths (iPad, unfolded, landscape Max).
struct MainShell: View {
    @Environment(\.horizontalSizeClass) private var sizeClass

    var body: some View {
        if sizeClass == .regular {
            SidebarShell()
        } else {
            TabShell()
        }
    }
}

// MARK: - Compact

private struct TabShell: View {
    private static let moreTag = "more"

    @Environment(AppModel.self) private var app
    @State private var selection = DebugLaunch.tab ?? Destination.home.id

    var body: some View {
        let destinations = app.destinations
        let primary = destinations.filter(\.primary)

        TabView(selection: $selection) {
            ForEach(primary) { destination in
                NavigationStack {
                    DestinationView(destination: destination)
                        .toolbar { ToolbarItem(placement: .topBarLeading) { ServerSwitcher(compact: true) } }
                }
                .id("\(destination.id)-\(app.generation)")
                .tabItem { Label(destination.label, systemImage: destination.systemImage) }
                .tag(destination.id)
            }

            NavigationStack {
                MoreList(destinations: destinations.filter { !$0.primary })
                    .toolbar { ToolbarItem(placement: .topBarLeading) { ServerSwitcher(compact: true) } }
            }
            .id("more-\(app.generation)")
            .tabItem { Label("More", systemImage: "ellipsis.circle") }
            .tag(Self.moreTag)
        }
        .onChange(of: destinations) {
            if selection != Self.moreTag && !primary.contains(where: { $0.id == selection }) {
                selection = Destination.home.id
            }
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
    @State private var selection: Destination? = DebugLaunch.tab.flatMap(Destination.init(rawValue:)) ?? .home

    var body: some View {
        let destinations = app.destinations

        NavigationSplitView {
            List(selection: $selection) {
                Section {
                    ServerSwitcher()
                }
                ForEach(DestinationSection.allCases) { section in
                    let items = destinations.filter { $0.section == section }
                    if !items.isEmpty {
                        Section(section.label) {
                            ForEach(items) { destination in
                                Label(destination.label, systemImage: destination.systemImage)
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
                DestinationView(destination: selection ?? .home)
            }
            .id("\(selection?.id ?? "home")-\(app.generation)")
        }
        .onChange(of: destinations) {
            if let selection, !destinations.contains(selection) { self.selection = .home }
        }
    }
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
