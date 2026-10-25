import SwiftUI

enum BackupsTab: String, CaseIterable, Identifiable {
    case dynamic, `static`, certs, git

    var id: Self { self }

    var label: String {
        switch self {
        case .dynamic: String(localized: "Dynamic")
        case .static: String(localized: "Static")
        case .certs: String(localized: "Certificates")
        case .git: "Git"
        }
    }
}

/// Local backups (dynamic, static, acme.json) and Git backup history. Port of `BackupsScreen`.
struct BackupsView: View {
    @Environment(AppModel.self) private var app
    @State private var tab: BackupsTab = DebugLaunch.form == "git" ? .git : .dynamic
    @State private var list: BackupsList?
    @State private var loading = true
    @State private var error: String?
    @State private var gitLoading = true
    @State private var gitStatus: GitStatus?
    @State private var gitCommits: [GitCommit] = []
    @State private var gitError: String?
    @State private var busy = false
    @State private var restartPending: BackupKind?
    @State private var restoreTarget: BackupEntry?
    @State private var askRestore = false
    @State private var askRestoreCommit = false
    @State private var deleteTarget: BackupEntry?
    @State private var restoreCommitTarget: GitCommit?
    @State private var diffFor: GitCommit?
    @State private var pushOpen = false
    @State private var pushMessage = ""
    @State private var message: String?

    private var onHost: Bool { app.activeAgentId == nil }
    private var hostBacked: Bool { app.activeServer.agent?.gitHostBackup ?? false }
    private var staticConfigured: Bool { list?.staticConfigured ?? true }
    private var tabs: [BackupsTab] { BackupsTab.allCases.filter { $0 != .static || staticConfigured } }

    private func rows(_ tab: BackupsTab) -> [BackupEntry] {
        let entries = list?.entries ?? []
        switch tab {
        case .static: return entries.filter { $0.kind == .static }
        case .certs: return entries.filter { $0.kind == .certs }
        default: return entries.filter { $0.kind == .routes }
        }
    }

