package dev.chr0nzz.traefikmanager.data.model

import dev.chr0nzz.traefikmanager.R

sealed interface WizardField {
    val key: String
    val label: UiText

    data class Text(
        override val key: String,
        override val label: UiText,
        val placeholder: String = "",
        val default: String = "",
        val help: UiText? = null,
        val numeric: Boolean = false,
        val secret: Boolean = false,
    ) : WizardField

    data class Lines(
        override val key: String,
        override val label: UiText,
        val placeholder: String = "",
        val help: UiText? = null,
        val default: String = "",
    ) : WizardField

    data class Toggle(
        override val key: String,
        override val label: UiText,
        val default: Boolean = false,
    ) : WizardField

    data class Choice(
        override val key: String,
        override val label: UiText,
        val options: List<Pair<String, UiText>>,
        val default: String,
    ) : WizardField
}

class WizardValues(
    private val text: Map<String, String>,
    private val toggles: Map<String, Boolean>,
    private val fields: List<WizardField>,
) {
    fun prefilled(key: String): String {
        val raw = text[key]
        if (raw != null) return raw.trim()
        return fields.filterIsInstance<WizardField.Text>().firstOrNull { it.key == key }?.default.orEmpty()
    }

    fun value(key: String, fallback: String = ""): String {
        val raw = text[key]?.trim()
        if (!raw.isNullOrEmpty()) return raw
        val declared = fields.filterIsInstance<WizardField.Text>().firstOrNull { it.key == key }?.default
        val choice = fields.filterIsInstance<WizardField.Choice>().firstOrNull { it.key == key }?.default
        return (declared ?: choice)?.takeIf { it.isNotEmpty() } ?: fallback
    }

    fun lines(key: String): List<String> {
        val raw = text[key]
            ?: fields.filterIsInstance<WizardField.Lines>().firstOrNull { it.key == key }?.default
            ?: ""
        return raw.split('\n').map(String::trim).filter(String::isNotEmpty)
    }

    fun flag(key: String): Boolean {
        toggles[key]?.let { return it }
        return fields.filterIsInstance<WizardField.Toggle>().firstOrNull { it.key == key }?.default ?: false
    }

    fun int(key: String, fallback: Int): Int = value(key).toIntOrNull() ?: fallback
}

data class MiddlewareWizard(
    val id: String,
    val label: UiText,
    val category: String,
    val fields: List<WizardField>,
    val build: (WizardValues) -> String,
)

private fun yamlString(value: String): String {
    val escaped = value.replace("\\", "\\\\").replace("\"", "\\\"")
    return "\"$escaped\""
}

private fun listBlock(indent: String, items: List<String>, quoted: Boolean = true): String =
    items.joinToString("\n") { "$indent- " + if (quoted) yamlString(it) else it }

object MiddlewareTemplates {

    const val CATEGORY_AUTH = "Auth"
    const val CATEGORY_SECURITY = "Security"
    const val CATEGORY_ROUTING = "Routing"
    const val CATEGORY_ADVANCED = "Advanced"

    private val basicAuthFields = listOf(
        WizardField.Lines(
            key = "users",
            label = UiText.of(R.string.mw_field_users),
            placeholder = "admin:\$apr1\$H6uskkkW\$IgXLP6ewTrSuBkTrqE8",
            help = UiText.of(R.string.mw_help_one_per_line_format_user_htpasswd),
        ),
        WizardField.Text(key = "realm", label = UiText.of(R.string.mw_field_realm_optional), placeholder = "My Protected Area"),
    )

    private val forwardAuthFields = listOf(
        WizardField.Text(key = "address", label = UiText.of(R.string.mw_field_auth_service_url), placeholder = "http://auth-service:4181"),
        WizardField.Toggle(key = "trust", label = UiText.of(R.string.mw_field_trust_forward_header), default = true),
        WizardField.Lines(
            key = "headers",
            label = UiText.of(R.string.mw_field_auth_response_headers),
            placeholder = "X-Auth-User\nX-Auth-Token",
            help = UiText.of(R.string.middlewares_form_one_per_line),
        ),
        WizardField.Text(
            key = "maxBody",
            label = UiText.of(R.string.mw_field_max_response_body_size),
            placeholder = "4096",
            default = "4096",
            help = UiText.of(R.string.mw_help_bytes_traefik_3_7_warns_on),
            numeric = true,
        ),
    )

