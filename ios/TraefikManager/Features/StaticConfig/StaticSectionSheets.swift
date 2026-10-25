import SwiftUI

// MARK: - Form building blocks (port of `StaticFormFields.kt`)

/// Sheet chrome shared by every static section form: Form, cancel/save, inline error.
struct StaticFormSheet<Content: View>: View {
    let title: String
    var saveLabel = String(localized: "Save changes")
    let onSave: () async -> String?
    @ViewBuilder var content: Content

    @Environment(\.dismiss) private var dismiss
    @State private var busy = false
    @State private var error: String?

    var body: some View {
        Form {
            content
            if let error {
                Section { Text(error).font(.footnote).foregroundStyle(Palette.red) }
            }
        }
        .textInputAutocapitalization(.never)
        .autocorrectionDisabled()
        .navigationTitle(title)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
            ToolbarItem(placement: .confirmationAction) {
                if busy {
                    ProgressView()
                } else {
                    Button(saveLabel) {
                        busy = true
                        error = nil
                        Task {
                            let failure = await onSave()
                            busy = false
                            if let failure { error = failure } else { dismiss() }
                        }
                    }
                }
            }
        }
        .interactiveDismissDisabled(busy)
    }
}

struct FormField: View {
    let label: String
    @Binding var value: String
    var placeholder = ""
    var help: String?
    var mono = false
    var numeric = false

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label).font(.caption).foregroundStyle(Palette.muted)
            TextField(placeholder, text: $value)
                .font(mono ? .callout.monospaced() : .body)
                .keyboardType(numeric ? .numberPad : .default)
            if let help { Text(help).font(.caption2).foregroundStyle(Palette.muted) }
        }
    }
}

struct FormLines: View {
    let label: String
    @Binding var value: String
    var placeholder = ""
    var help: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label).font(.caption).foregroundStyle(Palette.muted)
            TextField(placeholder, text: $value, axis: .vertical)
                .font(.callout.monospaced())
                .lineLimit(2...8)
            if let help { Text(help).font(.caption2).foregroundStyle(Palette.muted) }
        }
    }
}

struct FormToggle: View {
    let title: String
    var subtitle: String?
    @Binding var isOn: Bool
    var danger = false

    var body: some View {
        Toggle(isOn: $isOn) {
            VStack(alignment: .leading, spacing: 1) {
                Text(title)
                if let subtitle {
                    Text(subtitle).font(.caption2).foregroundStyle(danger ? Palette.red : Palette.muted)
                }
            }
        }
        .tint(danger ? Palette.red : nil)
    }
}

struct FormSelect: View {
    let label: String
    @Binding var value: String
    let options: [StaticOption]
    var help: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Picker(label, selection: $value) {
                ForEach(options, id: \.value) { Text($0.label).tag($0.value) }
            }
            if let help { Text(help).font(.caption2).foregroundStyle(Palette.muted) }
        }
    }
}

// MARK: - Sheets (port of `StaticSectionSheets.kt`)

struct EntrypointSheet: View {
    let adding: Bool
    let onSave: (EntrypointForm) async -> String?
    @State private var form: EntrypointForm

    init(initial: EntrypointForm, adding: Bool, onSave: @escaping (EntrypointForm) async -> String?) {
        self.adding = adding
        self.onSave = onSave
        _form = State(initialValue: initial)
    }

