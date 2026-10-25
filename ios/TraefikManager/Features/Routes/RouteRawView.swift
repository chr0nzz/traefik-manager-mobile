import SwiftUI

/// Edit a route's YAML directly. Port of `RouteRawScreen`.
struct RouteRawView: View {
    @Environment(AppModel.self) private var app
    let routeId: String
    /// Called with `true` after a successful save, `false` on cancel.
    var onFinish: (Bool) -> Void

    @State private var content = ""
    @State private var configFile = ""
    @State private var loading = true
    @State private var saving = false
    @State private var loadError: String?
    @State private var error: String?

    var body: some View {
        Group {
            if loading {
                ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if let loadError {
                ContentUnavailableView {
                    Label("Could not load the YAML", systemImage: "exclamationmark.triangle")
                } description: {
                    Text(loadError)
                }
            } else {
                VStack(spacing: 0) {
                    if let error {
                        Text(error)
                            .font(.footnote)
                            .foregroundStyle(Palette.red)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(.horizontal, Spacing.lg)
                            .padding(.vertical, Spacing.sm)
                    }
                    YamlEditor(text: $content, editable: !saving)
                        .background(Palette.card)
                }
            }
        }
        .background(Palette.bg)
        .navigationTitle("Raw YAML")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .principal) {
                VStack(spacing: 0) {
                    Text("Raw YAML").font(.headline)
                    if !configFile.isEmpty {
                        Text(configFile).font(.caption2.monospaced()).foregroundStyle(Palette.muted)
                    }
                }
            }
            ToolbarItem(placement: .cancellationAction) {
                Button("Cancel") { onFinish(false) }
            }
            ToolbarItem(placement: .confirmationAction) {
                if saving {
                    ProgressView()
                } else {
                    Button("Save", action: save).disabled(loading || loadError != nil)
                }
            }
        }
        .interactiveDismissDisabled(saving)
        .task { await load() }
    }

    private func load() async {
        guard let service = app.service else { return }
        do {
            let raw = try await service.routeRaw(routeId)
            content = raw.raw
            configFile = raw.configFile
        } catch {
            loadError = error.localizedDescription
        }
        loading = false
    }

    private func save() {
        guard let service = app.service else { return }
        guard !content.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            error = String(localized: "The YAML is empty")
            return
        }
        saving = true
        error = nil
        Task {
            defer { saving = false }
            do {
                try await app.routes.saveRaw(routeId: routeId, content: content, service: service)
                onFinish(true)
            } catch {
                self.error = error.localizedDescription
            }
        }
    }
}
