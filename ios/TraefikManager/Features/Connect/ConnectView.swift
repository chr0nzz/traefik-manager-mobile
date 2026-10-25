import SwiftUI

/// First-run screen: enter the instance URL and API key, or try the demo. Port of `ConnectScreen`.
struct ConnectView: View {
    @Environment(AppModel.self) private var app

    @State private var url = ""
    @State private var apiKey = ""
    @State private var showKey = false
    @State private var connecting = false
    @State private var error: String?
    @FocusState private var focus: Field?

    private enum Field { case url, key }

    var body: some View {
        ScrollView {
            VStack(spacing: Spacing.md) {
                Image("Logo")
                    .resizable()
                    .frame(width: 64, height: 64)
                    .accessibilityHidden(true)

                (Text("Traefik ") + Text("Manager").foregroundStyle(Palette.blue))
                    .font(.largeTitle.weight(.semibold))

                Text("Connect to your instance")
                    .font(.subheadline)
                    .foregroundStyle(Palette.muted)
                    .padding(.bottom, Spacing.sm)

                VStack(spacing: Spacing.md) {
                    TextField("Instance URL", text: $url, prompt: Text(verbatim: "manager.example.com"))
                        .textContentType(.URL)
                        .keyboardType(.URL)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .submitLabel(.next)
                        .focused($focus, equals: .url)
                        .onSubmit { focus = .key }
                        .fieldStyle()
                        .labeled("Instance URL")

                    HStack {
                        Group {
                            if showKey {
                                TextField("API Key", text: $apiKey, prompt: Text("Required if API key auth is enabled"))
                            } else {
                                SecureField("API Key", text: $apiKey, prompt: Text("Required if API key auth is enabled"))
                            }
                        }
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .submitLabel(.go)
                        .focused($focus, equals: .key)
                        .onSubmit(connect)

                        Button {
                            showKey.toggle()
                        } label: {
                            Image(systemName: showKey ? "eye.slash" : "eye")
                                .foregroundStyle(Palette.muted)
                        }
                        .accessibilityLabel(showKey ? Text("Hide API key") : Text("Show API key"))
                    }
                    .fieldStyle()
                    .labeled("API Key")
                }
                .disabled(connecting)
                .onChange(of: url) { error = nil }
                .onChange(of: apiKey) { error = nil }

                if let error {
                    Text(error)
                        .font(.footnote)
                        .foregroundStyle(Palette.red)
                        .multilineTextAlignment(.center)
                }

                Button(action: connect) {
                    Group {
                        if connecting {
                            ProgressView().tint(.white)
                        } else {
                            Text("Connect")
                        }
                    }
                    .frame(maxWidth: .infinity, minHeight: 28)
                }
                .buttonStyle(.borderedProminent)
                .controlSize(.large)
                .disabled(connecting)

                Button {
                    Task { await app.enterDemo() }
                } label: {
                    Text("Try Demo").frame(maxWidth: .infinity, minHeight: 28)
                }
                .buttonStyle(.bordered)
                .controlSize(.large)
                .disabled(connecting)

                Text("Leave the API key empty if your instance has built-in auth disabled")
                    .font(.footnote)
                    .foregroundStyle(Palette.muted)
                    .multilineTextAlignment(.center)
            }
            .frame(maxWidth: 480)
            .padding(Spacing.xl)
            .frame(maxWidth: .infinity)
        }
        .scrollBounceBehavior(.basedOnSize)
        .defaultScrollAnchor(.center)
        .background(Palette.bg)
    }

    private func connect() {
        guard let baseURL = Self.normalize(url) else {
            error = String(localized: "Enter a URL")
            return
        }
        let key = apiKey.trimmingCharacters(in: .whitespacesAndNewlines)
        connecting = true
        error = nil
        Task {
            defer { connecting = false }
            let client = APIClient(baseURL: baseURL, apiKey: key.isEmpty ? nil : key, agentId: nil)
            do {
                let status = try await LiveService(client: client).apiKeyStatus()
                if !status.enabled && !key.isEmpty {
                    error = String(localized: "API key auth is not enabled on this server")
                } else if status.enabled && key.isEmpty {
                    error = String(localized: "This server requires an API key")
                } else {
                    await app.connect(baseURL: baseURL, apiKey: key.isEmpty ? nil : key)
                }
            } catch let failure as APIError {
                error = Self.describe(failure, keyProvided: !key.isEmpty)
            } catch {
                self.error = String(localized: "Connection failed")
            }
        }
    }

    static func describe(_ error: APIError, keyProvided: Bool) -> String {
        switch error {
        case .http(401, _):
            keyProvided ? String(localized: "Invalid API key") : String(localized: "This server requires an API key")
        case let .http(status, _):
            String(localized: "Server error \(status)")
        case .unreachable:
            String(localized: "Could not reach the server. Check the URL, your network, and that the certificate is trusted.")
        default:
            error.localizedDescription
        }
    }

    /// Adds a scheme when missing: plain IPv4 addresses get http, everything else https. Port of `normalizeUrl`.
    static func normalize(_ input: String) -> URL? {
        var trimmed = input.trimmingCharacters(in: .whitespacesAndNewlines)
        while trimmed.hasSuffix("/") { trimmed.removeLast() }
        guard !trimmed.isEmpty else { return nil }
        let lower = trimmed.lowercased()
        if !(lower.hasPrefix("http://") || lower.hasPrefix("https://")) {
            let isIPv4 = trimmed.range(of: #"^(\d{1,3}\.){3}\d{1,3}(:\d+)?$"#, options: .regularExpression) != nil
            trimmed = (isIPv4 ? "http://" : "https://") + trimmed
        }
        guard let url = URL(string: trimmed), url.host != nil else { return nil }
        return url
    }
}

private extension View {
    func labeled(_ title: LocalizedStringKey) -> some View {
        VStack(alignment: .leading, spacing: Spacing.xs) {
            Text(title)
                .font(.footnote.weight(.medium))
                .foregroundStyle(Palette.muted)
                .accessibilityHidden(true)
            self
        }
    }

    func fieldStyle() -> some View {
        padding(.horizontal, Spacing.md)
            .frame(minHeight: 48)
            .background(Palette.card, in: RoundedRectangle(cornerRadius: Radius.md, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: Radius.md, style: .continuous)
                    .strokeBorder(Palette.border, lineWidth: 1)
            )
    }
}

#Preview {
    ConnectView().environment(AppModel())
}
