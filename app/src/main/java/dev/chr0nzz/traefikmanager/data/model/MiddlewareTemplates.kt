package dev.chr0nzz.traefikmanager.data.model

sealed interface WizardField {
    val key: String
    val label: String

    data class Text(
        override val key: String,
        override val label: String,
        val placeholder: String = "",
        val default: String = "",
        val help: String = "",
        val numeric: Boolean = false,
        val secret: Boolean = false,
    ) : WizardField

    data class Lines(
        override val key: String,
        override val label: String,
        val placeholder: String = "",
        val help: String = "",
        val default: String = "",
    ) : WizardField

    data class Toggle(
        override val key: String,
        override val label: String,
        val default: Boolean = false,
    ) : WizardField

    data class Choice(
        override val key: String,
        override val label: String,
        val options: List<Pair<String, String>>,
        val default: String,
    ) : WizardField
}

class WizardValues(
    private val text: Map<String, String>,
    private val toggles: Map<String, Boolean>,
    private val fields: List<WizardField>,
) {
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

    fun initialText(field: WizardField): String = when (field) {
        is WizardField.Text -> text[field.key] ?: field.default
        is WizardField.Lines -> text[field.key] ?: field.default
        is WizardField.Choice -> text[field.key] ?: field.default
        is WizardField.Toggle -> ""
    }

    fun flag(key: String): Boolean {
        toggles[key]?.let { return it }
        return fields.filterIsInstance<WizardField.Toggle>().firstOrNull { it.key == key }?.default ?: false
    }

    fun int(key: String, fallback: Int): Int = value(key).toIntOrNull() ?: fallback
}

