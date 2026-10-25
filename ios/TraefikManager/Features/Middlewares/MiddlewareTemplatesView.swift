import SwiftUI

/// Saved YAML templates shared across servers. Port of `MiddlewareTemplatesScreen`.
struct MiddlewareTemplatesView: View {
    @Environment(AppModel.self) private var app
    @Environment(\.dismiss) private var dismiss

    @State private var templates: [MiddlewareTemplate] = []
    @State private var loading = true
    @State private var editing: MiddlewareTemplate?
    @State private var creating = false
    @State private var pendingDelete: MiddlewareTemplate?
    @State private var message: String?

    var body: some View {
        List {
            Section {
                Text("Reusable YAML you can start a middleware from. Pick one under Template in the middleware form. Templates are shared across every server, not just this one.")
                    .font(.footnote)
                    .foregroundStyle(Palette.muted)
            }
            if loading {
                HStack { Spacer(); ProgressView(); Spacer() }
            } else if templates.isEmpty {
                ContentUnavailableView(
                    "No custom templates yet",
                    systemImage: "doc.on.doc",
                    description: Text("Tap Add template to create one.")
                )
            } else {
                ForEach(templates) { template in
                    Button { editing = template } label: {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(template.name).foregroundStyle(Palette.text)
                            Text(MiddlewareTemplates.kind(of: template.yaml)).font(.caption.monospaced()).foregroundStyle(Palette.purple)
                        }
                    }
                    .swipeActions {
                        Button("Delete", systemImage: "trash", role: .destructive) { pendingDelete = template }
                    }
                }
            }
        }
        .navigationTitle("Middleware templates")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .cancellationAction) { Button("Close") { dismiss() } }
            ToolbarItem(placement: .primaryAction) {
                Button { creating = true } label: { Image(systemName: "plus") }.accessibilityLabel("Add template")
            }
        }
        .sheet(item: $editing) { template in
            NavigationStack { TemplateEditor(template: template) { done($0) } }
        }
        .sheet(isPresented: $creating) {
            NavigationStack { TemplateEditor(template: nil) { done($0) } }
        }
        .confirmationDialog("Delete template", isPresented: Binding(get: { pendingDelete != nil }, set: { if !$0 { pendingDelete = nil } }), titleVisibility: .visible) {
            Button("Delete", role: .destructive) { if let pendingDelete { delete(pendingDelete) } }
        } message: {
            Text("Delete this template? Middlewares already created from it are not affected.")
        }
        .toast($message)
        .task { await load() }
    }

    private func load() async {
        guard let service = app.service else { return }
        templates = (try? await service.middlewareTemplates().templates) ?? []
        loading = false
    }

    private func done(_ text: String) {
        message = text
        Task { await load() }
    }

    private func delete(_ template: MiddlewareTemplate) {
        guard let service = app.service else { return }
        Task {
            do {
                let result = try await service.deleteMiddlewareTemplate(id: template.id)
                message = result.worked ? String(localized: "Template deleted") : (result.error ?? String(localized: "Could not delete the template"))
            } catch {
                message = error.localizedDescription
            }
            await load()
        }
    }
}

private struct TemplateEditor: View {
    @Environment(AppModel.self) private var app
    @Environment(\.dismiss) private var dismiss
    let template: MiddlewareTemplate?
    let onSaved: (String) -> Void

    @State private var name = ""
    @State private var yaml = ""
    @State private var saving = false
    @State private var error: String?

    var body: some View {
        Form {
            if let error {
                Section { Text(error).foregroundStyle(Palette.red).font(.footnote) }
            }
            Section {
                TextField("Name", text: $name, prompt: Text("e.g. Secure Headers"))
            }
            Section("Configuration (YAML)") {
                YamlEditor(text: $yaml)
                    .frame(minHeight: 260)
                    .listRowInsets(EdgeInsets())
            }
        }
        .navigationTitle(template == nil ? String(localized: "Add template") : String(localized: "Edit template"))
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
            ToolbarItem(placement: .confirmationAction) {
                if saving { ProgressView() } else { Button("Save", action: save) }
            }
        }
        .onAppear {
            name = template?.name ?? ""
            yaml = template?.yaml ?? ""
        }
    }

    private func save() {
        guard let service = app.service else { return }
        guard !name.isBlank else {
            error = String(localized: "Name is required")
            return
        }
        guard !yaml.isBlank else {
            error = String(localized: "Middleware content cannot be empty.")
            return
        }
        saving = true
        Task {
            defer { saving = false }
            do {
                let result = if let template {
                    try await service.updateMiddlewareTemplate(id: template.id, name: name.trimmed, yaml: yaml)
                } else {
                    try await service.createMiddlewareTemplate(name: name.trimmed, yaml: yaml)
                }
                guard result.worked else {
                    error = result.message ?? result.error ?? String(localized: "Could not save the template")
                    return
                }
                onSaved(template == nil ? String(localized: "Template created") : String(localized: "Template updated"))
                dismiss()
            } catch {
                self.error = error.localizedDescription
            }
        }
    }
}