    var body: some View {
        VStack(spacing: 0) {
            Picker("Backup type", selection: $tab) {
                ForEach(tabs) { Text($0.label).tag($0) }
            }
            .pickerStyle(.segmented)
            .padding(.horizontal, Spacing.lg)
            .padding(.vertical, Spacing.xs)

            if let kind = restartPending {
                RestartNotice(
                    title: kind == .certs ? String(localized: "Traefik is still serving the old certificates") : String(localized: "Traefik is still running the old static config"),
                    detail: kind == .certs ? String(localized: "A restored acme.json only takes effect after a restart.") : String(localized: "A restored static config only takes effect after a restart."),
                    busy: busy,
                    onRestart: restart,
                    onDismiss: { restartPending = nil }
                )
                .fixedSize(horizontal: false, vertical: true)
                .padding(.horizontal, Spacing.lg)
                .padding(.vertical, Spacing.xs)
            }

            if tab == .git { gitPane } else { localPane }
        }
        .background(Palette.bg)
        .navigationTitle("Backups")
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button { Task { await refresh() } } label: { Image(systemName: "arrow.clockwise") }
                    .accessibilityLabel("Refresh backups")
            }
        }
        .typedConfirm(isPresented: $askRestore,
                      title: String(localized: "Restore \(restoreTarget?.name ?? "")?"),
                      message: restoreConsequence(restoreTarget?.kind ?? .routes),
                      actionLabel: String(localized: "Restore")) {
            if let entry = restoreTarget { restore(entry) }
        }
        .typedConfirm(isPresented: $askRestoreCommit,
                      title: String(localized: "Restore \(restoreCommitTarget?.shaShort ?? "")?"),
                      message: String(localized: "This writes every config file from that commit over what is running now."),
                      actionLabel: String(localized: "Restore")) {
            if let commit = restoreCommitTarget { restoreCommit(commit) }
        }
        .alert("Delete this backup?", isPresented: Binding(get: { deleteTarget != nil }, set: { if !$0 { deleteTarget = nil } }), presenting: deleteTarget) { entry in
            Button("Delete", role: .destructive) { delete(entry) }
            Button("Cancel", role: .cancel) {}
        } message: { entry in
            Text(entry.name)
        }
        .alert("Push to Git", isPresented: $pushOpen) {
            TextField("Message (optional)", text: $pushMessage)
            Button("Push") { push(pushMessage) }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("Commits every config file as it stands now. A blank message uses the server's own wording.")
        }
        .sheet(item: $diffFor) { commit in
            NavigationStack { GitDiffView(commit: commit, hostBacked: hostBacked) }
        }
        .task(id: app.generation) {
            list = nil
            gitStatus = nil
            gitCommits = []
            restartPending = nil
            loading = true
            gitLoading = true
            await refresh()
            if let sha = DebugLaunch.route, tab == .git { diffFor = gitCommits.first { $0.shaShort == sha } }
        }
        .onChange(of: staticConfigured) { _, configured in
            if !configured && tab == .static { tab = .dynamic }
        }
        .toast($message)
    }

    // MARK: Local backups

    @ViewBuilder
    private var localPane: some View {
        let rows = rows(tab)
        let isStatic = tab == .static
        let isCerts = tab == .certs
        if loading && list == nil {
            ProgressView("Loading backups").frame(maxWidth: .infinity, maxHeight: .infinity)
        } else if let error, list == nil {
            ContentUnavailableView {
                Label("Could not read the backups", systemImage: "exclamationmark.triangle")
            } description: {
                Text(error)
            } actions: {
                Button("Retry") { Task { await refresh() } }.buttonStyle(.borderedProminent)
            }
        } else {
            List {
                Group {
                    Card(spacing: Spacing.xs) {
                        HStack {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(rows.isEmpty ? String(localized: "No backups") : String(localized: "\(rows.count) kept"))
                                    .font(.subheadline.weight(.semibold))
                                Text(([ByteSize.label(rows.reduce(0) { $0 + $1.size })] + (rows.first.map { [String(localized: "newest \($0.stamp)")] } ?? []))
                                    .joined(separator: " · "))
                                    .font(.caption)
                                    .foregroundStyle(Palette.muted)
                            }
                            Spacer()
                            if !isCerts && (!isStatic || onHost) {
                                Button { create(static: isStatic) } label: { Label("Back up", systemImage: "plus") }
                                    .buttonStyle(.borderedProminent)
                                    .disabled(busy)
                            }
                        }
                        if isCerts {
                            note(String(localized: "Taken automatically before a certificate is removed from acme.json."))
                        }
                        if isStatic && !onHost {
                            note(String(localized: "This agent folds its static config into the ordinary backup, so use Back up on the Dynamic tab."))
                        }
                        if list?.kindKnown == false {
                            note(String(localized: "This agent is too old to say which backup is which, so everything is listed as dynamic."))
                        }
                    }

                    if rows.isEmpty {
                        ContentUnavailableView {
                            Label(isStatic ? "No static backups" : isCerts ? "No certificate backups" : "No backups yet", systemImage: "archivebox")
                        } description: {
                            Text(isStatic ? "Backing up the static config keeps a copy of entrypoints and resolvers."
                                 : isCerts ? "A copy of acme.json is kept each time a certificate is removed."
                                 : "A backup copies every dynamic config file the server holds.")
                        }
                    } else {
                        SectionLabel(text: isStatic ? String(localized: "Static config") : isCerts ? String(localized: "Certificate stores") : String(localized: "Dynamic config"))
                            .padding(.top, Spacing.xs)
                        ForEach(rows) { entry in
                            BackupCard(entry: entry, busy: busy) { restoreTarget = entry; askRestore = true } onDelete: { deleteTarget = entry }
                        }
                    }
                }
                .listRowBackground(Color.clear)
                .listRowSeparator(.hidden)
                .listRowInsets(EdgeInsets(top: Spacing.xs, leading: Spacing.lg, bottom: Spacing.xs, trailing: Spacing.lg))
            }
            .listStyle(.plain)
            .scrollContentBackground(.hidden)
            .refreshable { await refresh() }
        }
    }

    private func note(_ text: String) -> some View {
        Text(text).font(.caption).foregroundStyle(Palette.muted)
    }

    private func restoreConsequence(_ kind: BackupKind) -> String {
        switch kind {
        case .static: String(localized: "This overwrites the live static config. The server backs up what is there now first, and Traefik has to restart before it takes effect.")
        case .certs: String(localized: "This replaces every certificate in acme.json. The server backs up what is there now first, then restarts Traefik because it only reads acme.json at startup.")
        case .routes: String(localized: "This overwrites the live config, replacing every route it holds. The server backs up what is there now first.")
        }
    }

    // MARK: Git

    @ViewBuilder
    private var gitPane: some View {
        if gitLoading && gitStatus == nil {
            ProgressView("Loading Git backup").frame(maxWidth: .infinity, maxHeight: .infinity)
        } else if let gitError, gitStatus == nil {
            ContentUnavailableView {
                Label("Could not read Git backup", systemImage: "exclamationmark.triangle")
            } description: {
                Text(gitError)
            } actions: {
                Button("Retry") { Task { await refresh() } }.buttonStyle(.borderedProminent)
            }
        } else if let status = gitStatus, status.configured {
            List {
                Group {
                    Card(accent: status.enabled ? Palette.green : Palette.yellow, spacing: Spacing.xs) {
                        HStack {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(status.enabled ? "Git backup on" : "Configured, switched off").font(.subheadline.weight(.semibold))
                                Text(gitSummary(status)).font(.caption.monospaced()).foregroundStyle(Palette.muted)
                            }
                            Spacer()
                            Button {
                                pushMessage = ""
                                pushOpen = true
                            } label: { Label("Push", systemImage: "arrow.up.circle") }
                                .buttonStyle(.borderedProminent)
                                .disabled(busy)
                        }
                        if !status.enabled {
                            note(String(localized: "Automatic pushes are off. Pushing here still works."))
                        }
                    }
                    if gitCommits.isEmpty {
                        ContentUnavailableView("No commits yet", systemImage: "clock.arrow.circlepath", description: Text("Push once and the history shows up here."))
                    } else {
                        SectionLabel(text: String(localized: "History \(gitCommits.count)")).padding(.top, Spacing.xs)
                        ForEach(gitCommits) { commit in
                            CommitCard(commit: commit, busy: busy) { diffFor = commit } onRestore: { restoreCommitTarget = commit; askRestoreCommit = true }
                        }
                    }
                }
                .listRowBackground(Color.clear)
                .listRowSeparator(.hidden)
                .listRowInsets(EdgeInsets(top: Spacing.xs, leading: Spacing.lg, bottom: Spacing.xs, trailing: Spacing.lg))
            }
            .listStyle(.plain)
            .scrollContentBackground(.hidden)
            .refreshable { await refresh() }
        } else {
            ContentUnavailableView(
                "Git backup is not configured",
                systemImage: "arrow.triangle.branch",
                description: Text("The repository, branch and token are set in the web UI or by environment. Once a repo is configured, pushes and restores appear here.")
            )
        }
    }

    private func gitSummary(_ status: GitStatus) -> String {
        var parts: [String] = []
        if !status.branch.isEmpty { parts.append(String(localized: "branch \(status.branch)")) }
        parts.append(status.lastSha.isEmpty ? String(localized: "nothing pushed yet") : status.lastSha)
        if !status.lastPush.isEmpty && !status.lastSha.isEmpty { parts.append(status.lastPush) }
        return parts.joined(separator: " · ")
    }

    // MARK: Loading and actions

    private func refresh() async {
        async let local: Void = loadLocal()
        async let git: Void = loadGit()
        _ = await (local, git)
    }

    private func loadLocal() async {
        guard let service = app.service else { return }
        error = nil
        do {
            list = try await service.backups()
        } catch {
            self.error = error.localizedDescription
        }
        loading = false
    }

    private func loadGit() async {
        guard let service = app.service else { return }
        gitError = nil
        do {
            let status = try await service.gitStatus(hostBacked: hostBacked)
            gitCommits = status.configured ? try await service.gitCommits(hostBacked: hostBacked) : []
            gitStatus = status
        } catch {
            gitError = error.localizedDescription
        }
        gitLoading = false
    }

    /// Runs one mutation at a time, toasts its outcome, then reloads both panes. Port of `BackupsViewModel.run`.
    private func run(_ failure: String, _ block: @escaping (TMService) async throws -> String) {
        guard !busy, let service = app.service else { return }
        busy = true
        Task {
            do {
                message = try await block(service)
            } catch {
                let text = error.localizedDescription.trimmed
                message = text.isEmpty || text.hasPrefix("<") || text.count > 200 ? failure : text
            }
            busy = false
            await refresh()
        }
    }

    private func create(static isStatic: Bool) {
        if isStatic {
            run(String(localized: "Could not back up the static config")) { service in
                let response = try await service.createStaticBackup()
                guard response.worked else { throw APIError.server(response.error ?? "") }
                return String(localized: "Static config backed up")
            }
        } else {
            run(String(localized: "Could not create the backup")) { service in
                let response = try await service.createBackup()
                guard response.worked else { throw APIError.server(response.error ?? "") }
                let created = response.created
                if created.isEmpty { return String(localized: "Backup created") }
                if created.count == 1 { return String(localized: "Backed up \(created[0])") }
                return String(localized: "Backed up \(created.count) files")
            }
        }
    }

    private func delete(_ entry: BackupEntry) {
        run(String(localized: "Could not delete the backup")) { service in
            let response = try await service.deleteBackup(entry.name)
            guard response.worked else { throw APIError.server(response.error ?? response.message ?? "") }
            return String(localized: "Deleted \(entry.name)")
        }
    }

    private func restore(_ entry: BackupEntry) {
        run(String(localized: "Could not restore the backup")) { service in
            let response = try await service.restoreBackup(entry.name)
            guard response.worked else { throw APIError.server(response.error ?? "") }
            await app.refreshRoutes()
            switch entry.kind {
            case .static:
                restartPending = .static
                return String(localized: "Static config restored - Traefik needs a restart")
            case .routes:
                return String(localized: "Restored \(entry.name)")
            case .certs:
                if response.restarted == true { return String(localized: "Certificates restored and Traefik restarted") }
                restartPending = .certs
                return response.restartError.isBlank
                    ? String(localized: "Certificates restored, but Traefik did not restart")
                    : String(localized: "Certificates restored, but Traefik did not restart: \(response.restartError)")
            }
        }
    }

    private func restart() {
        run(String(localized: "Restart failed")) { service in
            try await service.restartTraefik()
            restartPending = nil
            return String(localized: "Traefik restarted")
        }
    }

    private func push(_ text: String) {
        run(String(localized: "Could not push to Git")) { service in
            let response = try await service.gitPush(text, hostBacked: hostBacked)
            guard response.worked else { throw APIError.server(response.error ?? response.message ?? "") }
            return String(localized: "Pushed to Git")
        }
    }

    private func restoreCommit(_ commit: GitCommit) {
        run(String(localized: "Could not restore that commit")) { service in
            let response = try await service.gitRestore(commit.sha, hostBacked: hostBacked)
            guard response.worked else { throw APIError.server(response.error ?? response.message ?? "") }
            await app.refreshRoutes()
            return String(localized: "Restored \(commit.shaShort)")
        }
    }
}