    var body: some View {
        StaticFormSheet(
            title: adding ? String(localized: "New entrypoint") : String(localized: "Edit entrypoint"),
            saveLabel: adding ? String(localized: "Add entrypoint") : String(localized: "Save changes"),
            onSave: { await onSave(form) }
        ) {
            Section {
                FormField(label: String(localized: "Name"), value: $form.name, placeholder: "websecure")
                FormField(label: String(localized: "Address"), value: $form.address, placeholder: ":443", mono: true)
                FormField(label: String(localized: "HTTP to HTTPS redirect (optional)"), value: $form.redirectTo, placeholder: "websecure",
                          help: String(localized: "The HTTPS entrypoint to redirect to."))
                FormToggle(title: String(localized: "Enable HTTP/3 (QUIC)"), subtitle: String(localized: "Adds http3 to this entrypoint."), isOn: $form.http3)
                FormSelect(label: String(localized: "Underscore headers"), value: $form.underscoreHeaders, options: EntrypointForm.underscoreOptions,
                           help: String(localized: "Stops underscore header aliases from bypassing forwardAuth. Delete recommended."))
            }
            Section("Trusted sources") {
                FormLines(label: String(localized: "Trusted IPs - forwarded headers"), value: $form.trustedIps, placeholder: "173.245.48.0/20",
                          help: String(localized: "IPs or CIDRs allowed to set X-Forwarded-*, one per line."))
                FormLines(label: String(localized: "Trusted IPs - PROXY protocol"), value: $form.proxyTrustedIps, placeholder: "192.168.1.10/32",
                          help: String(localized: "Enables PROXY protocol from these load balancers, one per line."))
                FormToggle(title: String(localized: "Trust forwarded headers from everyone"), subtitle: String(localized: "Insecure: lets any client forge its IP."),
                           isOn: $form.forwardedInsecure, danger: true)
                FormToggle(title: String(localized: "Accept PROXY protocol from everyone"), subtitle: String(localized: "Insecure, testing only."),
                           isOn: $form.proxyInsecure, danger: true)
            }
            Section("Routing") {
                FormField(label: String(localized: "Middleware chain (optional)"), value: $form.middlewares, placeholder: "secure-headers@file, rate-limit@file",
                          help: String(localized: "Prepended to every router here, comma separated, provider suffix included."), mono: true)
                FormToggle(title: String(localized: "TLS on every router"), subtitle: String(localized: "Routers on this entrypoint get TLS by default."), isOn: $form.tlsEnabled)
                if form.tlsEnabled {
                    FormField(label: String(localized: "Default cert resolver (optional)"), value: $form.tlsCertResolver, placeholder: "cloudflare")
                    FormField(label: String(localized: "Default TLS options (optional)"), value: $form.tlsOptions, placeholder: "modern@file")
                }
                FormToggle(title: String(localized: "Default entrypoint"), subtitle: String(localized: "Used by routers that list no entrypoints."), isOn: $form.asDefault)
            }
            Section("Responding timeouts") {
                FormField(label: String(localized: "Read"), value: $form.readTimeout, placeholder: "60s")
                FormField(label: String(localized: "Write"), value: $form.writeTimeout, placeholder: "0")
                FormField(label: String(localized: "Idle"), value: $form.idleTimeout, placeholder: "180s",
                          help: String(localized: "Forms like 30, 30s or 1m30s. 0 is unlimited."))
            }
        }
    }
}

struct ResolverSheet: View {
    let adding: Bool
    let onSave: (ResolverForm) async -> String?
    @State private var form: ResolverForm

    init(initial: ResolverForm, adding: Bool, onSave: @escaping (ResolverForm) async -> String?) {
        self.adding = adding
        self.onSave = onSave
        _form = State(initialValue: initial)
    }

    var body: some View {
        StaticFormSheet(
            title: adding ? String(localized: "New resolver") : String(localized: "Edit resolver"),
            saveLabel: adding ? String(localized: "Add resolver") : String(localized: "Save changes"),
            onSave: { await onSave(form) }
        ) {
            Section {
                FormField(label: String(localized: "Name"), value: $form.name, placeholder: "cloudflare")
                FormField(label: String(localized: "Email"), value: $form.email, placeholder: "you@example.com")
                FormField(label: String(localized: "Storage path"), value: $form.storage, placeholder: "/acme.json", mono: true)
                FormSelect(label: String(localized: "Challenge type"), value: $form.challengeType, options: ResolverForm.challengeOptions)
                if form.challengeType == "dnsChallenge" {
                    FormField(label: String(localized: "DNS provider"), value: $form.provider, placeholder: "cloudflare")
                } else if form.challengeType == "httpChallenge" {
                    FormField(label: String(localized: "HTTP entrypoint"), value: $form.httpEntrypoint, placeholder: "web")
                }
                FormField(label: String(localized: "CA server (optional)"), value: $form.caServer, placeholder: String(localized: "Let's Encrypt production"), mono: true)
                FormSelect(label: String(localized: "Key type (optional)"), value: $form.keyType, options: ResolverForm.keyTypeOptions)
            }
            Section("External account binding") {
                FormField(label: String(localized: "EAB key ID (optional)"), value: $form.eabKid)
                FormField(label: String(localized: "EAB HMAC (optional)"), value: $form.eabHmac,
                          help: String(localized: "Both are needed together, or neither."))
            }
            if form.challengeType == "dnsChallenge" {
                Section("DNS checks") {
                    FormLines(label: String(localized: "DNS check resolvers (optional)"), value: $form.dnsResolvers, placeholder: "1.1.1.1:53",
                              help: String(localized: "Used to verify the record before asking for the certificate, one per line."))
                    FormField(label: String(localized: "Propagation delay (optional)"), value: $form.dnsDelay, placeholder: "30s")
                    FormToggle(title: String(localized: "Disable propagation checks"), isOn: $form.dnsDisableChecks)
                }
            }
        }
    }
}

struct StaticPluginSheet: View {
    let adding: Bool
    let onSave: (StaticPluginForm) async -> String?
    @State private var form: StaticPluginForm

