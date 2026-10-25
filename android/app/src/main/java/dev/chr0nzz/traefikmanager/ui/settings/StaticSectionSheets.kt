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
import androidx.compose.ui.res.stringResource
import dev.chr0nzz.traefikmanager.R
import dev.chr0nzz.traefikmanager.data.model.UiText

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
        title = stringResource(if (adding) R.string.static_entrypoint_new else R.string.static_entrypoint_edit),
        busy = busy,
        error = error,
        onSave = { onSave(form) },
        onDismiss = onDismiss,
        saveLabel = stringResource(if (adding) R.string.static_entrypoint_add else R.string.static_save_changes),
    ) {
        FormField(stringResource(R.string.common_label_name), form.name, { form = form.copy(name = it) }, placeholder = "websecure")
        FormField(stringResource(R.string.static_address), form.address, { form = form.copy(address = it) }, placeholder = ":443")
        FormField(
            label = stringResource(R.string.static_http_to_https_redirect_optional),
            value = form.redirectTo,
            onChange = { form = form.copy(redirectTo = it) },
            placeholder = "websecure",
            help = stringResource(R.string.static_the_https_entrypoint_to_redirect_to),
        )
        FormToggle(
            title = stringResource(R.string.static_enable_http_3_quic),
            subtitle = stringResource(R.string.static_adds_http3_to_this_entrypoint),
            checked = form.http3,
            onChange = { form = form.copy(http3 = it) },
        )
        FormSelect(
            label = stringResource(R.string.static_underscore_headers),
            value = form.underscoreHeaders,
            options = EntrypointForm.underscoreOptions,
            onChange = { form = form.copy(underscoreHeaders = it) },
            help = stringResource(R.string.static_stops_underscore_header_aliases_from_bypassing),
        )

        FormGroup(stringResource(R.string.static_trusted_sources))
        FormLines(
            label = stringResource(R.string.static_trusted_ips_forwarded_headers),
            value = form.trustedIps,
            onChange = { form = form.copy(trustedIps = it) },
            placeholder = "173.245.48.0/20",
            help = stringResource(R.string.static_ips_or_cidrs_allowed_to_set),
        )
        FormLines(
            label = stringResource(R.string.static_trusted_ips_proxy_protocol),
            value = form.proxyTrustedIps,
            onChange = { form = form.copy(proxyTrustedIps = it) },
            placeholder = "192.168.1.10/32",
            help = stringResource(R.string.static_enables_proxy_protocol_from_these_load),
        )
        FormToggle(
            title = stringResource(R.string.static_trust_forwarded_headers_from_everyone),
            subtitle = stringResource(R.string.static_insecure_lets_any_client_forge_its),
            checked = form.forwardedInsecure,
            onChange = { form = form.copy(forwardedInsecure = it) },
            danger = true,
        )
        FormToggle(
            title = stringResource(R.string.static_accept_proxy_protocol_from_everyone),
            subtitle = stringResource(R.string.static_insecure_testing_only),
            checked = form.proxyInsecure,
            onChange = { form = form.copy(proxyInsecure = it) },
            danger = true,
        )

        FormGroup(stringResource(R.string.static_routing))
        FormField(
            label = stringResource(R.string.static_middleware_chain_optional),
            value = form.middlewares,
            onChange = { form = form.copy(middlewares = it) },
            placeholder = "secure-headers@file, rate-limit@file",
            help = stringResource(R.string.static_prepended_to_every_router_here_comma),
            mono = true,
        )
        FormToggle(
            title = stringResource(R.string.static_tls_on_every_router),
            subtitle = stringResource(R.string.static_routers_on_this_entrypoint_get_tls),
            checked = form.tlsEnabled,
            onChange = { form = form.copy(tlsEnabled = it) },
        )
        if (form.tlsEnabled) {
            FormField(
                label = stringResource(R.string.static_default_cert_resolver_optional),
                value = form.tlsCertResolver,
                onChange = { form = form.copy(tlsCertResolver = it) },
                placeholder = "cloudflare",
            )
            FormField(
                label = stringResource(R.string.static_default_tls_options_optional),
                value = form.tlsOptions,
                onChange = { form = form.copy(tlsOptions = it) },
                placeholder = "modern@file",
            )
        }
        FormToggle(
            title = stringResource(R.string.static_default_entrypoint),
            subtitle = stringResource(R.string.static_used_by_routers_that_list_no),
            checked = form.asDefault,
            onChange = { form = form.copy(asDefault = it) },
        )

        FormGroup(stringResource(R.string.static_responding_timeouts))
        FormField(stringResource(R.string.static_read), form.readTimeout, { form = form.copy(readTimeout = it) }, placeholder = "60s")
        FormField(stringResource(R.string.static_write), form.writeTimeout, { form = form.copy(writeTimeout = it) }, placeholder = "0")
        FormField(
            label = stringResource(R.string.static_idle),
            value = form.idleTimeout,
            onChange = { form = form.copy(idleTimeout = it) },
            placeholder = "180s",
            help = stringResource(R.string.static_forms_like_30_30s_or_1m30s),
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
        title = stringResource(if (adding) R.string.static_resolver_new else R.string.static_resolver_edit),
        busy = busy,
        error = error,
        onSave = { onSave(form) },
        onDismiss = onDismiss,
        saveLabel = stringResource(if (adding) R.string.static_resolver_add else R.string.static_save_changes),
    ) {
        FormField(stringResource(R.string.common_label_name), form.name, { form = form.copy(name = it) }, placeholder = "cloudflare")
        FormField(stringResource(R.string.static_email), form.email, { form = form.copy(email = it) }, placeholder = "you@example.com")
        FormField(stringResource(R.string.static_storage_path), form.storage, { form = form.copy(storage = it) }, placeholder = "/acme.json")
        FormSelect(
            label = stringResource(R.string.static_challenge_type),
            value = form.challengeType,
            options = ResolverForm.challengeOptions,
            onChange = { form = form.copy(challengeType = it) },
        )
        when (form.challengeType) {
            "dnsChallenge" -> FormField(
                label = stringResource(R.string.static_dns_provider),
                value = form.provider,
                onChange = { form = form.copy(provider = it) },
                placeholder = "cloudflare",
            )
            "httpChallenge" -> FormField(
                label = stringResource(R.string.static_http_entrypoint),
                value = form.httpEntrypoint,
                onChange = { form = form.copy(httpEntrypoint = it) },
                placeholder = "web",
            )
        }
        FormField(
            label = stringResource(R.string.static_ca_server_optional),
            value = form.caServer,
            onChange = { form = form.copy(caServer = it) },
            placeholder = stringResource(R.string.static_ca_placeholder),
        )
        FormSelect(
            label = stringResource(R.string.static_key_type_optional),
            value = form.keyType,
            options = ResolverForm.keyTypeOptions,
            onChange = { form = form.copy(keyType = it) },
        )

        FormGroup(stringResource(R.string.static_external_account_binding))
        FormField(stringResource(R.string.static_eab_key_id_optional), form.eabKid, { form = form.copy(eabKid = it) })
        FormField(
            label = stringResource(R.string.static_eab_hmac_optional),
            value = form.eabHmac,
            onChange = { form = form.copy(eabHmac = it) },
            help = stringResource(R.string.static_both_are_needed_together_or_neither),
        )

        if (form.challengeType == "dnsChallenge") {
            FormGroup(stringResource(R.string.static_dns_checks))
            FormLines(
                label = stringResource(R.string.static_dns_check_resolvers_optional),
                value = form.dnsResolvers,
                onChange = { form = form.copy(dnsResolvers = it) },
                placeholder = "1.1.1.1:53",
                help = stringResource(R.string.static_used_to_verify_the_record_before),
            )
            FormField(
                label = stringResource(R.string.static_propagation_delay_optional),
                value = form.dnsDelay,
                onChange = { form = form.copy(dnsDelay = it) },
                placeholder = "30s",
            )
            FormToggle(
                title = stringResource(R.string.static_disable_propagation_checks),
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
        title = stringResource(if (adding) R.string.static_plugin_new else R.string.plugins_edit_title),
        busy = busy,
        error = error,
        onSave = { onSave(form) },
        onDismiss = onDismiss,
        saveLabel = stringResource(if (adding) R.string.plugins_add_title else R.string.static_save_changes),
    ) {
        FormField(stringResource(R.string.common_label_name), form.name, { form = form.copy(name = it) }, placeholder = "my-plugin")
        FormField(
            label = stringResource(R.string.static_module),
            value = form.moduleName,
            onChange = { form = form.copy(moduleName = it) },
            placeholder = "github.com/user/plugin",
            mono = true,
        )
        if (!form.local) {
            FormField(stringResource(R.string.common_label_version), form.version, { form = form.copy(version = it) }, placeholder = "v1.0.0")
        }
        FormToggle(
            title = stringResource(R.string.static_local_plugin),
            subtitle = stringResource(R.string.static_loaded_from_the_plugins_local_directory),
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
        title = stringResource(R.string.nav_section_providers),
        busy = busy,
        error = error,
        onSave = { onSave(form) },
        onDismiss = onDismiss,
        saveLabel = stringResource(R.string.static_save_changes),
    ) {
        FormToggle("Docker", form.docker, { form = form.copy(docker = it) })
        if (form.docker) {
            FormField(
                label = stringResource(R.string.static_endpoint),
                value = form.dockerEndpoint,
                onChange = { form = form.copy(dockerEndpoint = it) },
                placeholder = "unix:///var/run/docker.sock",
                mono = true,
            )
            FormToggle(
                title = stringResource(R.string.static_expose_by_default),
                subtitle = stringResource(R.string.static_every_container_is_routable_unless_it),
                checked = form.dockerExposedByDefault,
                onChange = { form = form.copy(dockerExposedByDefault = it) },
            )
            FormToggle(stringResource(R.string.static_watch), form.dockerWatch, { form = form.copy(dockerWatch = it) })
        }

        FormGroup("File")
        FormToggle("File", form.file, { form = form.copy(file = it) })
        if (form.file) {
            FormField(
                label = stringResource(R.string.static_directory),
                value = form.fileDirectory,
                onChange = { form = form.copy(fileDirectory = it) },
                placeholder = "/etc/traefik/dynamic",
                mono = true,
            )
            FormToggle(stringResource(R.string.static_watch), form.fileWatch, { form = form.copy(fileWatch = it) })
        }

        FormGroup(stringResource(R.string.static_reloads))
        FormField(
            label = stringResource(R.string.static_providers_throttle_optional),
            value = form.throttle,
            onChange = { form = form.copy(throttle = it) },
            placeholder = "2s",
            help = stringResource(R.string.static_minimum_time_between_config_reloads),
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
        title = stringResource(R.string.static_api_and_dashboard),
        busy = busy,
        error = error,
        onSave = { onSave(form) },
        onDismiss = onDismiss,
        saveLabel = stringResource(R.string.static_save_changes),
    ) {
        FormToggle(
            title = stringResource(R.string.static_api_enabled),
            subtitle = stringResource(R.string.static_api_enabled_body),
            checked = form.enabled,
            onChange = { form = form.copy(enabled = it) },
        )
        FormToggle(stringResource(R.string.static_dashboard), form.dashboard, { form = form.copy(dashboard = it) })
        FormToggle(
            title = stringResource(R.string.static_insecure_mode),
            subtitle = stringResource(R.string.static_exposes_the_api_without_authentication),
            checked = form.insecure,
            onChange = { form = form.copy(insecure = it) },
            danger = true,
        )
        FormToggle(stringResource(R.string.static_debug_mode), form.debug, { form = form.copy(debug = it) })
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
        title = stringResource(R.string.static_logging),
        busy = busy,
        error = error,
        onSave = { onSave(form) },
        onDismiss = onDismiss,
        saveLabel = stringResource(R.string.static_save_changes),
    ) {
        FormSelect(
            label = stringResource(R.string.static_log_level),
            value = form.level,
            options = LogForm.levels.map { it to UiText.Raw(it) },
            onChange = { form = form.copy(level = it) },
        )
        FormSelect(
            label = stringResource(R.string.static_log_format),
            value = form.logFormat,
            options = LogForm.formats,
            onChange = { form = form.copy(logFormat = it) },
        )
        FormField(
            label = stringResource(R.string.static_traefik_log_file),
            value = form.logFile,
            onChange = { form = form.copy(logFile = it) },
            placeholder = "/var/log/traefik/traefik.log",
            help = stringResource(R.string.static_leave_empty_for_stdout),
            mono = true,
        )
        if (form.logFile.isNotBlank()) {
            FormGroup(stringResource(R.string.static_rotation))
            FormField(stringResource(R.string.static_max_size_mb), form.maxSize, { form = form.copy(maxSize = it) }, numeric = true)
            FormField(stringResource(R.string.static_max_backups), form.maxBackups, { form = form.copy(maxBackups = it) }, numeric = true)
            FormField(stringResource(R.string.static_max_age_days), form.maxAge, { form = form.copy(maxAge = it) }, numeric = true)
            FormToggle(stringResource(R.string.static_compress_rotated_files), form.compress, { form = form.copy(compress = it) })
        }

        FormGroup(stringResource(R.string.static_access_log))
        FormToggle(
            title = stringResource(R.string.static_access_log),
            subtitle = stringResource(R.string.static_without_it_the_logs_tab_has),
            checked = form.accessLog,
            onChange = { form = form.copy(accessLog = it) },
        )
        if (form.accessLog) {
            FormField(
                label = stringResource(R.string.static_access_log_file),
                value = form.accessLogPath,
                onChange = { form = form.copy(accessLogPath = it) },
                placeholder = "/var/log/traefik/access.log",
                help = stringResource(R.string.static_empty_for_stdout),
                mono = true,
            )
            FormSelect(
                label = stringResource(R.string.static_format),
                value = form.alFormat,
                options = LogForm.accessFormats,
                onChange = { form = form.copy(alFormat = it) },
            )
            FormField(
                label = stringResource(R.string.static_status_code_filter_optional),
                value = form.alStatusCodes,
                onChange = { form = form.copy(alStatusCodes = it) },
                placeholder = "400-499, 500",
                help = stringResource(R.string.static_only_log_these_responses_comma_separated),
                mono = true,
            )
            FormField(
                label = stringResource(R.string.static_min_duration_filter_optional),
                value = form.alMinDuration,
                onChange = { form = form.copy(alMinDuration = it) },
                placeholder = "200ms",
                help = stringResource(R.string.static_only_log_requests_slower_than_this),
            )
            FormField(
                label = stringResource(R.string.static_buffering_lines_optional),
                value = form.alBuffering,
                onChange = { form = form.copy(alBuffering = it) },
                placeholder = "100",
                numeric = true,
            )
            FormSelect(
                label = stringResource(R.string.static_headers),
                value = form.alHeadersMode,
                options = LogForm.headerModes,
                onChange = { form = form.copy(alHeadersMode = it) },
            )
            FormToggle(stringResource(R.string.static_only_log_retry_attempts), form.alRetry, { form = form.copy(alRetry = it) })
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
        title = stringResource(R.string.nav_section_observability),
        busy = busy,
        error = error,
        onSave = { onSave(form) },
        onDismiss = onDismiss,
        saveLabel = stringResource(R.string.static_save_changes),
    ) {
        FormToggle(
            title = stringResource(R.string.static_ping_endpoint),
            subtitle = stringResource(R.string.static_ping_help),
            checked = form.ping,
            onChange = { form = form.copy(ping = it) },
        )
        FormToggle(
            title = stringResource(R.string.static_prometheus_metrics),
            subtitle = "/metrics",
            checked = form.prometheus,
            onChange = { form = form.copy(prometheus = it) },
        )
        if (form.prometheus) {
            FormToggle(stringResource(R.string.static_entrypoint_labels), form.promEpLabels, { form = form.copy(promEpLabels = it) })
            FormToggle(stringResource(R.string.static_router_labels), form.promRouterLabels, { form = form.copy(promRouterLabels = it) })
            FormToggle(stringResource(R.string.static_service_labels), form.promSvcLabels, { form = form.copy(promSvcLabels = it) })
        }

        FormGroup(stringResource(R.string.static_tracing))
        FormToggle(
            title = stringResource(R.string.static_tracing),
            subtitle = "OTLP",
            checked = form.tracing,
            onChange = { form = form.copy(tracing = it) },
        )
        if (form.tracing) {
            FormField(
                label = stringResource(R.string.static_service_name_optional),
                value = form.traceService,
                onChange = { form = form.copy(traceService = it) },
                placeholder = "traefik",
            )
            FormField(
                label = stringResource(R.string.static_sample_rate_optional),
                value = form.traceSample,
                onChange = { form = form.copy(traceSample = it) },
                placeholder = "1.0",
                help = stringResource(R.string.static_between_0_and_1),
            )
            FormField(
                label = stringResource(R.string.static_otlp_http_endpoint_optional),
                value = form.traceEndpoint,
                onChange = { form = form.copy(traceEndpoint = it) },
                placeholder = "http://collector:4318/v1/traces",
                help = stringResource(R.string.static_defaults_to_localhost_4318),
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
        title = stringResource(R.string.nav_section_system),
        busy = busy,
        error = error,
        onSave = { onSave(form) },
        onDismiss = onDismiss,
        saveLabel = stringResource(R.string.static_save_changes),
    ) {
        FormToggle(
            title = stringResource(R.string.static_check_for_new_traefik_versions),
            checked = form.checkNewVersion,
            onChange = { form = form.copy(checkNewVersion = it) },
        )
        FormToggle(
            title = stringResource(R.string.static_send_anonymous_usage_statistics),
            checked = form.sendUsage,
            onChange = { form = form.copy(sendUsage = it) },
        )
        FormSelect(
            label = stringResource(R.string.static_default_rule_syntax),
            value = form.ruleSyntax,
            options = SystemForm.ruleSyntaxOptions,
            onChange = { form = form.copy(ruleSyntax = it) },
            help = stringResource(R.string.static_only_change_this_while_migrating_rules),
        )

        FormGroup(stringResource(R.string.static_servers_transport_defaults))
        FormToggle(
            title = stringResource(R.string.static_skip_backend_tls_verification),
            subtitle = stringResource(R.string.static_insecure),
            checked = form.stInsecure,
            onChange = { form = form.copy(stInsecure = it) },
            danger = true,
        )
        FormLines(
            label = stringResource(R.string.static_root_cas_optional),
            value = form.stRootCAs,
            onChange = { form = form.copy(stRootCAs = it) },
            placeholder = "/certs/internal-ca.pem",
            help = stringResource(R.string.static_one_path_per_line),
        )
        FormField(
            label = stringResource(R.string.static_max_idle_conns_per_host_optional),
            value = form.stMaxIdle,
            onChange = { form = form.copy(stMaxIdle = it) },
            placeholder = "200",
            numeric = true,
        )

        FormGroup(stringResource(R.string.static_forwarding_timeouts))
        FormField(stringResource(R.string.static_dial), form.stDial, { form = form.copy(stDial = it) }, placeholder = "30s")
        FormField(stringResource(R.string.static_response_header), form.stRespHeader, { form = form.copy(stRespHeader = it) }, placeholder = "0")
        FormField(stringResource(R.string.static_idle_connection), form.stIdleConn, { form = form.copy(stIdleConn = it) }, placeholder = "90s")
    }
}
