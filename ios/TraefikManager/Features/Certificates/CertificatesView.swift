import SwiftUI

enum CertFilter: CaseIterable, Identifiable {
    case all, unused, noResolver, expiring

    var id: Self { self }

    var label: String {
        switch self {
        case .all: String(localized: "All")
        case .unused: String(localized: "Unused")
        case .noResolver: String(localized: "No resolver")
        case .expiring: String(localized: "Expiring")
        }
    }
}

/// TLS certificates with expiry, usage and removal. Port of `CertificatesScreen`.
struct CertificatesView: View {
    @Environment(AppModel.self) private var app
    @State private var certs: [CertRow] = []
    @State private var usage: CertUsage?
    @State private var manage = CertManageState()
    @State private var loading = true
    @State private var loadError: String?
    @State private var serverError: String?
    @State private var query = ""
    @State private var filter: CertFilter = .all
    @State private var pendingRemoval: [CertRow] = []
    @State private var confirming = false
    @State private var removing = false
    @State private var message: String?

    private func verdict(_ cert: CertRow) -> CertVerdict? { usage?.verdict(cert.resolver, cert.main) }

    private func removable(_ cert: CertRow) -> Bool {
        manage.available && !cert.main.isEmpty && !cert.resolver.isEmpty && cert.resolver != "file"
    }

    private var visible: [CertRow] {
        let needle = query.lowercased()
        return certs.filter { cert in
            let hit = needle.isEmpty || cert.main.lowercased().contains(needle) || cert.extraDomains.contains { $0.lowercased().contains(needle) }
            let matches: Bool = switch filter {
            case .all: true
            case .unused: verdict(cert)?.unused == true
            case .noResolver: verdict(cert)?.orphaned == true
            case .expiring: cert.health == .critical || cert.health == .expiring
            }
            return hit && matches
        }
    }

    private var unusedRemovable: [CertRow] { certs.filter { verdict($0)?.unused == true && removable($0) } }

    var body: some View {
        content
            .background(Palette.bg)
            .navigationTitle("Certificates")
            .searchable(text: $query, prompt: Text("Search certificates"))
            .toolbar {
                ToolbarItemGroup(placement: .primaryAction) {
                    Menu {
                        Picker("Show", selection: $filter) {
                            ForEach(CertFilter.allCases) { Text($0.label).tag($0) }
                        }
                        if !unusedRemovable.isEmpty {
                            Button(role: .destructive) { ask(unusedRemovable) } label: {
                                Label(String(localized: "Remove \(unusedRemovable.count) unused"), systemImage: "trash")
                            }
                        }
                    } label: {
                        Image(systemName: filter == .all ? "line.3.horizontal.decrease.circle" : "line.3.horizontal.decrease.circle.fill")
                    }
                    .accessibilityLabel("Filters")
                    Button { Task { await load() } } label: { Image(systemName: "arrow.clockwise") }
                        .accessibilityLabel("Refresh certificates")
                }
            }
            .typedConfirm(isPresented: $confirming, title: removalTitle, message: removalMessage, actionLabel: String(localized: "Remove")) {
                remove(pendingRemoval)
            }
            .task(id: app.generation) {
                certs = []
                loading = true
                await load()
            }
            .refreshable { await load() }
            .toast($message)
    }