    init(initial: StaticPluginForm, adding: Bool, onSave: @escaping (StaticPluginForm) async -> String?) {
        self.adding = adding
        self.onSave = onSave
        _form = State(initialValue: initial)
    }

    var body: some View {
        StaticFormSheet(
            title: adding ? String(localized: "New plugin") : String(localized: "Edit plugin"),
            saveLabel: adding ? String(localized: "Add plugin") : String(localized: "Save changes"),
            onSave: { await onSave(form) }
        ) {
            Section {
                FormField(label: String(localized: "Name"), value: $form.name, placeholder: "my-plugin")
                FormField(label: String(localized: "Module"), value: $form.moduleName, placeholder: "github.com/user/plugin", mono: true)
                if !form.local {
                    FormField(label: String(localized: "Version"), value: $form.version, placeholder: "v1.0.0", mono: true)
                }
                FormToggle(title: String(localized: "Local plugin"), subtitle: String(localized: "Loaded from the plugins-local directory, no version needed."), isOn: $form.local)
            }
        }
    }
}

struct ProvidersSheet: View {
    let onSave: (ProvidersForm) async -> String?
    @State private var form: ProvidersForm

    init(initial: ProvidersForm, onSave: @escaping (ProvidersForm) async -> String?) {
        self.onSave = onSave
        _form = State(initialValue: initial)
    }

    var body: some View {
        StaticFormSheet(title: String(localized: "Providers"), onSave: { await onSave(form) }) {
            Section("Docker") {
                FormToggle(title: "Docker", isOn: $form.docker)
                if form.docker {
                    FormField(label: String(localized: "Endpoint"), value: $form.dockerEndpoint, placeholder: "unix:///var/run/docker.sock", mono: true)
                    FormToggle(title: String(localized: "Expose by default"), subtitle: String(localized: "Every container is routable unless it opts out."),
                               isOn: $form.dockerExposedByDefault)
                    FormToggle(title: String(localized: "Watch"), isOn: $form.dockerWatch)
                }
            }
            Section("File") {
                FormToggle(title: "File", isOn: $form.file)
                if form.file {
                    FormField(label: String(localized: "Directory"), value: $form.fileDirectory, placeholder: "/etc/traefik/dynamic", mono: true)
                    FormToggle(title: String(localized: "Watch"), isOn: $form.fileWatch)
                }
            }
            Section("Reloads") {
                FormField(label: String(localized: "Providers throttle (optional)"), value: $form.throttle, placeholder: "2s",
                          help: String(localized: "Minimum time between config reloads."))
            }
        }
    }
}

struct ApiSheet: View {
    let onSave: (ApiForm) async -> String?
    @State private var form: ApiForm

    init(initial: ApiForm, onSave: @escaping (ApiForm) async -> String?) {
        self.onSave = onSave
        _form = State(initialValue: initial)
    }

    var body: some View {
        StaticFormSheet(title: String(localized: "API and dashboard"), onSave: { await onSave(form) }) {
            Section {
                FormToggle(title: String(localized: "API enabled"),
                           subtitle: String(localized: "Traefik Manager reads routes, services and middlewares from this API. With it off those tabs are empty."),
                           isOn: $form.enabled)
                FormToggle(title: String(localized: "Dashboard"), isOn: $form.dashboard)
                FormToggle(title: String(localized: "Insecure mode"), subtitle: String(localized: "Exposes the API without authentication."), isOn: $form.insecure, danger: true)
                FormToggle(title: String(localized: "Debug mode"), isOn: $form.debug)
            }
        }
    }
}

struct LogSheet: View {
    let onSave: (LogForm) async -> String?
    @State private var form: LogForm

    init(initial: LogForm, onSave: @escaping (LogForm) async -> String?) {
        self.onSave = onSave
        _form = State(initialValue: initial)
    }

