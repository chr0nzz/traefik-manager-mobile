package dev.chr0nzz.traefikmanager.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.chr0nzz.traefikmanager.data.model.ApiForm
import dev.chr0nzz.traefikmanager.data.model.EntrypointForm
import dev.chr0nzz.traefikmanager.data.model.LogForm
import dev.chr0nzz.traefikmanager.data.model.ObservabilityForm
import dev.chr0nzz.traefikmanager.data.model.ProvidersForm
import dev.chr0nzz.traefikmanager.data.model.ResolverForm
import dev.chr0nzz.traefikmanager.data.model.StaticPluginForm
import dev.chr0nzz.traefikmanager.data.model.SystemForm

@Composable
fun EntrypointSheet(
    initial: EntrypointForm,
    adding: Boolean,
    busy: Boolean,
    error: String?,
    onSave: (EntrypointForm) -> Unit,
    onDismiss: () -> Unit,
) {
    var form by remember(initial) { mutableStateOf(initial) }

    StaticFormSheet(
        title = if (adding) "New entrypoint" else "Edit entrypoint",
        busy = busy,
        error = error,
        onSave = { onSave(form) },
        onDismiss = onDismiss,
        saveLabel = if (adding) "Add entrypoint" else "Save changes",
    ) {
        FormField("Name", form.name, { form = form.copy(name = it) }, placeholder = "websecure")
        FormField("Address", form.address, { form = form.copy(address = it) }, placeholder = ":443")
        FormField(
            label = "HTTP to HTTPS redirect (optional)",
            value = form.redirectTo,
            onChange = { form = form.copy(redirectTo = it) },
            placeholder = "websecure",
            help = "The HTTPS entrypoint to redirect to.",
        )
        FormToggle(
            title = "Enable HTTP/3 (QUIC)",
            subtitle = "Adds http3 to this entrypoint.",
            checked = form.http3,
            onChange = { form = form.copy(http3 = it) },
        )
        FormSelect(
            label = "Underscore headers",
            value = form.underscoreHeaders,
            options = EntrypointForm.underscoreOptions,
            onChange = { form = form.copy(underscoreHeaders = it) },
            help = "Stops underscore header aliases from bypassing forwardAuth. Delete recommended.",
        )

        FormGroup("Trusted sources")
        FormLines(
            label = "Trusted IPs - forwarded headers",
            value = form.trustedIps,
            onChange = { form = form.copy(trustedIps = it) },
            placeholder = "173.245.48.0/20",
            help = "IPs or CIDRs allowed to set X-Forwarded-*, one per line.",
        )
        FormLines(
            label = "Trusted IPs - PROXY protocol",
            value = form.proxyTrustedIps,
            onChange = { form = form.copy(proxyTrustedIps = it) },
            placeholder = "192.168.1.10/32",
            help = "Enables PROXY protocol from these load balancers, one per line.",
        )
        FormToggle(
            title = "Trust forwarded headers from everyone",
            subtitle = "Insecure: lets any client forge its IP.",
            checked = form.forwardedInsecure,
            onChange = { form = form.copy(forwardedInsecure = it) },
            danger = true,
        )
        FormToggle(
            title = "Accept PROXY protocol from everyone",
            subtitle = "Insecure, testing only.",
            checked = form.proxyInsecure,
            onChange = { form = form.copy(proxyInsecure = it) },
            danger = true,
        )

        FormGroup("Routing")
        FormField(
            label = "Middleware chain (optional)",
            value = form.middlewares,
            onChange = { form = form.copy(middlewares = it) },
            placeholder = "secure-headers@file, rate-limit@file",
            help = "Prepended to every router here, comma separated, provider suffix included.",
            mono = true,
        )
        FormToggle(
            title = "TLS on every router",
            subtitle = "Routers on this entrypoint get TLS by default.",
            checked = form.tlsEnabled,
            onChange = { form = form.copy(tlsEnabled = it) },
        )
        if (form.tlsEnabled) {
            FormField(
                label = "Default cert resolver (optional)",
                value = form.tlsCertResolver,
                onChange = { form = form.copy(tlsCertResolver = it) },
                placeholder = "cloudflare",
            )
            FormField(
                label = "Default TLS options (optional)",
                value = form.tlsOptions,
                onChange = { form = form.copy(tlsOptions = it) },
                placeholder = "modern@file",
            )
        }
        FormToggle(
            title = "Default entrypoint",
            subtitle = "Used by routers that list no entrypoints.",
            checked = form.asDefault,
            onChange = { form = form.copy(asDefault = it) },
        )

        FormGroup("Responding timeouts")
        FormField("Read", form.readTimeout, { form = form.copy(readTimeout = it) }, placeholder = "60s")
        FormField("Write", form.writeTimeout, { form = form.copy(writeTimeout = it) }, placeholder = "0")
        FormField(
            label = "Idle",
            value = form.idleTimeout,
            onChange = { form = form.copy(idleTimeout = it) },
            placeholder = "180s",
            help = "Forms like 30, 30s or 1m30s. 0 is unlimited.",
        )
    }
}