private struct BackupCard: View {
    let entry: BackupEntry
    let busy: Bool
    let onRestore: () -> Void
    let onDelete: () -> Void

    var body: some View {
        Card(spacing: Spacing.xs) {
            Text(entry.name).font(.footnote.monospaced().weight(.semibold)).lineLimit(2)
            Text("\(entry.stamp) · \(ByteSize.label(entry.size))").font(.caption2).foregroundStyle(Palette.muted)
            Divider().overlay(Palette.border).padding(.vertical, 2)
            HStack(spacing: Spacing.sm) {
                Button(action: onRestore) { Label("Restore", systemImage: "clock.arrow.circlepath") }
                    .buttonStyle(.bordered)
                Button(role: .destructive, action: onDelete) { Label("Delete", systemImage: "trash") }
                    .buttonStyle(.borderless)
                    .tint(Palette.red)
            }
            .font(.footnote)
            .disabled(busy)
        }
    }
}

private struct CommitCard: View {
    let commit: GitCommit
    let busy: Bool
    let onDiff: () -> Void
    let onRestore: () -> Void

    var body: some View {
        Card(spacing: Spacing.xs) {
            Text(commit.message).font(.subheadline.weight(.semibold)).lineLimit(3)
            Text("\(commit.shaShort) · \(commit.timestamp)").font(.caption2.monospaced()).foregroundStyle(Palette.muted)
            Divider().overlay(Palette.border).padding(.vertical, 2)
            HStack(spacing: Spacing.sm) {
                Button(action: onDiff) { Label("Changes", systemImage: "plusminus") }
                    .buttonStyle(.bordered)
                Button(action: onRestore) { Label("Restore", systemImage: "clock.arrow.circlepath") }
                    .buttonStyle(.borderless)
                    .disabled(busy)
            }
            .font(.footnote)
        }
    }
}