    var body: some View {
        StaticFormSheet(title: String(localized: "Logging"), onSave: { await onSave(form) }) {
            Section {
                FormSelect(label: String(localized: "Log level"), value: $form.level, options: LogForm.levels)
                FormSelect(label: String(localized: "Log format"), value: $form.logFormat, options: LogForm.formats)
                FormField(label: String(localized: "Traefik log file"), value: $form.logFile, placeholder: "/var/log/traefik/traefik.log",
                          help: String(localized: "Leave empty for stdout."), mono: true)
            }
            if !form.logFile.isBlank {
                Section("Rotation") {
                    FormField(label: String(localized: "Max size (MB)"), value: $form.maxSize, numeric: true)
                    FormField(label: String(localized: "Max backups"), value: $form.maxBackups, numeric: true)
                    FormField(label: String(localized: "Max age (days)"), value: $form.maxAge, numeric: true)
                    FormToggle(title: String(localized: "Compress rotated files"), isOn: $form.compress)
                }
            }
            Section("Access log") {
                FormToggle(title: String(localized: "Access log"), subtitle: String(localized: "Without it the Logs tab has nothing to read."), isOn: $form.accessLog)
                if form.accessLog {
                    FormField(label: String(localized: "Access log file"), value: $form.accessLogPath, placeholder: "/var/log/traefik/access.log",
                              help: String(localized: "Empty for stdout."), mono: true)
                    FormSelect(label: String(localized: "Format"), value: $form.alFormat, options: LogForm.accessFormats)
                    FormField(label: String(localized: "Status code filter (optional)"), value: $form.alStatusCodes, placeholder: "400-499, 500",
                              help: String(localized: "Only log these responses, comma separated codes or ranges."), mono: true)
                    FormField(label: String(localized: "Min duration filter (optional)"), value: $form.alMinDuration, placeholder: "200ms",
                              help: String(localized: "Only log requests slower than this."))
                    FormField(label: String(localized: "Buffering (lines, optional)"), value: $form.alBuffering, placeholder: "100", numeric: true)
                    FormSelect(label: String(localized: "Headers"), value: $form.alHeadersMode, options: LogForm.headerModes)
                    FormToggle(title: String(localized: "Only log retry attempts"), isOn: $form.alRetry)
                }
            }
        }
    }
}

struct ObservabilitySheet: View {
    let onSave: (ObservabilityForm) async -> String?
    @State private var form: ObservabilityForm

    init(initial: ObservabilityForm, onSave: @escaping (ObservabilityForm) async -> String?) {
        self.onSave = onSave
        _form = State(initialValue: initial)
    }

    var body: some View {
        StaticFormSheet(title: String(localized: "Observability"), onSave: { await onSave(form) }) {
            Section {
                FormToggle(title: String(localized: "Ping endpoint"), subtitle: String(localized: "/ping health check."), isOn: $form.ping)
                FormToggle(title: String(localized: "Prometheus metrics"), subtitle: "/metrics", isOn: $form.prometheus)
                if form.prometheus {
                    FormToggle(title: String(localized: "Entrypoint labels"), isOn: $form.promEpLabels)
                    FormToggle(title: String(localized: "Router labels"), isOn: $form.promRouterLabels)
                    FormToggle(title: String(localized: "Service labels"), isOn: $form.promSvcLabels)
                }
            }
            Section("Tracing") {
                FormToggle(title: String(localized: "Tracing"), subtitle: "OTLP", isOn: $form.tracing)
                if form.tracing {
                    FormField(label: String(localized: "Service name (optional)"), value: $form.traceService, placeholder: "traefik")
                    FormField(label: String(localized: "Sample rate (optional)"), value: $form.traceSample, placeholder: "1.0",
                              help: String(localized: "Between 0 and 1."))
                    FormField(label: String(localized: "OTLP HTTP endpoint (optional)"), value: $form.traceEndpoint, placeholder: "http://collector:4318/v1/traces",
                              help: String(localized: "Defaults to localhost:4318."), mono: true)
                }
            }
        }
    }
}

struct SystemSheet: View {
    let onSave: (SystemForm) async -> String?
    @State private var form: SystemForm

    init(initial: SystemForm, onSave: @escaping (SystemForm) async -> String?) {
        self.onSave = onSave
        _form = State(initialValue: initial)
    }

    var body: some View {
        StaticFormSheet(title: String(localized: "System"), onSave: { await onSave(form) }) {
            Section {
                FormToggle(title: String(localized: "Check for new Traefik versions"), isOn: $form.checkNewVersion)
                FormToggle(title: String(localized: "Send anonymous usage statistics"), isOn: $form.sendUsage)
                FormSelect(label: String(localized: "Default rule syntax"), value: $form.ruleSyntax, options: SystemForm.ruleSyntaxOptions,
                           help: String(localized: "Only change this while migrating rules written for Traefik v2."))
            }
            Section("Servers transport defaults") {
                FormToggle(title: String(localized: "Skip backend TLS verification"), subtitle: String(localized: "Insecure."), isOn: $form.stInsecure, danger: true)
                FormLines(label: String(localized: "Root CAs (optional)"), value: $form.stRootCAs, placeholder: "/certs/internal-ca.pem",
                          help: String(localized: "One path per line."))
                FormField(label: String(localized: "Max idle conns per host (optional)"), value: $form.stMaxIdle, placeholder: "200", numeric: true)
            }
            Section("Forwarding timeouts") {
                FormField(label: String(localized: "Dial"), value: $form.stDial, placeholder: "30s")
                FormField(label: String(localized: "Response header"), value: $form.stRespHeader, placeholder: "0")
                FormField(label: String(localized: "Idle connection"), value: $form.stIdleConn, placeholder: "90s")
            }
        }
    }
}