    private val ipAllowListFields = listOf(
        WizardField.Lines(
            key = "cidrs",
            label = UiText.of(R.string.mw_field_allowed_cidrs),
            placeholder = "192.168.1.0/24\n10.0.0.0/8",
            help = UiText.of(R.string.middlewares_form_one_per_line),
        ),
        WizardField.Choice(
            key = "strategy",
            label = UiText.of(R.string.mw_field_client_ip_source),
            options = listOf(
                "direct" to UiText.of(R.string.mw_field_direct_connection),
                "depth" to UiText.of(R.string.mw_field_trusted_hop_depth),
                "excluded" to UiText.of(R.string.mw_field_excluded_proxy_ips),
            ),
            default = "direct",
        ),
        WizardField.Text(key = "depth", label = UiText.of(R.string.mw_field_trusted_hop_depth), default = "1", numeric = true),
        WizardField.Lines(
            key = "excluded",
            label = UiText.of(R.string.mw_field_proxy_ips_to_exclude),
            placeholder = "173.245.48.0/20",
            help = UiText.of(R.string.middlewares_form_one_per_line),
        ),
    )

    private val stripPrefixRegexFields = listOf(
        WizardField.Lines(
            key = "regex",
            label = UiText.of(R.string.mw_field_regexes),
            placeholder = "^/api/v[0-9]+",
            help = UiText.of(R.string.mw_help_one_per_line_the_matched_prefix),
        ),
    )

    private val replacePathRegexFields = listOf(
        WizardField.Text(key = "regex", label = UiText.of(R.string.mw_field_regex), placeholder = "^/foo/(.*)"),
        WizardField.Text(
            key = "replacement",
            label = UiText.of(R.string.mw_field_replacement),
            placeholder = "/bar/$1",
            help = UiText.of(R.string.mw_help_capture_groups_are_1_2_and),
        ),
    )

    private val errorsFields = listOf(
        WizardField.Lines(
            key = "status",
            label = UiText.of(R.string.mw_field_status_codes),
            placeholder = "500-599",
            help = UiText.of(R.string.mw_help_one_per_line_single_codes_or),
        ),
        WizardField.Text(
            key = "service",
            label = UiText.of(R.string.mw_field_service),
            placeholder = "error-pages",
            help = UiText.of(R.string.mw_help_the_service_that_serves_the_error),
        ),
        WizardField.Text(
            key = "query",
            label = UiText.of(R.string.mw_field_query),
            placeholder = "/{status}.html",
            help = UiText.of(R.string.mw_help_optional_status_is_replaced_with_the),
        ),
    )

    private val contentTypeFields = listOf(
        WizardField.Toggle(
            key = "autoDetect",
            label = UiText.of(R.string.mw_field_auto_detect_the_content_type),
            default = false,
        ),
    )

    private val grpcWebFields = listOf(
        WizardField.Lines(
            key = "allowOrigins",
            label = UiText.of(R.string.mw_field_allowed_origins),
            placeholder = "https://app.example.com",
            help = UiText.of(R.string.mw_help_one_per_line_converts_grpc_web),
        ),
    )

    private val passTlsClientCertFields = listOf(
        WizardField.Toggle(key = "pem", label = UiText.of(R.string.mw_field_pass_the_whole_certificate), default = true),
        WizardField.Toggle(key = "info", label = UiText.of(R.string.mw_field_also_pass_selected_certificate_fields)),
        WizardField.Toggle(key = "subjectCN", label = UiText.of(R.string.mw_field_subject_common_name), default = true),
        WizardField.Toggle(key = "issuerCN", label = UiText.of(R.string.mw_field_issuer_common_name)),
        WizardField.Toggle(key = "serial", label = UiText.of(R.string.mw_field_serial_number)),
        WizardField.Toggle(key = "notAfter", label = UiText.of(R.string.mw_field_expiry_date)),
    )