@Composable
fun ResolverSheet(
    initial: ResolverForm,
    adding: Boolean,
    busy: Boolean,
    error: String?,
    onSave: (ResolverForm) -> Unit,
    onDismiss: () -> Unit,
) {
    var form by remember(initial) { mutableStateOf(initial) }

    StaticFormSheet(
        title = if (adding) "New resolver" else "Edit resolver",
        busy = busy,
        error = error,
        onSave = { onSave(form) },
        onDismiss = onDismiss,
        saveLabel = if (adding) "Add resolver" else "Save changes",
    ) {
        FormField("Name", form.name, { form = form.copy(name = it) }, placeholder = "cloudflare")
        FormField("Email", form.email, { form = form.copy(email = it) }, placeholder = "you@example.com")
        FormField("Storage path", form.storage, { form = form.copy(storage = it) }, placeholder = "/acme.json")
        FormSelect(
            label = "Challenge type",
            value = form.challengeType,
            options = ResolverForm.challengeOptions,
            onChange = { form = form.copy(challengeType = it) },
        )
        when (form.challengeType) {
            "dnsChallenge" -> FormField(
                label = "DNS provider",
                value = form.provider,
                onChange = { form = form.copy(provider = it) },
                placeholder = "cloudflare",
            )
            "httpChallenge" -> FormField(
                label = "HTTP entrypoint",
                value = form.httpEntrypoint,
                onChange = { form = form.copy(httpEntrypoint = it) },
                placeholder = "web",
            )
        }
        FormField(
            label = "CA server (optional)",
            value = form.caServer,
            onChange = { form = form.copy(caServer = it) },
            placeholder = "Let's Encrypt production",
        )
        FormSelect(
            label = "Key type (optional)",
            value = form.keyType,
            options = ResolverForm.keyTypeOptions,
            onChange = { form = form.copy(keyType = it) },
        )

        FormGroup("External account binding")
        FormField("EAB key ID (optional)", form.eabKid, { form = form.copy(eabKid = it) })
        FormField(
            label = "EAB HMAC (optional)",
            value = form.eabHmac,
            onChange = { form = form.copy(eabHmac = it) },
            help = "Both are needed together, or neither.",
        )

        if (form.challengeType == "dnsChallenge") {
            FormGroup("DNS checks")
            FormLines(
                label = "DNS check resolvers (optional)",
                value = form.dnsResolvers,
                onChange = { form = form.copy(dnsResolvers = it) },
                placeholder = "1.1.1.1:53",
                help = "Used to verify the record before asking for the certificate, one per line.",
            )
            FormField(
                label = "Propagation delay (optional)",
                value = form.dnsDelay,
                onChange = { form = form.copy(dnsDelay = it) },
                placeholder = "30s",
            )
            FormToggle(
                title = "Disable propagation checks",
                checked = form.dnsDisableChecks,
                onChange = { form = form.copy(dnsDisableChecks = it) },
            )
        }
    }
}

