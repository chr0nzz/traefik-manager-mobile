import SwiftUI

enum MiddlewareFilter: String, CaseIterable, Identifiable {
    case all, http, tcp

    var id: String { rawValue }
    var label: String { self == .all ? String(localized: "All") : rawValue.uppercased() }
}

/// Middleware list with search and protocol filter. Port of `MiddlewaresScreen`.
struct MiddlewaresView: View {
    @Environment(AppModel.self) private var app
    @State private var query = ""
    @State private var filter: MiddlewareFilter = .all
    @State private var message: String?
    @State private var creating = DebugLaunch.form == "new-mw"
    @State private var showTemplates = false
    @State private var debugDetail: String? = DebugLaunch.route

    private var store: RoutesStore { app.routes }
    private var middlewares: [MiddlewareDef] { store.snapshot?.middlewares ?? [] }

    private var usage: [String: Int] { MiddlewareUsage.counts(routes: store.routes, middlewares: middlewares) }

    private var visible: [MiddlewareDef] {
        let needle = query.trimmed.lowercased()
        return middlewares.filter { middleware in
            let matchesProtocol = filter == .all || middleware.proto.rawValue == filter.rawValue
            let matchesQuery = needle.isEmpty || middleware.name.lowercased().contains(needle) || middleware.yaml.lowercased().contains(needle)
            return matchesProtocol && matchesQuery
        }
        .sorted { $0.name.lowercased() < $1.name.lowercased() }
    }

    var body: some View {
        content
            .background(Palette.bg)
            .navigationTitle("Middleware")
            .searchable(text: $query, prompt: Text("Search middleware"))
            .toolbar {
                ToolbarItemGroup(placement: .primaryAction) {
                    Menu {
                        Picker("Protocol", selection: $filter) {
                            ForEach(MiddlewareFilter.allCases) { Text($0.label).tag($0) }
                        }
                        Divider()
                        Button { showTemplates = true } label: { Label("Middleware templates", systemImage: "doc.on.doc") }
                    } label: {
                        Image(systemName: filter == .all ? "line.3.horizontal.decrease.circle" : "line.3.horizontal.decrease.circle.fill")
                    }
                    .accessibilityLabel("Filters")
                    Button { creating = true } label: { Image(systemName: "plus") }
                        .accessibilityLabel("Add middleware")
                }
            }
            .navigationDestination(for: String.self) { name in
                MiddlewareDetailView(name: name, usage: usage[name] ?? 0, message: $message)
            }
            .navigationDestination(item: $debugDetail) { name in
                MiddlewareDetailView(name: name, usage: usage[name] ?? 0, message: $message)
            }
            .sheet(isPresented: $creating) {
                NavigationStack {
                    MiddlewareFormView(name: nil) { message = String(localized: "\($0) saved") }
                }
            }
            .sheet(isPresented: $showTemplates) {
                NavigationStack { MiddlewareTemplatesView() }
            }
            .task(id: app.generation) { await app.refreshRoutes() }
            .refreshable { await app.refreshRoutes() }
            .toast($message)
    }

    @ViewBuilder
    private var content: some View {
        if store.snapshot == nil && store.loading {
            ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
        } else if store.snapshot == nil, let error = store.error {
            ContentUnavailableView {
                Label("Could not load middlewares", systemImage: "exclamationmark.triangle")
            } description: {
                Text(error)
            } actions: {
                Button("Retry") { Task { await app.refreshRoutes() } }.buttonStyle(.borderedProminent)
            }
        } else if visible.isEmpty {
            ContentUnavailableView {
                Label(middlewares.isEmpty ? "No middlewares" : "No middlewares match", systemImage: "square.3.layers.3d")
            } description: {
                Text(middlewares.isEmpty ? "Middlewares you create appear here." : "Try a different search or filter.")
            }
        } else {
            List(visible, id: \.name) { middleware in
                ZStack {
                    NavigationLink(value: middleware.name) { EmptyView() }.opacity(0)
                    MiddlewareCard(middleware: middleware, usage: usage[middleware.name] ?? 0)
                }
                .listRowBackground(Color.clear)
                .listRowSeparator(.hidden)
                .listRowInsets(EdgeInsets(top: Spacing.xs, leading: Spacing.lg, bottom: Spacing.xs, trailing: Spacing.lg))
            }
            .listStyle(.plain)
            .scrollContentBackground(.hidden)
        }
    }
}

/// One middleware row. Port of `MiddlewareCard`.
struct MiddlewareCard: View {
    let middleware: MiddlewareDef
    let usage: Int

    var body: some View {
        let kind = middleware.kind
        Card(spacing: Spacing.xs) {
            HStack(spacing: Spacing.sm) {
                Image(systemName: MiddlewareTemplates.icon(for: kind))
                    .font(.system(size: 14))
                    .foregroundStyle(Palette.muted)
                    .frame(width: 28, height: 28)
                    .background(Palette.bg, in: RoundedRectangle(cornerRadius: Radius.sm))
                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: Spacing.xs) {
                        if middleware.proto == .tcp {
                            Text(verbatim: "TCP").font(.caption2.weight(.bold)).foregroundStyle(Palette.green)
                        }
                        Text(middleware.name).font(.subheadline.weight(.semibold)).lineLimit(1)
                    }
                    Text(kind).font(.caption.monospaced()).foregroundStyle(Palette.purple)
                }
                Spacer(minLength: 0)
            }
            Divider().overlay(Palette.border).padding(.top, Spacing.xs)
            HStack {
                Text(usage == 0 ? String(localized: "unused") : String(localized: "used by \(usage) routes"))
                    .font(.caption2)
                    .foregroundStyle(usage == 0 ? Palette.muted : Palette.text)
                Spacer()
                if !middleware.configFile.isEmpty {
                    Text(middleware.configFile.replacingOccurrences(of: #"\.ya?ml$"#, with: "", options: .regularExpression))
                        .font(.caption2.monospaced())
                        .foregroundStyle(Palette.muted)
                }
            }
        }
        .opacity(usage == 0 ? 0.75 : 1)
        .accessibilityElement(children: .combine)
    }
}