    val all: List<MiddlewareWizard> = listOf(
        MiddlewareWizard("basicAuth", UiText.of(R.string.mw_template_basic_auth), CATEGORY_AUTH, basicAuthFields) { values ->
            buildString {
                append("basicAuth:\n  users:\n")
                append(listBlock("    ", values.lines("users")))
                val realm = values.value("realm")
                if (realm.isNotEmpty()) append("\n  realm: ${yamlString(realm)}")
            }
        },
        MiddlewareWizard(
            "digestAuth",
            UiText.of(R.string.mw_template_digest_auth),
            CATEGORY_AUTH,
            listOf(
                WizardField.Lines(
                    key = "users",
                    label = UiText.of(R.string.mw_field_users),
                    placeholder = "admin:traefik:a2688e031edb4be6fe3079ef99",
                    help = UiText.of(R.string.mw_help_one_per_line_format_user_realm),
                ),
            ),
        ) { values ->
            "digestAuth:\n  users:\n" + listBlock("    ", values.lines("users"))
        },
        MiddlewareWizard("forwardAuth", UiText.of(R.string.mw_template_forward_auth), CATEGORY_AUTH, forwardAuthFields, ::buildForwardAuth),
        MiddlewareWizard(
            "forwardAuthAuthentik",
            UiText.of(R.string.mw_template_forward_auth_authentik),
            CATEGORY_AUTH,
            forwardAuthFields.withDefaults(
                "address" to "http://authentik-server:9000/outpost.goauthentik.io/auth/traefik",
                "headers" to "X-authentik-username\nX-authentik-groups\nX-authentik-email\nX-authentik-name\nX-authentik-uid",
            ),
            ::buildForwardAuth,
        ),
        MiddlewareWizard(
            "forwardAuthAuthelia",
            UiText.of(R.string.mw_template_forward_auth_authelia),
            CATEGORY_AUTH,
            forwardAuthFields.withDefaults(
                "address" to "http://authelia:9091/api/verify?rd=https://auth.example.com",
                "headers" to "Remote-User\nRemote-Groups\nRemote-Name\nRemote-Email",
            ),
            ::buildForwardAuth,
        ),
        MiddlewareWizard(
            "forwardAuthGatekeeper",
            UiText.of(R.string.mw_template_forward_auth_gatekeeper),
            CATEGORY_AUTH,
            listOf(
                WizardField.Text(key = "url", label = UiText.of(R.string.mw_field_gatekeeper_url), placeholder = "https://auth.example.com"),
                WizardField.Text(key = "policy", label = UiText.of(R.string.mw_field_policy_optional), placeholder = "admin"),
                WizardField.Toggle(key = "trust", label = UiText.of(R.string.mw_field_trust_forward_header), default = false),
                WizardField.Toggle(key = "authorization", label = UiText.of(R.string.mw_field_forward_authorization_header), default = false),
                WizardField.Lines(key = "headers", label = UiText.of(R.string.mw_field_auth_response_headers), help = UiText.of(R.string.middlewares_form_one_per_line)),
                WizardField.Text(
                    key = "maxBody",
                    label = UiText.of(R.string.mw_field_max_response_body_size),
                    placeholder = "4096",
                    default = "4096",
                    help = UiText.of(R.string.mw_help_bytes_traefik_3_7_warns_on),
                    numeric = true,
                ),
            ),
        ) { values ->
            val url = values.value("url").trimEnd('/')
            val policy = values.value("policy")
            val address = if (url.isEmpty()) {
                ""
            } else {
                url + "/auth/verify" + if (policy.isNotEmpty()) "?policy=$policy" else ""
            }
            val headers = values.lines("headers")
            val all = if (values.flag("authorization")) {
                listOf("Authorization") + headers.filterNot { it == "Authorization" }
            } else {
                headers
            }
            buildString {
                append("forwardAuth:\n  address: ${yamlString(address)}\n")
                append("  trustForwardHeader: ${values.flag("trust")}")
                if (all.isNotEmpty()) append("\n  authResponseHeaders:\n" + listBlock("    ", all))
                val maxBody = values.prefilled("maxBody")
                if (maxBody.toIntOrNull() != null) append("\n  maxResponseBodySize: $maxBody")
            }
        },
        MiddlewareWizard(
            "oidcAuth",
            UiText.of(R.string.mw_template_oidc_auth_traefik_oidc_auth),
            CATEGORY_AUTH,
            listOf(
                WizardField.Text(key = "providerUrl", label = UiText.of(R.string.mw_field_provider_url), placeholder = "https://login.example.com"),
                WizardField.Text(key = "clientId", label = UiText.of(R.string.mw_field_client_id), placeholder = "client-id"),
                WizardField.Text(key = "clientSecret", label = UiText.of(R.string.mw_field_client_secret), placeholder = "client-secret", secret = true),
                WizardField.Text(
                    key = "secret",
                    label = UiText.of(R.string.mw_field_session_secret),
                    placeholder = "change-me-with-own-32-character-secret",
                    help = UiText.of(R.string.mw_help_32_character_random_string),
                    secret = true,
                ),
                WizardField.Lines(key = "scopes", label = UiText.of(R.string.mw_field_scopes), help = UiText.of(R.string.middlewares_form_one_per_line)),
                WizardField.Text(key = "maxAge", label = UiText.of(R.string.mw_field_session_max_age_seconds), default = "86400", numeric = true),
                WizardField.Lines(
                    key = "headers",
                    label = UiText.of(R.string.mw_field_forward_claims_as_headers),
                    help = UiText.of(R.string.mw_help_one_per_line_format_name_claim),
                ),
                WizardField.Lines(
                    key = "bypass",
                    label = UiText.of(R.string.mw_field_bypass_rules_optional),
                    placeholder = "^/health$\n^/metrics$",
                    help = UiText.of(R.string.mw_help_regex_one_per_line),
                ),
            ),
        ) { values ->
            buildString {
                append("plugin:\n  traefik-oidc-auth:")
                val secret = values.value("secret")
                if (secret.isNotEmpty()) append("\n    Secret: ${yamlString(secret)}")
                append("\n    Provider:")
                val providerUrl = values.value("providerUrl")
                if (providerUrl.isNotEmpty()) append("\n      Url: ${yamlString(providerUrl)}")
                val clientId = values.value("clientId")
                if (clientId.isNotEmpty()) append("\n      ClientId: ${yamlString(clientId)}")
                val clientSecret = values.value("clientSecret")
                if (clientSecret.isNotEmpty()) append("\n      ClientSecret: ${yamlString(clientSecret)}")
                val scopes = values.lines("scopes")
                if (scopes.isNotEmpty()) append("\n    Scopes:\n" + listBlock("      ", scopes, quoted = false))
                append("\n    SessionCookie:\n      MaxAge: ${values.int("maxAge", 86400)}")
                val headers = values.lines("headers").mapNotNull { line ->
                    val index = line.indexOf(':')
                    if (index < 0) return@mapNotNull null
                    val name = line.take(index).trim()
                    val claim = line.drop(index + 1).trim()
                    name to "{{`{{ .claims.$claim }}`}}"
                }
                if (headers.isNotEmpty()) {
                    append("\n    Headers:\n")
                    append(
                        headers.joinToString("\n") { (name, value) ->
                            "      - Name: ${yamlString(name)}\n        Value: ${yamlString(value)}"
                        },
                    )
                }
                val bypass = values.lines("bypass")
                if (bypass.isNotEmpty()) append("\n    BypassAuthenticationRule:\n" + listBlock("      ", bypass))
            }
        },
        MiddlewareWizard("ipAllowList", UiText.of(R.string.mw_template_ip_allow_list), CATEGORY_SECURITY, ipAllowListFields, ::buildIpAllowList),
        MiddlewareWizard(
            "ipAllowListPrivate",
            UiText.of(R.string.mw_template_ip_allow_list_private_ranges),
            CATEGORY_SECURITY,
            ipAllowListFields.withDefaults("cidrs" to "10.0.0.0/8\n172.16.0.0/12\n192.168.0.0/16\n127.0.0.1/32"),
            ::buildIpAllowList,
        ),
        MiddlewareWizard(
            "rateLimit",
            UiText.of(R.string.mw_template_rate_limit),
            CATEGORY_SECURITY,
            listOf(
                WizardField.Text(key = "average", label = UiText.of(R.string.mw_field_average_req_s), default = "100", numeric = true),
                WizardField.Text(key = "burst", label = UiText.of(R.string.mw_field_burst), default = "50", numeric = true),
                WizardField.Text(key = "period", label = UiText.of(R.string.mw_field_period), default = "1s"),
            ),
        ) { values ->
            "rateLimit:\n  average: ${values.value("average", "100")}\n" +
                "  burst: ${values.value("burst", "50")}\n  period: ${values.value("period", "1s")}"
        },
        MiddlewareWizard(
            "secureHeaders",
            UiText.of(R.string.mw_template_secure_headers),
            CATEGORY_SECURITY,
            listOf(
                WizardField.Toggle(key = "ssl", label = UiText.of(R.string.mw_field_ssl_redirect_force_https), default = true),
                WizardField.Toggle(key = "hsts", label = UiText.of(R.string.mw_field_hsts_strict_transport_security), default = true),
                WizardField.Text(key = "hstsAge", label = UiText.of(R.string.mw_field_hsts_max_age_seconds), default = "315360000", numeric = true),
                WizardField.Toggle(key = "subdomains", label = UiText.of(R.string.mw_field_include_subdomains), default = true),
                WizardField.Toggle(key = "preload", label = UiText.of(R.string.mw_field_preload), default = true),
                WizardField.Toggle(key = "nosniff", label = UiText.of(R.string.mw_field_x_content_type_options_nosniff), default = true),
                WizardField.Toggle(key = "xss", label = UiText.Raw("X-XSS-Protection"), default = true),
                WizardField.Toggle(key = "frame", label = UiText.of(R.string.mw_field_x_frame_options_deny), default = true),
                WizardField.Toggle(key = "referrer", label = UiText.of(R.string.mw_field_same_origin_referrer_policy), default = false),
            ),
        ) { values ->
            buildList {
                add("headers:")
                if (values.flag("ssl")) add("  sslRedirect: true")
                if (values.flag("hsts")) {
                    add("  forceSTSHeader: true")
                    add("  stsSeconds: ${values.value("hstsAge", "315360000")}")
                    if (values.flag("subdomains")) add("  stsIncludeSubdomains: true")
                    if (values.flag("preload")) add("  stsPreload: true")
                }
                if (values.flag("nosniff")) add("  contentTypeNosniff: true")
                if (values.flag("xss")) add("  browserXssFilter: true")
                if (values.flag("frame")) add("  frameDeny: true")
                if (values.flag("referrer")) add("  referrerPolicy: \"same-origin\"")
            }.joinToString("\n")
        },
        MiddlewareWizard(
            "corsHeaders",
            UiText.of(R.string.mw_template_cors_headers),
            CATEGORY_SECURITY,
            listOf(
                WizardField.Toggle(key = "GET", label = UiText.Raw("GET"), default = true),
                WizardField.Toggle(key = "POST", label = UiText.Raw("POST"), default = true),
                WizardField.Toggle(key = "PUT", label = UiText.Raw("PUT"), default = true),
                WizardField.Toggle(key = "DELETE", label = UiText.Raw("DELETE"), default = true),
                WizardField.Toggle(key = "PATCH", label = UiText.Raw("PATCH"), default = true),
                WizardField.Toggle(key = "OPTIONS", label = UiText.Raw("OPTIONS"), default = true),
                WizardField.Toggle(key = "HEAD", label = UiText.Raw("HEAD"), default = false),
                WizardField.Lines(
                    key = "origins",
                    label = UiText.of(R.string.mw_field_allowed_origins),
                    placeholder = "https://example.com",
                    help = UiText.of(R.string.middlewares_form_one_per_line),
                ),
                WizardField.Lines(key = "headers", label = UiText.of(R.string.mw_field_allowed_headers), placeholder = "*", help = UiText.of(R.string.middlewares_form_one_per_line)),
                WizardField.Text(key = "maxAge", label = UiText.of(R.string.mw_field_max_age_seconds), default = "100", numeric = true),
                WizardField.Toggle(key = "vary", label = UiText.of(R.string.mw_field_add_vary_header), default = true),
            ),
        ) { values ->
            val methods = listOf("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS", "HEAD")
                .filter { values.flag(it) }
            buildList {
                add("headers:")
                if (methods.isNotEmpty()) {
                    add("  accessControlAllowMethods:\n" + listBlock("    ", methods, quoted = false))
                }
                val headers = values.lines("headers")
                if (headers.isNotEmpty()) add("  accessControlAllowHeaders:\n" + listBlock("    ", headers))
                val origins = values.lines("origins")
                if (origins.isNotEmpty()) add("  accessControlAllowOriginList:\n" + listBlock("    ", origins))
                add("  accessControlMaxAge: ${values.value("maxAge", "100")}")
                if (values.flag("vary")) add("  addVaryHeader: true")
            }.joinToString("\n")
        },
        MiddlewareWizard(
            "encodedCharacters",
            UiText.of(R.string.mw_template_encoded_characters_traefik_3_7),
            CATEGORY_SECURITY,
            listOf(
                WizardField.Toggle(key = "allowEncodedSlash", label = UiText.of(R.string.mw_field_allow_encoded_slash_2f)),
                WizardField.Toggle(key = "allowEncodedBackSlash", label = UiText.of(R.string.mw_field_allow_encoded_backslash_5c)),
                WizardField.Toggle(key = "allowEncodedSemicolon", label = UiText.of(R.string.mw_field_allow_encoded_semicolon_3b)),
                WizardField.Toggle(key = "allowEncodedPercent", label = UiText.of(R.string.mw_field_allow_encoded_percent_25)),
                WizardField.Toggle(key = "allowEncodedQuestionMark", label = UiText.of(R.string.mw_field_allow_encoded_question_mark_3f)),
                WizardField.Toggle(key = "allowEncodedHash", label = UiText.of(R.string.mw_field_allow_encoded_hash_23)),
            ),
        ) { values ->
            val enabled = listOf(
                "allowEncodedSlash",
                "allowEncodedBackSlash",
                "allowEncodedSemicolon",
                "allowEncodedPercent",
                "allowEncodedQuestionMark",
                "allowEncodedHash",
            ).filter { values.flag(it) }
            if (enabled.isEmpty()) {
                "encodedCharacters: {}"
            } else {
                "encodedCharacters:\n" + enabled.joinToString("\n") { "  $it: true" }
            }
        },
        MiddlewareWizard(
            "redirectScheme",
            UiText.of(R.string.mw_template_redirect_to_https),
            CATEGORY_ROUTING,
            listOf(
                WizardField.Choice(
                    key = "scheme",
                    label = UiText.of(R.string.mw_field_scheme),
                    options = listOf("https" to UiText.Raw("https"), "http" to UiText.Raw("http")),
                    default = "https",
                ),
                WizardField.Toggle(key = "permanent", label = UiText.of(R.string.mw_field_permanent_redirect_301), default = true),
            ),
        ) { values ->
            "redirectScheme:\n  scheme: ${values.value("scheme", "https")}\n  permanent: ${values.flag("permanent")}"
        },
        MiddlewareWizard(
            "redirectRegex",
            UiText.of(R.string.mw_template_redirect_regex),
            CATEGORY_ROUTING,
            listOf(
                WizardField.Text(key = "regex", label = UiText.of(R.string.mw_field_regex), placeholder = "^http://(.*)"),
                WizardField.Text(key = "replacement", label = UiText.of(R.string.mw_field_replacement), placeholder = "https://\${1}"),
                WizardField.Toggle(key = "permanent", label = UiText.of(R.string.mw_field_permanent_redirect_301), default = true),
            ),
        ) { values ->
            "redirectRegex:\n  regex: ${yamlString(values.value("regex"))}\n" +
                "  replacement: ${yamlString(values.value("replacement"))}\n" +
                "  permanent: ${values.flag("permanent")}"
        },
        MiddlewareWizard(
            "stripPrefix",
            UiText.of(R.string.mw_template_strip_prefix),
            CATEGORY_ROUTING,
            listOf(WizardField.Lines(key = "prefixes", label = UiText.of(R.string.mw_field_prefixes), placeholder = "/api\n/v1", help = UiText.of(R.string.middlewares_form_one_per_line))),
        ) { values ->
            "stripPrefix:\n  prefixes:\n" + listBlock("    ", values.lines("prefixes"))
        },
        MiddlewareWizard(
            "addPrefix",
            UiText.of(R.string.mw_template_add_prefix),
            CATEGORY_ROUTING,
            listOf(WizardField.Text(key = "prefix", label = UiText.of(R.string.mw_field_prefix), placeholder = "/api")),
        ) { values ->
            "addPrefix:\n  prefix: ${yamlString(values.value("prefix"))}"
        },
        MiddlewareWizard(
            "replacePath",
            UiText.of(R.string.mw_template_replace_path),
            CATEGORY_ROUTING,
            listOf(WizardField.Text(key = "path", label = UiText.of(R.string.mw_field_path), placeholder = "/foo")),
        ) { values ->
            "replacePath:\n  path: ${yamlString(values.value("path"))}"
        },
        MiddlewareWizard(
            "compress",
            UiText.of(R.string.mw_template_gzip_compress),
            CATEGORY_ADVANCED,
            listOf(
                WizardField.Text(
                    key = "min",
                    label = UiText.of(R.string.mw_field_min_response_body_bytes),
                    default = "1200",
                    help = UiText.of(R.string.mw_help_responses_smaller_than_this_are_not),
                    numeric = true,
                ),
            ),
        ) { values ->
            "compress:\n  minResponseBodyBytes: ${values.value("min", "1200")}"
        },
        MiddlewareWizard(
            "retry",
            UiText.of(R.string.mw_template_retry),
            CATEGORY_ADVANCED,
            listOf(
                WizardField.Text(key = "attempts", label = UiText.of(R.string.mw_field_attempts), default = "4", numeric = true),
                WizardField.Text(key = "interval", label = UiText.of(R.string.mw_field_initial_interval), default = "100ms"),
            ),
        ) { values ->
            "retry:\n  attempts: ${values.value("attempts", "4")}\n" +
                "  initialInterval: ${values.value("interval", "100ms")}"
        },
        MiddlewareWizard(
            "circuitBreaker",
            UiText.of(R.string.mw_template_circuit_breaker),
            CATEGORY_ADVANCED,
            listOf(
                WizardField.Text(
                    key = "expression",
                    label = UiText.of(R.string.mw_field_expression),
                    placeholder = "NetworkErrorRatio() > 0.30",
                ),
            ),
        ) { values ->
            "circuitBreaker:\n  expression: ${yamlString(values.value("expression"))}"
        },
        MiddlewareWizard(
            "buffering",
            UiText.of(R.string.mw_template_buffering),
            CATEGORY_ADVANCED,
            listOf(
                WizardField.Text(key = "request", label = UiText.of(R.string.mw_field_max_request_body_bytes), default = "10485760", numeric = true),
                WizardField.Text(key = "response", label = UiText.of(R.string.mw_field_max_response_body_bytes), default = "10485760", numeric = true),
                WizardField.Text(key = "retry", label = UiText.of(R.string.mw_field_retry_expression_optional)),
            ),
        ) { values ->
            buildString {
                append("buffering:\n  maxRequestBodyBytes: ${values.value("request", "10485760")}\n")
                append("  maxResponseBodyBytes: ${values.value("response", "10485760")}")
                val retry = values.value("retry")
                if (retry.isNotEmpty()) append("\n  retryExpression: ${yamlString(retry)}")
            }
        },
        MiddlewareWizard(
            "chain",
            UiText.of(R.string.mw_template_middleware_chain),
            CATEGORY_ADVANCED,
            listOf(
                WizardField.Lines(
                    key = "middlewares",
                    label = UiText.of(R.string.mw_field_middlewares),
                    placeholder = "redirect-https\nsecure-headers",
                    help = UiText.of(R.string.mw_help_one_per_line_applied_in_order),
                ),
            ),
        ) { values ->
            "chain:\n  middlewares:\n" + listBlock("    ", values.lines("middlewares"), quoted = false)
        },
        MiddlewareWizard(
            "inFlightReq",
            UiText.of(R.string.mw_template_in_flight_limit),
            CATEGORY_ADVANCED,
            listOf(WizardField.Text(key = "amount", label = UiText.of(R.string.mw_field_max_in_flight_requests), default = "10", numeric = true)),
        ) { values ->
            "inFlightReq:\n  amount: ${values.value("amount", "10")}"
        },
        MiddlewareWizard(
            "stripPrefixRegex",
            UiText.of(R.string.mw_template_strip_prefix_regex),
            CATEGORY_ROUTING,
            stripPrefixRegexFields,
        ) { values ->
            "stripPrefixRegex:\n  regex:\n" + listBlock("    ", values.lines("regex"))
        },
        MiddlewareWizard(
            "replacePathRegex",
            UiText.of(R.string.mw_template_replace_path_regex),
            CATEGORY_ROUTING,
            replacePathRegexFields,
        ) { values ->
            "replacePathRegex:\n  regex: " + yamlString(values.value("regex")) +
                "\n  replacement: " + yamlString(values.value("replacement"))
        },
        MiddlewareWizard("errors", UiText.of(R.string.mw_template_custom_error_pages), CATEGORY_ADVANCED, errorsFields) { values ->
            buildString {
                append("errors:\n  status:\n")
                append(listBlock("    ", values.lines("status")))
                append("\n  service: ")
                append(yamlString(values.value("service")))
                val query = values.value("query")
                if (query.isNotBlank()) {
                    append("\n  query: ")
                    append(yamlString(query))
                }
            }
        },
        MiddlewareWizard("contentType", UiText.of(R.string.mw_template_content_type), CATEGORY_ADVANCED, contentTypeFields) { values ->
            "contentType:\n  autoDetect: " + values.flag("autoDetect")
        },
        MiddlewareWizard("grpcWeb", UiText.Raw("gRPC-Web"), CATEGORY_ADVANCED, grpcWebFields) { values ->
            "grpcWeb:\n  allowOrigins:\n" + listBlock("    ", values.lines("allowOrigins"))
        },
        MiddlewareWizard(
            "passTLSClientCert",
            UiText.of(R.string.mw_template_pass_tls_client_cert),
            CATEGORY_ADVANCED,
            passTlsClientCertFields,
        ) { values ->
            buildString {
                append("passTLSClientCert:\n  pem: ")
                append(values.flag("pem"))
                if (values.flag("info")) {
                    append("\n  info:")
                    if (values.flag("serial")) append("\n    serialNumber: true")
                    if (values.flag("notAfter")) append("\n    notAfter: true")
                    if (values.flag("subjectCN")) append("\n    subject:\n      commonName: true")
                    if (values.flag("issuerCN")) append("\n    issuer:\n      commonName: true")
                }
            }
        },
    )