@Composable
fun StaticPluginSheet(
    initial: StaticPluginForm,
    adding: Boolean,
    busy: Boolean,
    error: String?,
    onSave: (StaticPluginForm) -> Unit,
    onDismiss: () -> Unit,
) {
    var form by remember(initial) { mutableStateOf(initial) }

    StaticFormSheet(
        title = if (adding) "New plugin" else "Edit plugin",
        busy = busy,
        error = error,
        onSave = { onSave(form) },
        onDismiss = onDismiss,
        saveLabel = if (adding) "Add plugin" else "Save changes",
    ) {
        FormField("Name", form.name, { form = form.copy(name = it) }, placeholder = "my-plugin")
        FormField(
            label = "Module",
            value = form.moduleName,
            onChange = { form = form.copy(moduleName = it) },
            placeholder = "github.com/user/plugin",
            mono = true,
        )
        if (!form.local) {
            FormField("Version", form.version, { form = form.copy(version = it) }, placeholder = "v1.0.0")
        }
        FormToggle(
            title = "Local plugin",
            subtitle = "Loaded from the plugins-local directory, no version needed.",
            checked = form.local,
            onChange = { form = form.copy(local = it) },
        )
    }
}

@Composable
fun ProvidersSheet(
    initial: ProvidersForm,
    busy: Boolean,
    error: String?,
    onSave: (ProvidersForm) -> Unit,
    onDismiss: () -> Unit,
) {
    var form by remember(initial) { mutableStateOf(initial) }

    StaticFormSheet(
        title = "Providers",
        busy = busy,
        error = error,
        onSave = { onSave(form) },
        onDismiss = onDismiss,
        saveLabel = "Save changes",
    ) {
        FormToggle("Docker", form.docker, { form = form.copy(docker = it) })
        if (form.docker) {
            FormField(
                label = "Endpoint",
                value = form.dockerEndpoint,
                onChange = { form = form.copy(dockerEndpoint = it) },
                placeholder = "unix:///var/run/docker.sock",
                mono = true,
            )
            FormToggle(
                title = "Expose by default",
                subtitle = "Every container is routable unless it opts out.",
                checked = form.dockerExposedByDefault,
                onChange = { form = form.copy(dockerExposedByDefault = it) },
            )
            FormToggle("Watch", form.dockerWatch, { form = form.copy(dockerWatch = it) })
        }

        FormGroup("File")
        FormToggle("File", form.file, { form = form.copy(file = it) })
        if (form.file) {
            FormField(
                label = "Directory",
                value = form.fileDirectory,
                onChange = { form = form.copy(fileDirectory = it) },
                placeholder = "/etc/traefik/dynamic",
                mono = true,
            )
            FormToggle("Watch", form.fileWatch, { form = form.copy(fileWatch = it) })
        }

        FormGroup("Reloads")
        FormField(
            label = "Providers throttle (optional)",
            value = form.throttle,
            onChange = { form = form.copy(throttle = it) },
            placeholder = "2s",
            help = "Minimum time between config reloads.",
        )
    }
}

@Composable
fun ApiSheet(
    initial: ApiForm,
    busy: Boolean,
    error: String?,
    onSave: (ApiForm) -> Unit,
    onDismiss: () -> Unit,
) {
    var form by remember(initial) { mutableStateOf(initial) }

    StaticFormSheet(
        title = "API and dashboard",
        busy = busy,
        error = error,
        onSave = { onSave(form) },
        onDismiss = onDismiss,
        saveLabel = "Save changes",
    ) {
        FormToggle(
            title = "API enabled",
            subtitle = "Traefik Manager reads routes, services and middlewares from this API. " +
                "With it off those tabs are empty.",
            checked = form.enabled,
            onChange = { form = form.copy(enabled = it) },
        )
        FormToggle("Dashboard", form.dashboard, { form = form.copy(dashboard = it) })
        FormToggle(
            title = "Insecure mode",
            subtitle = "Exposes the API without authentication.",
            checked = form.insecure,
            onChange = { form = form.copy(insecure = it) },
            danger = true,
        )
        FormToggle("Debug mode", form.debug, { form = form.copy(debug = it) })
    }
}

