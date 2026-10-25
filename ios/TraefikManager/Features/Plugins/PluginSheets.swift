import SwiftUI

private let defaultStaticSnippet = """
experimental:
  plugins:
    myPlugin:
      moduleName: github.com/author/plugin
      version: v0.1.0
"""

private let defaultMiddlewareSnippet = """
http:
  middlewares:
    my-myPlugin:
      plugin:
        myPlugin:
          setting: value
"""

/// Paste a plugin's static + middleware snippets and install. Port of `PluginInstallSheet`.
struct PluginInstallSheet: View {
    let onInstalled: (PluginInstallResponse) -> Void

    @Environment(AppModel.self) private var app
    @Environment(\.dismiss) private var dismiss
    @State private var staticYaml = defaultStaticSnippet
    @State private var middlewareYaml = defaultMiddlewareSnippet
    @State private var middlewareFile = ""
    @State private var busy = false
    @State private var error: String?

    var body: some View {
        Form {
            Section {
                VStack(alignment: .leading, spacing: Spacing.xs) {
                    Label("Only install plugins from trusted sources", systemImage: "exclamationmark.shield")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Palette.purple)
                    Text("Plugins run inside the Traefik process with full access to your traffic. Review the plugin source on GitHub before installing. A Traefik restart is required to activate the plugin after saving.")
                        .font(.caption)
                        .foregroundStyle(Palette.muted)
                }
            }

            Section {
                YamlEditor(text: $staticYaml).frame(minHeight: 130)
            } header: {
                Text("Step 1 - static config snippet")
            } footer: {
                Text("Paste it from the plugin's page on plugins.traefik.io.")
            }

            Section {
                YamlEditor(text: $middlewareYaml).frame(minHeight: 150)
            } header: {
                Text("Step 2 - middleware snippet")
            } footer: {
                Text("Edit the settings for your needs. Replace every placeholder and any {{ }} block with real values or Traefik will crash.")
                    .foregroundStyle(Palette.yellow)
            }

            Section {
                TextField("plugin-middlewares.yml", text: $middlewareFile)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
            } header: {
                Text("Middleware file")
            } footer: {
                Text("Which config file the middleware is written to.")
            }

            if let error {
                Section { Text(error).foregroundStyle(Palette.red).font(.footnote) }
            }
        }
        .navigationTitle("Add plugin")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
            ToolbarItem(placement: .confirmationAction) {
                if busy { ProgressView() } else { Button("Save", action: install) }
            }
        }
        .interactiveDismissDisabled(busy)
    }

    private func install() {
        guard !staticYaml.isBlank else {
            error = String(localized: "Paste the static config snippet")
            return
        }
        guard let service = app.service else { return }
        busy = true
        error = nil
        Task {
            let response = await service.installPlugin(staticYaml: staticYaml, middlewareYaml: middlewareYaml, middlewareFile: middlewareFile.trimmed)
            busy = false
            if response.ok {
                onInstalled(response)
                dismiss()
            } else {
                error = response.error.flatMap { $0.isBlank ? nil : $0 } ?? String(localized: "Failed to install plugin")
            }
        }
    }
}

/// Rename a plugin or change its module/version. Port of `PluginEditSheet`.
struct PluginEditSheet: View {
    let plugin: PluginEntry
    let onSaved: () -> Void

    @Environment(AppModel.self) private var app
    @Environment(\.dismiss) private var dismiss
    @State private var name = ""
    @State private var moduleName = ""
    @State private var version = ""
    @State private var busy = false
    @State private var error: String?

    var body: some View {
        Form {
            Section {
                LabeledContent("Name") {
                    TextField("myPlugin", text: $name).multilineTextAlignment(.trailing)
                }
                LabeledContent("Module") {
                    TextField("github.com/author/plugin", text: $moduleName).multilineTextAlignment(.trailing).font(.callout.monospaced())
                }
                LabeledContent("Version") {
                    TextField("v0.1.0", text: $version).multilineTextAlignment(.trailing).font(.callout.monospaced())
                }
            }
            .textInputAutocapitalization(.never)
            .autocorrectionDisabled()

            if let error {
                Section { Text(error).foregroundStyle(Palette.red).font(.footnote) }
            }
        }
        .navigationTitle("Edit plugin")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
            ToolbarItem(placement: .confirmationAction) {
                if busy { ProgressView() } else { Button("Save", action: save) }
            }
        }
        .onAppear {
            name = plugin.name
            moduleName = plugin.moduleName
            version = plugin.version
        }
        .interactiveDismissDisabled(busy)
    }

    private func save() {
        guard !name.isBlank, !moduleName.isBlank, !version.isBlank else {
            error = String(localized: "Name, module, and version are required")
            return
        }
        guard let service = app.service else { return }
        busy = true
        error = nil
        Task {
            do {
                try await StaticPlugins.save(name: name.trimmed, oldName: plugin.name, moduleName: moduleName.trimmed, version: version.trimmed, service: service)
                onSaved()
                dismiss()
            } catch {
                self.error = error.localizedDescription
            }
            busy = false
        }
    }
}