    val categories: List<String> = listOf(CATEGORY_AUTH, CATEGORY_SECURITY, CATEGORY_ROUTING, CATEGORY_ADVANCED)

    fun byId(id: String): MiddlewareWizard? = all.firstOrNull { it.id == id }

    fun kindOf(yaml: String): String {
        val first = yaml.lineSequence()
            .map(String::trim)
            .firstOrNull { it.isNotEmpty() && !it.startsWith("#") }
            ?: return "middleware"
        return first.substringBefore(':').trim().ifEmpty { "middleware" }
    }
}

private fun buildForwardAuth(values: WizardValues): String = buildString {
    append("forwardAuth:\n  address: ${yamlStringOf(values.value("address"))}\n")
    append("  trustForwardHeader: ${values.flag("trust")}")
    val headers = values.lines("headers")
    if (headers.isNotEmpty()) {
        append("\n  authResponseHeaders:\n")
        append(headers.joinToString("\n") { "    - " + yamlStringOf(it) })
    }
    val maxBody = values.prefilled("maxBody")
    if (maxBody.toIntOrNull() != null) append("\n  maxResponseBodySize: $maxBody")
}

private fun buildIpAllowList(values: WizardValues): String = buildString {
    val cidrs = values.lines("cidrs")
    append("ipAllowList:\n  sourceRange:\n")
    append(cidrs.joinToString("\n") { "    - " + yamlStringOf(it) })
    when (values.value("strategy", "direct")) {
        "depth" -> {
            val depth = values.value("depth", "1").toIntOrNull()?.takeIf { it > 0 } ?: 1
            append("\n  ipStrategy:\n    depth: $depth")
        }
        "excluded" -> {
            val excluded = values.lines("excluded")
            if (excluded.isNotEmpty()) {
                append("\n  ipStrategy:\n    excludedIPs:\n")
                append(excluded.joinToString("\n") { "      - " + yamlStringOf(it) })
            }
        }
    }
}

private fun yamlStringOf(value: String): String {
    val escaped = value.replace("\\", "\\\\").replace("\"", "\\\"")
    return "\"$escaped\""
}

private fun List<WizardField>.withDefaults(vararg defaults: Pair<String, String>): List<WizardField> {
    val map = defaults.toMap()
    return map { field ->
        val preset = map[field.key] ?: return@map field
        when (field) {
            is WizardField.Text -> field.copy(default = preset)
            is WizardField.Lines -> field.copy(default = preset)
            else -> field
        }
    }
}