    @ViewBuilder
    private var content: some View {
        if loading && certs.isEmpty {
            ProgressView("Loading certificates").frame(maxWidth: .infinity, maxHeight: .infinity)
        } else if let loadError, certs.isEmpty {
            ContentUnavailableView {
                Label("Could not load certificate data", systemImage: "exclamationmark.triangle")
            } description: {
                Text(loadError)
            } actions: {
                Button("Retry") { Task { await load() } }.buttonStyle(.borderedProminent)
            }
        } else if certs.isEmpty {
            ContentUnavailableView {
                Label(serverError == nil ? "No certificates found" : "No certificates", systemImage: "checkmark.seal")
            } description: {
                Text(serverError ?? String(localized: "acme.json may be empty - certs are issued on first request."))
            }
        } else {
            List {
                Group {
                    verdictCard
                    if visible.isEmpty {
                        Text(query.isEmpty ? "No certificates match" : "No certificates match your search")
                            .font(.footnote)
                            .foregroundStyle(Palette.muted)
                    }
                    ForEach(visible) { cert in
                        CertCard(cert: cert, verdict: verdict(cert), removable: removable(cert)) { ask([cert]) } onCopy: {
                            UIPasteboard.general.string = ([cert.main] + cert.extraDomains).filter { !$0.isEmpty }.joined(separator: "\n")
                            message = String(localized: "Copied")
                        }
                    }
                }
                .listRowBackground(Color.clear)
                .listRowSeparator(.hidden)
                .listRowInsets(EdgeInsets(top: Spacing.xs, leading: Spacing.lg, bottom: Spacing.xs, trailing: Spacing.lg))
            }
            .listStyle(.plain)
            .scrollContentBackground(.hidden)
            .overlay { if removing { ProgressView().padding().background(.regularMaterial, in: RoundedRectangle(cornerRadius: 12)) } }
        }
    }

    private var verdictCard: some View {
        let days = certs.compactMap(\.daysLeft)
        let expired = days.filter { $0 <= 0 }.count
        let critical = days.filter { (1...6).contains($0) }.count
        let expiring = days.filter { (7...29).contains($0) }.count
        let next = days.filter { $0 > 0 }.min()
        let resolvers = Set(certs.map(\.resolver).filter { !$0.isEmpty }).count
        let unused = usage?.certs.filter(\.unused).count ?? 0
        let orphaned = usage?.certs.filter(\.orphaned).count ?? 0
        let headline = expired > 0 ? String(localized: "\(expired) certificates have expired")
            : critical > 0 ? String(localized: "\(critical) expiring within 7 days")
            : expiring > 0 ? String(localized: "\(expiring) expiring within 30 days")
            : String(localized: "All certificates healthy")
        var detail = [String(localized: "\(certs.count) certificates")]
        if expired > 0 { detail.append(String(localized: "\(expired) expired")) }
        if critical > 0 { detail.append(String(localized: "\(critical) under 7d")) }
        if expiring > 0 { detail.append(String(localized: "\(expiring) under 30d")) }
        if expired == 0 && critical == 0 && expiring == 0 { detail.append(String(localized: "none expiring soon")) }
        if resolvers > 1 { detail.append(String(localized: "\(resolvers) resolvers")) }
        if unused > 0 { detail.append(String(localized: "\(unused) unused")) }
        if orphaned > 0 { detail.append(String(localized: "\(orphaned) no resolver")) }
        if let next { detail.append(String(localized: "next expiry in \(next)d")) }
        let status: TMStatus = expired > 0 || critical > 0 ? .error : expiring > 0 ? .warn : .ok
        var note = [String(localized: "TLS certificates are managed automatically by Traefik's ACME resolver.")]
        if manage.available {
            note.append(String(localized: "Removing one restarts Traefik so it lets go of it."))
        } else if !manage.reason.isBlank {
            note.append(String(localized: "Removal is off: \(manage.reasonText)"))
        } else {
            note.append(String(localized: "Renew or revoke them via your Traefik config."))
        }
        if let why = usage?.why, !why.isBlank { note.append(why) }
        return Card(accent: status.color, spacing: 2) {
            HStack(spacing: Spacing.sm) {
                StatusDot(status: status)
                Text(headline).font(.subheadline.weight(.semibold))
            }
            Text(detail.joined(separator: " · ")).font(.footnote).foregroundStyle(Palette.muted)
            Text(note.joined(separator: " ")).font(.caption).foregroundStyle(Palette.muted).padding(.top, Spacing.xs)
        }
    }

    private var removalTitle: String {
        pendingRemoval.count == 1 ? String(localized: "Remove \(pendingRemoval.first?.main ?? "")?") : String(localized: "Remove \(pendingRemoval.count) certificates?")
    }

    private var removalMessage: String {
        let inUse = pendingRemoval.filter { verdict($0)?.unused != true }.count
        return [
            String(localized: "Traefik is restarted afterwards, because it only reads acme.json at startup."),
            inUse > 0 ? String(localized: "\(inUse) still serve routes, so Traefik requests new certificates for them.") : nil,
            String(localized: "A copy of acme.json is saved to Backups first."),
        ].compactMap { $0 }.joined(separator: " ")
    }