@Composable
fun LogSheet(
    initial: LogForm,
    busy: Boolean,
    error: String?,
    onSave: (LogForm) -> Unit,
    onDismiss: () -> Unit,
) {
    var form by remember(initial) { mutableStateOf(initial) }

    StaticFormSheet(
        title = "Logging",
        busy = busy,
        error = error,
        onSave = { onSave(form) },
        onDismiss = onDismiss,
        saveLabel = "Save changes",
    ) {
        FormSelect(
            label = "Log level",
            value = form.level,
            options = LogForm.levels.map { it to it },
            onChange = { form = form.copy(level = it) },
        )
        FormSelect(
            label = "Log format",
            value = form.logFormat,
            options = LogForm.formats,
            onChange = { form = form.copy(logFormat = it) },
        )
        FormField(
            label = "Traefik log file",
            value = form.logFile,
            onChange = { form = form.copy(logFile = it) },
            placeholder = "/var/log/traefik/traefik.log",
            help = "Leave empty for stdout.",
            mono = true,
        )
        if (form.logFile.isNotBlank()) {
            FormGroup("Rotation")
            FormField("Max size (MB)", form.maxSize, { form = form.copy(maxSize = it) }, numeric = true)
            FormField("Max backups", form.maxBackups, { form = form.copy(maxBackups = it) }, numeric = true)
            FormField("Max age (days)", form.maxAge, { form = form.copy(maxAge = it) }, numeric = true)
            FormToggle("Compress rotated files", form.compress, { form = form.copy(compress = it) })
        }

        FormGroup("Access log")
        FormToggle(
            title = "Access log",
            subtitle = "Without it the Logs tab has nothing to read.",
            checked = form.accessLog,
            onChange = { form = form.copy(accessLog = it) },
        )
        if (form.accessLog) {
            FormField(
                label = "Access log file",
                value = form.accessLogPath,
                onChange = { form = form.copy(accessLogPath = it) },
                placeholder = "/var/log/traefik/access.log",
                help = "Empty for stdout.",
                mono = true,
            )
            FormSelect(
                label = "Format",
                value = form.alFormat,
                options = LogForm.accessFormats,
                onChange = { form = form.copy(alFormat = it) },
            )
            FormField(
                label = "Status code filter (optional)",
                value = form.alStatusCodes,
                onChange = { form = form.copy(alStatusCodes = it) },
                placeholder = "400-499, 500",
                help = "Only log these responses, comma separated codes or ranges.",
                mono = true,
            )
            FormField(
                label = "Min duration filter (optional)",
                value = form.alMinDuration,
                onChange = { form = form.copy(alMinDuration = it) },
                placeholder = "200ms",
                help = "Only log requests slower than this.",
            )
            FormField(
                label = "Buffering (lines, optional)",
                value = form.alBuffering,
                onChange = { form = form.copy(alBuffering = it) },
                placeholder = "100",
                numeric = true,
            )
            FormSelect(
                label = "Headers",
                value = form.alHeadersMode,
                options = LogForm.headerModes,
                onChange = { form = form.copy(alHeadersMode = it) },
            )
            FormToggle("Only log retry attempts", form.alRetry, { form = form.copy(alRetry = it) })
        }
    }
}