/// Files changed in one commit, each expandable to a line diff. Port of `DiffDialog`.
struct GitDiffView: View {
    let commit: GitCommit
    let hostBacked: Bool

    @Environment(AppModel.self) private var app
    @Environment(\.dismiss) private var dismiss
    @State private var diff: GitDiff?
    @State private var loading = true
    @State private var error: String?
    @State private var expanded: Set<String> = []

    var body: some View {
        Group {
            if loading {
                ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if let error {
                ContentUnavailableView("Could not read that commit", systemImage: "exclamationmark.triangle", description: Text(error))
            } else if let diff, !(diff.stat.isEmpty && diff.files.isEmpty) {
                ScrollView {
                    VStack(alignment: .leading, spacing: Spacing.md) {
                        Text(commit.message).font(.subheadline.weight(.semibold))
                        if !diff.stat.isBlank {
                            Text(diff.stat.trimmed)
                                .font(.caption2.monospaced())
                                .foregroundStyle(Palette.muted)
                                .textSelection(.enabled)
                        }
                        ForEach(diff.files, id: \.filename) { file in
                            fileCard(file)
                        }
                    }
                    .padding(Spacing.lg)
                }
            } else {
                ContentUnavailableView("Nothing to show for this commit.", systemImage: "doc.text.magnifyingglass")
            }
        }
        .background(Palette.bg)
        .navigationTitle(commit.shaShort)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Close") { dismiss() } } }
        .task {
            guard let service = app.service else { return }
            do {
                diff = try await service.gitDiff(commit.sha, hostBacked: hostBacked)
                if let first = diff?.files.first, diff?.files.count == 1 { expanded.insert(first.filename) }
            } catch {
                self.error = error.localizedDescription
            }
            loading = false
        }
    }

    private func color(_ status: String) -> Color {
        switch status.first {
        case "A": Palette.green
        case "D": Palette.red
        default: Palette.yellow
        }
    }

    private func fileCard(_ file: GitDiffFile) -> some View {
        let open = expanded.contains(file.filename)
        let hasBody = !(file.old.isEmpty && file.new.isEmpty)
        return Card(spacing: Spacing.xs) {
            Button {
                withAnimation { if open { expanded.remove(file.filename) } else { expanded.insert(file.filename) } }
            } label: {
                HStack {
                    Text(file.statusWord).font(.caption.monospaced().weight(.bold)).foregroundStyle(color(file.status))
                    Text(file.filename).font(.caption.monospaced()).foregroundStyle(Palette.text).lineLimit(2)
                    Spacer()
                    if hasBody { Image(systemName: open ? "chevron.up" : "chevron.down").font(.caption).foregroundStyle(Palette.muted) }
                }
            }
            .buttonStyle(.plain)
            .disabled(!hasBody)
            if open && hasBody {
                ScrollView(.horizontal) {
                    VStack(alignment: .leading, spacing: 0) {
                        ForEach(Array(LineDiff.lines(old: file.old, new: file.new).enumerated()), id: \.offset) { _, line in
                            diffLine(line)
                        }
                    }
                }
            }
        }
    }

    private func diffLine(_ line: DiffLine) -> some View {
        let (mark, tint): (String, Color?) = switch line.kind {
        case .added: ("+", Palette.green)
        case .removed: ("-", Palette.red)
        case .same: (" ", nil)
        }
        return Text("\(mark) \(line.text)")
            .font(.caption2.monospaced())
            .foregroundStyle(tint ?? Palette.muted)
            .padding(.horizontal, 4)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(tint.map { $0.opacity(0.1) } ?? .clear)
    }
}