    private func ask(_ rows: [CertRow]) {
        pendingRemoval = rows
        confirming = true
    }

    private func load() async {
        guard let service = app.service else { return }
        loadError = nil
        async let fetched = service.certs()
        async let manageState = try? service.certManage()
        async let usageState = try? service.certUsage(exclude: [])
        do {
            let response = try await fetched
            certs = CertRows.from(response.certs)
            serverError = response.error.flatMap { $0.isBlank ? nil : $0 }
        } catch {
            loadError = error.localizedDescription
        }
        manage = await manageState ?? CertManageState(reason: CertManageState.tooOld)
        usage = await usageState ?? usage
        loading = false
    }

    private func remove(_ rows: [CertRow]) {
        guard let service = app.service, !rows.isEmpty else { return }
        removing = true
        Task {
            do {
                let response = try await service.deleteCerts(rows.map { CertRef(resolver: $0.resolver, main: $0.main) })
                message = response.outcome(asked: rows.count)
            } catch {
                message = error.localizedDescription
            }
            removing = false
            await load()
        }
    }
}

private struct CertCard: View {
    let cert: CertRow
    let verdict: CertVerdict?
    let removable: Bool
    let onRemove: () -> Void
    let onCopy: () -> Void

    @State private var expanded = false

    var body: some View {
        Card(accent: cert.health == .healthy ? nil : cert.status.color, spacing: Spacing.xs) {
            HStack(spacing: Spacing.sm) {
                StatusDot(status: cert.status)
                Text(cert.title).font(.subheadline.weight(.semibold)).lineLimit(1)
                Spacer(minLength: 0)
                if let label = cert.daysLeftLabel {
                    Text(label).font(.caption2.monospaced().weight(.bold)).foregroundStyle(cert.status.color)
                }
            }
            HStack(spacing: Spacing.xs) {
                Text(cert.resolver.ifEmpty("-")).font(.caption2.monospaced()).foregroundStyle(Palette.purple)
                if verdict?.unused == true { badge(String(localized: "unused"), Palette.yellow) }
                if verdict?.orphaned == true { badge(String(localized: "no resolver"), Palette.red) }
            }
            if !cert.extraDomains.isEmpty {
                let shown = expanded ? cert.extraDomains : Array(cert.extraDomains.prefix(2))
                ForEach(shown, id: \.self) { domain in
                    Text(domain).font(.caption2.monospaced()).foregroundStyle(Palette.muted)
                }
                if cert.extraDomains.count > 2 {
                    Button(expanded ? String(localized: "Show less") : String(localized: "+\(cert.extraDomains.count - 2) more")) {
                        withAnimation { expanded.toggle() }
                    }
                    .font(.caption2)
                    .buttonStyle(.plain)
                    .foregroundStyle(Palette.blue)
                }
            }
            Divider().overlay(Palette.border).padding(.top, Spacing.xs)
            HStack {
                let parts = [
                    cert.expiresOn.map { String(localized: "expires \($0.formatted(date: .abbreviated, time: .omitted))") } ?? String(localized: "expiry unknown"),
                    cert.domainCount > 1 ? String(localized: "\(cert.domainCount) domains") : nil,
                    cert.origin,
                ].compactMap { $0 }
                Text(parts.joined(separator: " · ")).font(.caption2).foregroundStyle(Palette.muted).lineLimit(1)
                Spacer()
                Button(action: onCopy) { Image(systemName: "doc.on.doc") }
                    .accessibilityLabel("Copy domains")
                if removable {
                    Button(action: onRemove) { Image(systemName: "trash").foregroundStyle(Palette.red) }
                        .accessibilityLabel("Remove certificate")
                }
            }
            .buttonStyle(.borderless)
            .font(.system(size: 13))
            .foregroundStyle(Palette.muted)
        }
        .accessibilityElement(children: .contain)
    }

    private func badge(_ text: String, _ color: Color) -> some View {
        Text(text)
            .font(.caption2)
            .foregroundStyle(color)
            .padding(.horizontal, 5)
            .padding(.vertical, 1)
            .background(color.opacity(0.12), in: Capsule())
    }
}