data class MiddlewareWizard(
    val id: String,
    val label: String,
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
            label = "Users",
            placeholder = "admin:\$apr1\$H6uskkkW\$IgXLP6ewTrSuBkTrqE8",
            help = "One per line, format user:htpasswd-hash",
        ),
        WizardField.Text(key = "realm", label = "Realm (optional)", placeholder = "My Protected Area"),
    )

    private val forwardAuthFields = listOf(
        WizardField.Text(key = "address", label = "Auth service URL", placeholder = "http://auth-service:4181"),
        WizardField.Toggle(key = "trust", label = "Trust forward header", default = true),
        WizardField.Lines(
            key = "headers",
            label = "Auth response headers",
            placeholder = "X-Auth-User\nX-Auth-Token",
            help = "One per line",
        ),
        WizardField.Text(
            key = "maxBody",
            label = "Max response body size (optional)",
            placeholder = "4096",
            help = "Bytes, Traefik 3.7+",
            numeric = true,
        ),
    )

    private val ipAllowListFields = listOf(
        WizardField.Lines(
            key = "cidrs",
            label = "Allowed CIDRs",
            placeholder = "192.168.1.0/24\n10.0.0.0/8",
            help = "One per line",
        ),
        WizardField.Choice(
            key = "strategy",
            label = "Client IP source",
            options = listOf(
                "direct" to "Direct connection",
                "depth" to "Trusted hop depth",
                "excluded" to "Excluded proxy IPs",
            ),
            default = "direct",
        ),
        WizardField.Text(key = "depth", label = "Trusted hop depth", default = "1", numeric = true),
        WizardField.Lines(
            key = "excluded",
            label = "Proxy IPs to exclude",
            placeholder = "173.245.48.0/20",
            help = "One per line",
        ),
    )

    val all: List<MiddlewareWizard> = listOf(
        MiddlewareWizard("basicAuth", "Basic Auth", CATEGORY_AUTH, basicAuthFields) { values ->
            buildString {
                append("basicAuth:\n  users:\n")
                append(listBlock("    ", values.lines("users")))
                val realm = values.value("realm")
                if (realm.isNotEmpty()) append("\n  realm: ${yamlString(realm)}")
            }
        },
        MiddlewareWizard(
            "digestAuth",
            "Digest Auth",
            CATEGORY_AUTH,
            listOf(
                WizardField.Lines(
                    key = "users",
                    label = "Users",
                    placeholder = "admin:traefik:a2688e031edb4be6fe3079ef99",
                    help = "One per line, format user:realm:md5hash",
                ),
            ),
        ) { values ->
            "digestAuth:\n  users:\n" + listBlock("    ", values.lines("users"))
        },
        MiddlewareWizard("forwardAuth", "Forward Auth", CATEGORY_AUTH, forwardAuthFields, ::buildForwardAuth),
        MiddlewareWizard(
            "forwardAuthAuthentik",
            "Forward Auth (Authentik)",
            CATEGORY_AUTH,
            forwardAuthFields.withDefaults(
                "address" to "http://authentik-server:9000/outpost.goauthentik.io/auth/traefik",
                "headers" to "X-authentik-username\nX-authentik-groups\nX-authentik-email\nX-authentik-name\nX-authentik-uid",
            ),
            ::buildForwardAuth,
        ),
        MiddlewareWizard(
            "forwardAuthAuthelia",
            "Forward Auth (Authelia)",
            CATEGORY_AUTH,
            forwardAuthFields.withDefaults(
                "address" to "http://authelia:9091/api/verify?rd=https://auth.example.com",
                "headers" to "Remote-User\nRemote-Groups\nRemote-Name\nRemote-Email",
            ),
            ::buildForwardAuth,
        ),
        MiddlewareWizard(
            "forwardAuthGatekeeper",
            "Forward Auth (Gatekeeper)",
            CATEGORY_AUTH,
            listOf(
                WizardField.Text(key = "url", label = "Gatekeeper URL", placeholder = "https://auth.example.com"),
                WizardField.Text(key = "policy", label = "Policy (optional)", placeholder = "admin"),
                WizardField.Toggle(key = "trust", label = "Trust forward header", default = false),
                WizardField.Toggle(key = "authorization", label = "Forward Authorization header", default = false),
                WizardField.Lines(key = "headers", label = "Auth response headers", help = "One per line"),
                WizardField.Text(
                    key = "maxBody",
                    label = "Max response body size (optional)",
                    placeholder = "4096",
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
                val maxBody = values.value("maxBody")
                if (maxBody.toIntOrNull() != null) append("\n  maxResponseBodySize: $maxBody")
            }
        },
        MiddlewareWizard(
            "oidcAuth",
            "OIDC Auth (traefik-oidc-auth)",
            CATEGORY_AUTH,
            listOf(
                WizardField.Text(key = "providerUrl", label = "Provider URL", placeholder = "https://login.example.com"),
                WizardField.Text(key = "clientId", label = "Client ID", placeholder = "client-id"),
                WizardField.Text(key = "clientSecret", label = "Client secret", placeholder = "client-secret", secret = true),
                WizardField.Text(
                    key = "secret",
                    label = "Session secret",
                    placeholder = "change-me-with-own-32-character-secret",
                    help = "32+ character random string",
                    secret = true,
                ),
                WizardField.Lines(key = "scopes", label = "Scopes", help = "One per line"),
                WizardField.Text(key = "maxAge", label = "Session max age (seconds)", default = "86400", numeric = true),
                WizardField.Lines(
                    key = "headers",
                    label = "Forward claims as headers",
                    help = "One per line, format Name: claim",
                ),
                WizardField.Lines(
                    key = "bypass",
                    label = "Bypass rules (optional)",
                    placeholder = "^/health$\n^/metrics$",
                    help = "Regex, one per line",
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
        MiddlewareWizard("ipAllowList", "IP Allow List", CATEGORY_SECURITY, ipAllowListFields, ::buildIpAllowList),
        MiddlewareWizard(
            "ipAllowListPrivate",
            "IP Allow List (Private Ranges)",
            CATEGORY_SECURITY,
            ipAllowListFields.withDefaults("cidrs" to "10.0.0.0/8\n172.16.0.0/12\n192.168.0.0/16\n127.0.0.1/32"),
            ::buildIpAllowList,
        ),
        MiddlewareWizard(
            "rateLimit",
            "Rate Limit",
            CATEGORY_SECURITY,
            listOf(
                WizardField.Text(key = "average", label = "Average (req/s)", default = "100", numeric = true),
                WizardField.Text(key = "burst", label = "Burst", default = "50", numeric = true),
                WizardField.Text(key = "period", label = "Period", default = "1s"),
            ),
        ) { values ->
            "rateLimit:\n  average: ${values.value("average", "100")}\n" +
                "  burst: ${values.value("burst", "50")}\n  period: ${values.value("period", "1s")}"
        },
        MiddlewareWizard(
            "secureHeaders",
            "Secure Headers",
            CATEGORY_SECURITY,
            listOf(
                WizardField.Toggle(key = "ssl", label = "SSL redirect (force HTTPS)", default = true),
                WizardField.Toggle(key = "hsts", label = "HSTS (Strict Transport Security)", default = true),
                WizardField.Text(key = "hstsAge", label = "HSTS max age (seconds)", default = "315360000", numeric = true),
                WizardField.Toggle(key = "subdomains", label = "Include subdomains", default = true),
                WizardField.Toggle(key = "preload", label = "Preload", default = true),
                WizardField.Toggle(key = "nosniff", label = "X-Content-Type-Options (nosniff)", default = true),
                WizardField.Toggle(key = "xss", label = "X-XSS-Protection", default = true),
                WizardField.Toggle(key = "frame", label = "X-Frame-Options (deny)", default = true),
                WizardField.Toggle(key = "referrer", label = "Same-origin referrer policy", default = false),
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
            "CORS Headers",
            CATEGORY_SECURITY,
            listOf(
                WizardField.Toggle(key = "GET", label = "GET", default = true),
                WizardField.Toggle(key = "POST", label = "POST", default = true),
                WizardField.Toggle(key = "PUT", label = "PUT", default = true),
                WizardField.Toggle(key = "DELETE", label = "DELETE", default = true),
                WizardField.Toggle(key = "PATCH", label = "PATCH", default = true),
                WizardField.Toggle(key = "OPTIONS", label = "OPTIONS", default = true),
                WizardField.Toggle(key = "HEAD", label = "HEAD", default = false),
                WizardField.Lines(
                    key = "origins",
                    label = "Allowed origins",
                    placeholder = "https://example.com",
                    help = "One per line",
                ),
                WizardField.Lines(key = "headers", label = "Allowed headers", placeholder = "*", help = "One per line"),
                WizardField.Text(key = "maxAge", label = "Max age (seconds)", default = "100", numeric = true),
                WizardField.Toggle(key = "vary", label = "Add Vary header", default = true),
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
            "Encoded Characters (Traefik 3.7+)",
            CATEGORY_SECURITY,
            listOf(
                WizardField.Toggle(key = "allowEncodedSlash", label = "Allow encoded slash %2F"),
                WizardField.Toggle(key = "allowEncodedBackSlash", label = "Allow encoded backslash %5C"),
                WizardField.Toggle(key = "allowEncodedSemicolon", label = "Allow encoded semicolon %3B"),
                WizardField.Toggle(key = "allowEncodedPercent", label = "Allow encoded percent %25"),
                WizardField.Toggle(key = "allowEncodedQuestionMark", label = "Allow encoded question mark %3F"),
                WizardField.Toggle(key = "allowEncodedHash", label = "Allow encoded hash %23"),
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
            "Redirect to HTTPS",
            CATEGORY_ROUTING,
            listOf(
                WizardField.Choice(
                    key = "scheme",
                    label = "Scheme",
                    options = listOf("https" to "https", "http" to "http"),
                    default = "https",
                ),
                WizardField.Toggle(key = "permanent", label = "Permanent redirect (301)", default = true),
            ),
        ) { values ->
            "redirectScheme:\n  scheme: ${values.value("scheme", "https")}\n  permanent: ${values.flag("permanent")}"
        },
        MiddlewareWizard(
            "redirectRegex",
            "Redirect Regex",
            CATEGORY_ROUTING,
            listOf(
                WizardField.Text(key = "regex", label = "Regex", placeholder = "^http://(.*)"),
                WizardField.Text(key = "replacement", label = "Replacement", placeholder = "https://\${1}"),
                WizardField.Toggle(key = "permanent", label = "Permanent redirect (301)", default = true),
            ),
        ) { values ->
            "redirectRegex:\n  regex: ${yamlString(values.value("regex"))}\n" +
                "  replacement: ${yamlString(values.value("replacement"))}\n" +
                "  permanent: ${values.flag("permanent")}"
        },
        MiddlewareWizard(
            "stripPrefix",
            "Strip Prefix",
            CATEGORY_ROUTING,
            listOf(WizardField.Lines(key = "prefixes", label = "Prefixes", placeholder = "/api\n/v1", help = "One per line")),
        ) { values ->
            "stripPrefix:\n  prefixes:\n" + listBlock("    ", values.lines("prefixes"))
        },
        MiddlewareWizard(
            "addPrefix",
            "Add Prefix",
            CATEGORY_ROUTING,
            listOf(WizardField.Text(key = "prefix", label = "Prefix", placeholder = "/api")),
        ) { values ->
            "addPrefix:\n  prefix: ${yamlString(values.value("prefix"))}"
        },
        MiddlewareWizard(
            "replacePath",
            "Replace Path",
            CATEGORY_ROUTING,
            listOf(WizardField.Text(key = "path", label = "Path", placeholder = "/foo")),
        ) { values ->
            "replacePath:\n  path: ${yamlString(values.value("path"))}"
        },
        MiddlewareWizard(
            "compress",
            "Gzip Compress",
            CATEGORY_ADVANCED,
            listOf(
                WizardField.Text(
                    key = "min",
                    label = "Min response body bytes",
                    default = "1200",
                    help = "Responses smaller than this are not compressed",
                    numeric = true,
                ),
            ),
        ) { values ->
            "compress:\n  minResponseBodyBytes: ${values.value("min", "1200")}"
        },
        MiddlewareWizard(
            "retry",
            "Retry",
            CATEGORY_ADVANCED,
            listOf(
                WizardField.Text(key = "attempts", label = "Attempts", default = "4", numeric = true),
                WizardField.Text(key = "interval", label = "Initial interval", default = "100ms"),
            ),
        ) { values ->
            "retry:\n  attempts: ${values.value("attempts", "4")}\n" +
                "  initialInterval: ${values.value("interval", "100ms")}"
        },
        MiddlewareWizard(
            "circuitBreaker",
            "Circuit Breaker",
            CATEGORY_ADVANCED,
            listOf(
                WizardField.Text(
                    key = "expression",
                    label = "Expression",
                    placeholder = "NetworkErrorRatio() > 0.30",
                ),
            ),
        ) { values ->
            "circuitBreaker:\n  expression: ${yamlString(values.value("expression"))}"
        },
        MiddlewareWizard(
            "buffering",
            "Buffering",
            CATEGORY_ADVANCED,
            listOf(
                WizardField.Text(key = "request", label = "Max request body (bytes)", default = "10485760", numeric = true),
                WizardField.Text(key = "response", label = "Max response body (bytes)", default = "10485760", numeric = true),
                WizardField.Text(key = "retry", label = "Retry expression (optional)"),
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
            "Middleware Chain",
            CATEGORY_ADVANCED,
            listOf(
                WizardField.Lines(
                    key = "middlewares",
                    label = "Middlewares",
                    placeholder = "redirect-https\nsecure-headers",
                    help = "One per line, applied in order",
                ),
            ),
        ) { values ->
            "chain:\n  middlewares:\n" + listBlock("    ", values.lines("middlewares"), quoted = false)
        },
        MiddlewareWizard(
            "inFlightReq",
            "In-Flight Limit",
            CATEGORY_ADVANCED,
            listOf(WizardField.Text(key = "amount", label = "Max in-flight requests", default = "10", numeric = true)),
        ) { values ->
            "inFlightReq:\n  amount: ${values.value("amount", "10")}"
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
    val maxBody = values.value("maxBody")
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
