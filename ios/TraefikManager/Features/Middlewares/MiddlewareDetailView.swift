import SwiftUI

/// One middleware: facts, YAML and actions. Port of `MiddlewareDetailPane`.
struct MiddlewareDetailView: View {
    @Environment(AppModel.self) private var app
    @Environment(\.dismiss) private var dismiss
    let name: String
    let usage: Int
    @Binding var message: String?

    @State private var editing = false
    @State private var confirmDelete = false
    @State private var blocked: (message: String, routers: [String])?

    private var middleware: MiddlewareDef? { app.routes.snapshot?.middlewares.first { $0.name == name } }

    var body: some View {
        if let middleware {
            content(middleware)
        } else {
            ContentUnavailableView(
                "Select a middleware",
                systemImage: "hand.tap",
                description: Text("Pick one from the list to see its configuration.")
            )
        }
    }

    private func content(_ middleware: MiddlewareDef) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.md) {
                HStack(spacing: Spacing.sm) {
                    Image(systemName: MiddlewareTemplates.icon(for: middleware.kind))
                        .foregroundStyle(Palette.purple)
                    Text(middleware.name).font(.title2.weight(.semibold)).lineLimit(2)
                }
                DetailSection(title: String(localized: "Middleware"), systemImage: "info.circle", tint: Palette.purple) {
                    DetailRow(label: String(localized: "Name"), value: middleware.name, mono: true)
                    DetailRow(label: String(localized: "Protocol"), value: middleware.proto.rawValue.uppercased())
                    DetailRow(label: String(localized: "Kind"), value: middleware.kind, mono: true)
                    if !middleware.configFile.isEmpty {
                        DetailRow(label: String(localized: "Config file"), value: middleware.configFile, mono: true)
                    }
                    DetailRow(
                        label: String(localized: "Usage"),
                        value: usage == 0 ? String(localized: "unused") : String(localized: "used by \(usage) routes"),
                        last: true
                    )
                }
                VStack(alignment: .leading, spacing: Spacing.sm) {
                    SectionLabel(text: String(localized: "Configuration"))
                    Text(YamlHighlighter.attributed(middleware.yaml.trimmingCharacters(in: .newlines)))
                        .textSelection(.enabled)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(Spacing.md)
                        .background(Palette.card, in: RoundedRectangle(cornerRadius: Radius.md))
                        .overlay(RoundedRectangle(cornerRadius: Radius.md).strokeBorder(Palette.border))
                }
            }
            .padding(Spacing.lg)
            .frame(maxWidth: 820)
            .frame(maxWidth: .infinity)
        }
        .background(Palette.bg)
        .navigationTitle(middleware.name)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Menu {
                    Button { editing = true } label: { Label("Edit middleware", systemImage: "pencil") }
                    Button {
                        UIPasteboard.general.string = middleware.yaml
                        message = String(localized: "Copied")
                    } label: {
                        Label("Copy YAML", systemImage: "doc.on.doc")
                    }
                    Divider()
                    Button(role: .destructive) { confirmDelete = true } label: { Label("Delete middleware", systemImage: "trash") }
                } label: {
                    Image(systemName: "ellipsis.circle")
                }
                .accessibilityLabel("Actions")
            }
        }
        .sheet(isPresented: $editing) {
            NavigationStack {
                MiddlewareFormView(name: middleware.name) { message = String(localized: "\($0) saved") }
            }
        }
        .confirmationDialog(String(localized: "Delete \(middleware.name)?"), isPresented: $confirmDelete, titleVisibility: .visible) {
            Button("Delete", role: .destructive) { delete(middleware, force: false) }
        } message: {
            Text("Any route still referencing it will stop working.")
        }
        .alert("Still in use", isPresented: Binding(get: { blocked != nil }, set: { if !$0 { blocked = nil } })) {
            Button("Delete anyway", role: .destructive) { delete(middleware, force: true) }
            Button("Keep it", role: .cancel) {}
        } message: {
            if let blocked {
                Text(blocked.routers.isEmpty
                    ? blocked.message
                    : blocked.message + "\n" + String(localized: "Deleting it removes it from \(blocked.routers.joined(separator: ", ")).")
                )
            }
        }
    }

    private func delete(_ middleware: MiddlewareDef, force: Bool) {
        guard let service = app.service else { return }
        Task {
            do {
                let result = try await service.deleteMiddleware(middleware.name, configFile: middleware.configFile, force: force)
                guard result.worked else {
                    message = result.message ?? result.error ?? String(localized: "Could not delete the middleware")
                    return
                }
                message = String(localized: "\(middleware.name) deleted")
                dismiss()
                app.routes.noteExternalChange()
                await app.refreshRoutes()
            } catch let APIError.inUse(text, routers, _) {
                blocked = (text ?? String(localized: "Could not delete the middleware"), routers)
            } catch {
                message = error.localizedDescription
            }
        }
    }
}