@Composable
fun ObservabilitySheet(
    initial: ObservabilityForm,
    busy: Boolean,
    error: String?,
    onSave: (ObservabilityForm) -> Unit,
    onDismiss: () -> Unit,
) {
    var form by remember(initial) { mutableStateOf(initial) }

    StaticFormSheet(
        title = "Observability",
        busy = busy,
        error = error,
        onSave = { onSave(form) },
        onDismiss = onDismiss,
        saveLabel = "Save changes",
    ) {
        FormToggle(
            title = "Ping endpoint",
            subtitle = "/ping health check.",
            checked = form.ping,
            onChange = { form = form.copy(ping = it) },
        )
        FormToggle(
            title = "Prometheus metrics",
            subtitle = "/metrics",
            checked = form.prometheus,
            onChange = { form = form.copy(prometheus = it) },
        )
        if (form.prometheus) {
            FormToggle("Entrypoint labels", form.promEpLabels, { form = form.copy(promEpLabels = it) })
            FormToggle("Router labels", form.promRouterLabels, { form = form.copy(promRouterLabels = it) })
            FormToggle("Service labels", form.promSvcLabels, { form = form.copy(promSvcLabels = it) })
        }

        FormGroup("Tracing")
        FormToggle(
            title = "Tracing",
            subtitle = "OTLP",
            checked = form.tracing,
            onChange = { form = form.copy(tracing = it) },
        )
        if (form.tracing) {
            FormField(
                label = "Service name (optional)",
                value = form.traceService,
                onChange = { form = form.copy(traceService = it) },
                placeholder = "traefik",
            )
            FormField(
                label = "Sample rate (optional)",
                value = form.traceSample,
                onChange = { form = form.copy(traceSample = it) },
                placeholder = "1.0",
                help = "Between 0 and 1.",
            )
            FormField(
                label = "OTLP HTTP endpoint (optional)",
                value = form.traceEndpoint,
                onChange = { form = form.copy(traceEndpoint = it) },
                placeholder = "http://collector:4318/v1/traces",
                help = "Defaults to localhost:4318.",
                mono = true,
            )
        }
    }
}

@Composable
fun SystemSheet(
    initial: SystemForm,
    busy: Boolean,
    error: String?,
    onSave: (SystemForm) -> Unit,
    onDismiss: () -> Unit,
) {
    var form by remember(initial) { mutableStateOf(initial) }

    StaticFormSheet(
        title = "System",
        busy = busy,
        error = error,
        onSave = { onSave(form) },
        onDismiss = onDismiss,
        saveLabel = "Save changes",
    ) {
        FormToggle(
            title = "Check for new Traefik versions",
            checked = form.checkNewVersion,
            onChange = { form = form.copy(checkNewVersion = it) },
        )
        FormToggle(
            title = "Send anonymous usage statistics",
            checked = form.sendUsage,
            onChange = { form = form.copy(sendUsage = it) },
        )
        FormSelect(
            label = "Default rule syntax",
            value = form.ruleSyntax,
            options = SystemForm.ruleSyntaxOptions,
            onChange = { form = form.copy(ruleSyntax = it) },
            help = "Only change this while migrating rules written for Traefik v2.",
        )

        FormGroup("Servers transport defaults")
        FormToggle(
            title = "Skip backend TLS verification",
            subtitle = "Insecure.",
            checked = form.stInsecure,
            onChange = { form = form.copy(stInsecure = it) },
            danger = true,
        )
        FormLines(
            label = "Root CAs (optional)",
            value = form.stRootCAs,
            onChange = { form = form.copy(stRootCAs = it) },
            placeholder = "/certs/internal-ca.pem",
            help = "One path per line.",
        )
        FormField(
            label = "Max idle conns per host (optional)",
            value = form.stMaxIdle,
            onChange = { form = form.copy(stMaxIdle = it) },
            placeholder = "200",
            numeric = true,
        )

        FormGroup("Forwarding timeouts")
        FormField("Dial", form.stDial, { form = form.copy(stDial = it) }, placeholder = "30s")
        FormField("Response header", form.stRespHeader, { form = form.copy(stRespHeader = it) }, placeholder = "0")
        FormField("Idle connection", form.stIdleConn, { form = form.copy(stIdleConn = it) }, placeholder = "90s")
    }
}
