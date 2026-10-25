import Foundation

/// One input in a middleware wizard. Port of `WizardField`.
enum WizardField: Identifiable, Sendable {
    case text(key: String, label: String, placeholder: String = "", defaultValue: String = "", help: String? = nil, numeric: Bool = false, secret: Bool = false)
    case lines(key: String, label: String, placeholder: String = "", help: String? = nil, defaultValue: String = "")
    case toggle(key: String, label: String, defaultValue: Bool = false)
    case choice(key: String, label: String, options: [(String, String)], defaultValue: String)

    var id: String { key }

    var key: String {
        switch self {
        case let .text(key, _, _, _, _, _, _), let .lines(key, _, _, _, _), let .toggle(key, _, _), let .choice(key, _, _, _): key
        }
    }

    var textDefault: String? {
        switch self {
        case let .text(_, _, _, value, _, _, _), let .lines(_, _, _, _, value), let .choice(_, _, _, value): value
        case .toggle: nil
        }
    }

    func withDefault(_ value: String) -> WizardField {
        switch self {
        case let .text(key, label, placeholder, _, help, numeric, secret):
            .text(key: key, label: label, placeholder: placeholder, defaultValue: value, help: help, numeric: numeric, secret: secret)
        case let .lines(key, label, placeholder, help, _):
            .lines(key: key, label: label, placeholder: placeholder, help: help, defaultValue: value)
        default:
            self
        }
    }
}

/// The values a user typed into a wizard, with field defaults as fallback. Port of `WizardValues`.
struct WizardValues {
    var text: [String: String]
    var toggles: [String: Bool]
    var fields: [WizardField]

    func prefilled(_ key: String) -> String {
        if let raw = text[key] { return raw.trimmed }
        if case let .text(_, _, _, value, _, _, _)? = fields.first(where: { $0.key == key }) { return value }
        return ""
    }

    func value(_ key: String, _ fallback: String = "") -> String {
        if let raw = text[key]?.trimmed, !raw.isEmpty { return raw }
        switch fields.first(where: { $0.key == key }) {
        case let .text(_, _, _, value, _, _, _)?, let .choice(_, _, _, value)?:
            return value.isEmpty ? fallback : value
        default:
            return fallback
        }
    }

    func lines(_ key: String) -> [String] {
        var raw = text[key]
        if raw == nil, case let .lines(_, _, _, _, value)? = fields.first(where: { $0.key == key }) { raw = value }
        return (raw ?? "").components(separatedBy: "\n").map(\.trimmed).filter { !$0.isEmpty }
    }

    func flag(_ key: String) -> Bool {
        if let value = toggles[key] { return value }
        if case let .toggle(_, _, value)? = fields.first(where: { $0.key == key }) { return value }
        return false
    }

    func int(_ key: String, _ fallback: Int) -> Int { Int(value(key)) ?? fallback }
}

struct MiddlewareWizard: Identifiable, Sendable {
    var id: String
    var label: String
    var category: String
    var fields: [WizardField]
    var build: @Sendable (WizardValues) -> String

    var defaultValues: WizardValues {
        var text: [String: String] = [:]
        var toggles: [String: Bool] = [:]
        for field in fields {
            if case let .toggle(key, _, value) = field { toggles[key] = value } else { text[field.key] = field.textDefault ?? "" }
        }
        return WizardValues(text: text, toggles: toggles, fields: fields)
    }
}

private func yamlString(_ value: String) -> String {
    "\"" + value.replacingOccurrences(of: "\\", with: "\\\\").replacingOccurrences(of: "\"", with: "\\\"") + "\""
}

private func listBlock(_ indent: String, _ items: [String], quoted: Bool = true) -> String {
    items.map { indent + "- " + (quoted ? yamlString($0) : $0) }.joined(separator: "\n")
}

/// The built-in middleware wizards, each producing YAML. Line-for-line port of `MiddlewareTemplates.kt`.
enum MiddlewareTemplates {
    static let categoryAuth = "Auth"
    static let categorySecurity = "Security"
    static let categoryRouting = "Routing"
    static let categoryAdvanced = "Advanced"
    static let categories = [categoryAuth, categorySecurity, categoryRouting, categoryAdvanced]

    static func categoryLabel(_ category: String) -> String {
        switch category {
        case categoryAuth: String(localized: "Auth")
        case categorySecurity: String(localized: "Security")
        case categoryRouting: String(localized: "Routing")
        default: String(localized: "Advanced")
        }
    }

    private static let onePerLine = String(localized: "One per line")
    private static let maxBodyHelp = String(localized: "Bytes. Traefik 3.7 warns on every forward auth without one. Clear it to omit.")

    private static let maxBodyField = WizardField.text(
        key: "maxBody", label: String(localized: "Max response body size"), placeholder: "4096",
        defaultValue: "4096", help: maxBodyHelp, numeric: true
    )

    private static let forwardAuthFields: [WizardField] = [
        .text(key: "address", label: String(localized: "Auth service URL"), placeholder: "http://auth-service:4181"),
        .toggle(key: "trust", label: String(localized: "Trust forward header"), defaultValue: true),
        .lines(key: "headers", label: String(localized: "Auth response headers"), placeholder: "X-Auth-User\nX-Auth-Token", help: onePerLine),
        maxBodyField,
    ]

    private static let ipAllowListFields: [WizardField] = [
        .lines(key: "cidrs", label: String(localized: "Allowed CIDRs"), placeholder: "192.168.1.0/24\n10.0.0.0/8", help: onePerLine),
        .choice(
            key: "strategy",
            label: String(localized: "Client IP source"),
            options: [
                ("direct", String(localized: "Direct connection")),
                ("depth", String(localized: "Trusted hop depth")),
                ("excluded", String(localized: "Excluded proxy IPs")),
            ],
            defaultValue: "direct"
        ),
        .text(key: "depth", label: String(localized: "Trusted hop depth"), defaultValue: "1", numeric: true),
        .lines(key: "excluded", label: String(localized: "Proxy IPs to exclude"), placeholder: "173.245.48.0/20", help: onePerLine),
    ]

    private static func withDefaults(_ fields: [WizardField], _ defaults: [String: String]) -> [WizardField] {
        fields.map { field in defaults[field.key].map(field.withDefault) ?? field }
    }

    static let all: [MiddlewareWizard] = [
        MiddlewareWizard(id: "basicAuth", label: String(localized: "Basic Auth"), category: categoryAuth, fields: [
            .lines(
                key: "users", label: String(localized: "Users"), placeholder: "admin:$apr1$H6uskkkW$IgXLP6ewTrSuBkTrqE8",
                help: String(localized: "One per line, format user:htpasswd-hash")
            ),
            .text(key: "realm", label: String(localized: "Realm (optional)"), placeholder: "My Protected Area"),
        ]) { values in
            var yaml = "basicAuth:\n  users:\n" + listBlock("    ", values.lines("users"))
            let realm = values.value("realm")
            if !realm.isEmpty { yaml += "\n  realm: \(yamlString(realm))" }
            return yaml
        },
        MiddlewareWizard(id: "digestAuth", label: String(localized: "Digest Auth"), category: categoryAuth, fields: [
            .lines(
                key: "users", label: String(localized: "Users"), placeholder: "admin:traefik:a2688e031edb4be6fe3079ef99",
                help: String(localized: "One per line, format user:realm:md5hash")
            ),
        ]) { values in
            "digestAuth:\n  users:\n" + listBlock("    ", values.lines("users"))
        },
        MiddlewareWizard(id: "forwardAuth", label: String(localized: "Forward Auth"), category: categoryAuth, fields: forwardAuthFields, build: buildForwardAuth),
        MiddlewareWizard(
            id: "forwardAuthAuthentik",
            label: String(localized: "Forward Auth (Authentik)"),
            category: categoryAuth,
            fields: withDefaults(forwardAuthFields, [
                "address": "http://authentik-server:9000/outpost.goauthentik.io/auth/traefik",
                "headers": "X-authentik-username\nX-authentik-groups\nX-authentik-email\nX-authentik-name\nX-authentik-uid",
            ]),
            build: buildForwardAuth
        ),
        MiddlewareWizard(
            id: "forwardAuthAuthelia",
            label: String(localized: "Forward Auth (Authelia)"),
            category: categoryAuth,
            fields: withDefaults(forwardAuthFields, [
                "address": "http://authelia:9091/api/verify?rd=https://auth.example.com",
                "headers": "Remote-User\nRemote-Groups\nRemote-Name\nRemote-Email",
            ]),
            build: buildForwardAuth
        ),
        MiddlewareWizard(id: "forwardAuthGatekeeper", label: String(localized: "Forward Auth (Gatekeeper)"), category: categoryAuth, fields: [
            .text(key: "url", label: String(localized: "Gatekeeper URL"), placeholder: "https://auth.example.com"),
            .text(key: "policy", label: String(localized: "Policy (optional)"), placeholder: "admin"),
            .toggle(key: "trust", label: String(localized: "Trust forward header"), defaultValue: false),
            .toggle(key: "authorization", label: String(localized: "Forward Authorization header"), defaultValue: false),
            .lines(key: "headers", label: String(localized: "Auth response headers"), help: onePerLine),
            maxBodyField,
        ]) { values in
            var url = values.value("url")
            while url.hasSuffix("/") { url.removeLast() }
            let policy = values.value("policy")
            let address = url.isEmpty ? "" : url + "/auth/verify" + (policy.isEmpty ? "" : "?policy=\(policy)")
            let headers = values.lines("headers")
            let all = values.flag("authorization") ? ["Authorization"] + headers.filter { $0 != "Authorization" } : headers
            var yaml = "forwardAuth:\n  address: \(yamlString(address))\n  trustForwardHeader: \(values.flag("trust"))"
            if !all.isEmpty { yaml += "\n  authResponseHeaders:\n" + listBlock("    ", all) }
            let maxBody = values.prefilled("maxBody")
            if Int(maxBody) != nil { yaml += "\n  maxResponseBodySize: \(maxBody)" }
            return yaml
        },
        MiddlewareWizard(id: "oidcAuth", label: String(localized: "OIDC Auth (traefik-oidc-auth)"), category: categoryAuth, fields: [
            .text(key: "providerUrl", label: String(localized: "Provider URL"), placeholder: "https://login.example.com"),
            .text(key: "clientId", label: String(localized: "Client ID"), placeholder: "client-id"),
            .text(key: "clientSecret", label: String(localized: "Client secret"), placeholder: "client-secret", secret: true),
            .text(
                key: "secret", label: String(localized: "Session secret"), placeholder: "change-me-with-own-32-character-secret",
                help: String(localized: "32+ character random string"), secret: true
            ),
            .lines(key: "scopes", label: String(localized: "Scopes"), help: onePerLine),
            .text(key: "maxAge", label: String(localized: "Session max age (seconds)"), defaultValue: "86400", numeric: true),
            .lines(key: "headers", label: String(localized: "Forward claims as headers"), help: String(localized: "One per line, format Name: claim")),
            .lines(
                key: "bypass", label: String(localized: "Bypass rules (optional)"), placeholder: "^/health$\n^/metrics$",
                help: String(localized: "Regex, one per line")
            ),
        ]) { values in
            var yaml = "plugin:\n  traefik-oidc-auth:"
            let secret = values.value("secret")
            if !secret.isEmpty { yaml += "\n    Secret: \(yamlString(secret))" }
            yaml += "\n    Provider:"
            let providerUrl = values.value("providerUrl")
            if !providerUrl.isEmpty { yaml += "\n      Url: \(yamlString(providerUrl))" }
            let clientId = values.value("clientId")
            if !clientId.isEmpty { yaml += "\n      ClientId: \(yamlString(clientId))" }
            let clientSecret = values.value("clientSecret")
            if !clientSecret.isEmpty { yaml += "\n      ClientSecret: \(yamlString(clientSecret))" }
            let scopes = values.lines("scopes")
            if !scopes.isEmpty { yaml += "\n    Scopes:\n" + listBlock("      ", scopes, quoted: false) }
            yaml += "\n    SessionCookie:\n      MaxAge: \(values.int("maxAge", 86400))"
            let headers = values.lines("headers").compactMap { line -> (String, String)? in
                guard let index = line.firstIndex(of: ":") else { return nil }
                let name = line[..<index].trimmingCharacters(in: .whitespaces)
                let claim = line[line.index(after: index)...].trimmingCharacters(in: .whitespaces)
                return (name, "{{`{{ .claims.\(claim) }}`}}")
            }
            if !headers.isEmpty {
                yaml += "\n    Headers:\n" + headers.map { "      - Name: \(yamlString($0.0))\n        Value: \(yamlString($0.1))" }.joined(separator: "\n")
            }
            let bypass = values.lines("bypass")
            if !bypass.isEmpty { yaml += "\n    BypassAuthenticationRule:\n" + listBlock("      ", bypass) }
            return yaml
        },
        MiddlewareWizard(id: "ipAllowList", label: String(localized: "IP Allow List"), category: categorySecurity, fields: ipAllowListFields, build: buildIPAllowList),
        MiddlewareWizard(
            id: "ipAllowListPrivate",
            label: String(localized: "IP Allow List (Private Ranges)"),
            category: categorySecurity,
            fields: withDefaults(ipAllowListFields, ["cidrs": "10.0.0.0/8\n172.16.0.0/12\n192.168.0.0/16\n127.0.0.1/32"]),
            build: buildIPAllowList
        ),
        MiddlewareWizard(id: "rateLimit", label: String(localized: "Rate Limit"), category: categorySecurity, fields: [
            .text(key: "average", label: String(localized: "Average (req/s)"), defaultValue: "100", numeric: true),
            .text(key: "burst", label: String(localized: "Burst"), defaultValue: "50", numeric: true),
            .text(key: "period", label: String(localized: "Period"), defaultValue: "1s"),
        ]) { values in
            "rateLimit:\n  average: \(values.value("average", "100"))\n  burst: \(values.value("burst", "50"))\n  period: \(values.value("period", "1s"))"
        },
        MiddlewareWizard(id: "secureHeaders", label: String(localized: "Secure Headers"), category: categorySecurity, fields: [
            .toggle(key: "ssl", label: String(localized: "SSL redirect (force HTTPS)"), defaultValue: true),
            .toggle(key: "hsts", label: String(localized: "HSTS (Strict Transport Security)"), defaultValue: true),
            .text(key: "hstsAge", label: String(localized: "HSTS max age (seconds)"), defaultValue: "315360000", numeric: true),
            .toggle(key: "subdomains", label: String(localized: "Include subdomains"), defaultValue: true),
            .toggle(key: "preload", label: String(localized: "Preload"), defaultValue: true),
            .toggle(key: "nosniff", label: String(localized: "X-Content-Type-Options (nosniff)"), defaultValue: true),
            .toggle(key: "xss", label: "X-XSS-Protection", defaultValue: true),
            .toggle(key: "frame", label: String(localized: "X-Frame-Options (deny)"), defaultValue: true),
            .toggle(key: "referrer", label: String(localized: "Same-origin referrer policy"), defaultValue: false),
        ]) { values in
            var lines = ["headers:"]
            if values.flag("ssl") { lines.append("  sslRedirect: true") }
            if values.flag("hsts") {
                lines.append("  forceSTSHeader: true")
                lines.append("  stsSeconds: \(values.value("hstsAge", "315360000"))")
                if values.flag("subdomains") { lines.append("  stsIncludeSubdomains: true") }
                if values.flag("preload") { lines.append("  stsPreload: true") }
            }
            if values.flag("nosniff") { lines.append("  contentTypeNosniff: true") }
            if values.flag("xss") { lines.append("  browserXssFilter: true") }
            if values.flag("frame") { lines.append("  frameDeny: true") }
            if values.flag("referrer") { lines.append("  referrerPolicy: \"same-origin\"") }
            return lines.joined(separator: "\n")
        },
        MiddlewareWizard(id: "corsHeaders", label: String(localized: "CORS Headers"), category: categorySecurity, fields: [
            .toggle(key: "GET", label: "GET", defaultValue: true),
            .toggle(key: "POST", label: "POST", defaultValue: true),
            .toggle(key: "PUT", label: "PUT", defaultValue: true),
            .toggle(key: "DELETE", label: "DELETE", defaultValue: true),
            .toggle(key: "PATCH", label: "PATCH", defaultValue: true),
            .toggle(key: "OPTIONS", label: "OPTIONS", defaultValue: true),
            .toggle(key: "HEAD", label: "HEAD", defaultValue: false),
            .lines(key: "origins", label: String(localized: "Allowed origins"), placeholder: "https://example.com", help: onePerLine),
            .lines(key: "headers", label: String(localized: "Allowed headers"), placeholder: "*", help: onePerLine),
            .text(key: "maxAge", label: String(localized: "Max age (seconds)"), defaultValue: "100", numeric: true),
            .toggle(key: "vary", label: String(localized: "Add Vary header"), defaultValue: true),
        ]) { values in
            let methods = ["GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS", "HEAD"].filter { values.flag($0) }
            var lines = ["headers:"]
            if !methods.isEmpty { lines.append("  accessControlAllowMethods:\n" + listBlock("    ", methods, quoted: false)) }
            let headers = values.lines("headers")
            if !headers.isEmpty { lines.append("  accessControlAllowHeaders:\n" + listBlock("    ", headers)) }
            let origins = values.lines("origins")
            if !origins.isEmpty { lines.append("  accessControlAllowOriginList:\n" + listBlock("    ", origins)) }
            lines.append("  accessControlMaxAge: \(values.value("maxAge", "100"))")
            if values.flag("vary") { lines.append("  addVaryHeader: true") }
            return lines.joined(separator: "\n")
        },
        MiddlewareWizard(id: "encodedCharacters", label: String(localized: "Encoded Characters (Traefik 3.7+)"), category: categorySecurity, fields: [
            .toggle(key: "allowEncodedSlash", label: String(localized: "Allow encoded slash %2F")),
            .toggle(key: "allowEncodedBackSlash", label: String(localized: "Allow encoded backslash %5C")),
            .toggle(key: "allowEncodedSemicolon", label: String(localized: "Allow encoded semicolon %3B")),
            .toggle(key: "allowEncodedPercent", label: String(localized: "Allow encoded percent %25")),
            .toggle(key: "allowEncodedQuestionMark", label: String(localized: "Allow encoded question mark %3F")),
            .toggle(key: "allowEncodedHash", label: String(localized: "Allow encoded hash %23")),
        ]) { values in
            let enabled = [
                "allowEncodedSlash", "allowEncodedBackSlash", "allowEncodedSemicolon",
                "allowEncodedPercent", "allowEncodedQuestionMark", "allowEncodedHash",
            ].filter { values.flag($0) }
            return enabled.isEmpty ? "encodedCharacters: {}" : "encodedCharacters:\n" + enabled.map { "  \($0): true" }.joined(separator: "\n")
        },
        MiddlewareWizard(id: "redirectScheme", label: String(localized: "Redirect to HTTPS"), category: categoryRouting, fields: [
            .choice(key: "scheme", label: String(localized: "Scheme"), options: [("https", "https"), ("http", "http")], defaultValue: "https"),
            .toggle(key: "permanent", label: String(localized: "Permanent redirect (301)"), defaultValue: true),
        ]) { values in
            "redirectScheme:\n  scheme: \(values.value("scheme", "https"))\n  permanent: \(values.flag("permanent"))"
        },
        MiddlewareWizard(id: "redirectRegex", label: String(localized: "Redirect Regex"), category: categoryRouting, fields: [
            .text(key: "regex", label: String(localized: "Regex"), placeholder: "^http://(.*)"),
            .text(key: "replacement", label: String(localized: "Replacement"), placeholder: "https://${1}"),
            .toggle(key: "permanent", label: String(localized: "Permanent redirect (301)"), defaultValue: true),
        ]) { values in
            "redirectRegex:\n  regex: \(yamlString(values.value("regex")))\n  replacement: \(yamlString(values.value("replacement")))\n  permanent: \(values.flag("permanent"))"
        },
        MiddlewareWizard(id: "stripPrefix", label: String(localized: "Strip Prefix"), category: categoryRouting, fields: [
            .lines(key: "prefixes", label: String(localized: "Prefixes"), placeholder: "/api\n/v1", help: onePerLine),
        ]) { values in
            "stripPrefix:\n  prefixes:\n" + listBlock("    ", values.lines("prefixes"))
        },
        MiddlewareWizard(id: "addPrefix", label: String(localized: "Add Prefix"), category: categoryRouting, fields: [
            .text(key: "prefix", label: String(localized: "Prefix"), placeholder: "/api"),
        ]) { values in
            "addPrefix:\n  prefix: \(yamlString(values.value("prefix")))"
        },
        MiddlewareWizard(id: "replacePath", label: String(localized: "Replace Path"), category: categoryRouting, fields: [
            .text(key: "path", label: String(localized: "Path"), placeholder: "/foo"),
        ]) { values in
            "replacePath:\n  path: \(yamlString(values.value("path")))"
        },
        MiddlewareWizard(id: "compress", label: String(localized: "Gzip Compress"), category: categoryAdvanced, fields: [
            .text(
                key: "min", label: String(localized: "Min response body bytes"), defaultValue: "1200",
                help: String(localized: "Responses smaller than this are not compressed"), numeric: true
            ),
        ]) { values in
            "compress:\n  minResponseBodyBytes: \(values.value("min", "1200"))"
        },
        MiddlewareWizard(id: "retry", label: String(localized: "Retry"), category: categoryAdvanced, fields: [
            .text(key: "attempts", label: String(localized: "Attempts"), defaultValue: "4", numeric: true),
            .text(key: "interval", label: String(localized: "Initial interval"), defaultValue: "100ms"),
        ]) { values in
            "retry:\n  attempts: \(values.value("attempts", "4"))\n  initialInterval: \(values.value("interval", "100ms"))"
        },
        MiddlewareWizard(id: "circuitBreaker", label: String(localized: "Circuit Breaker"), category: categoryAdvanced, fields: [
            .text(key: "expression", label: String(localized: "Expression"), placeholder: "NetworkErrorRatio() > 0.30"),
        ]) { values in
            "circuitBreaker:\n  expression: \(yamlString(values.value("expression")))"
        },
        MiddlewareWizard(id: "buffering", label: String(localized: "Buffering"), category: categoryAdvanced, fields: [
            .text(key: "request", label: String(localized: "Max request body (bytes)"), defaultValue: "10485760", numeric: true),
            .text(key: "response", label: String(localized: "Max response body (bytes)"), defaultValue: "10485760", numeric: true),
            .text(key: "retry", label: String(localized: "Retry expression (optional)")),
        ]) { values in
            var yaml = "buffering:\n  maxRequestBodyBytes: \(values.value("request", "10485760"))\n  maxResponseBodyBytes: \(values.value("response", "10485760"))"
            let retry = values.value("retry")
            if !retry.isEmpty { yaml += "\n  retryExpression: \(yamlString(retry))" }
            return yaml
        },
        MiddlewareWizard(id: "chain", label: String(localized: "Middleware Chain"), category: categoryAdvanced, fields: [
            .lines(
                key: "middlewares", label: String(localized: "Middlewares"), placeholder: "redirect-https\nsecure-headers",
                help: String(localized: "One per line, applied in order")
            ),
        ]) { values in
            "chain:\n  middlewares:\n" + listBlock("    ", values.lines("middlewares"), quoted: false)
        },
        MiddlewareWizard(id: "inFlightReq", label: String(localized: "In-Flight Limit"), category: categoryAdvanced, fields: [
            .text(key: "amount", label: String(localized: "Max in-flight requests"), defaultValue: "10", numeric: true),
        ]) { values in
            "inFlightReq:\n  amount: \(values.value("amount", "10"))"
        },
        MiddlewareWizard(id: "stripPrefixRegex", label: String(localized: "Strip Prefix Regex"), category: categoryRouting, fields: [
            .lines(
                key: "regex", label: String(localized: "Regexes"), placeholder: "^/api/v[0-9]+",
                help: String(localized: "One per line. The matched prefix is removed before the request is forwarded. Anchor with ^ or it can match mid-path.")
            ),
        ]) { values in
            "stripPrefixRegex:\n  regex:\n" + listBlock("    ", values.lines("regex"))
        },
        MiddlewareWizard(id: "replacePathRegex", label: String(localized: "Replace Path Regex"), category: categoryRouting, fields: [
            .text(key: "regex", label: String(localized: "Regex"), placeholder: "^/foo/(.*)"),
            .text(
                key: "replacement", label: String(localized: "Replacement"), placeholder: "/bar/$1",
                help: String(localized: "Capture groups are $1, $2 and so on. In a compose file $ must be written $$.")
            ),
        ]) { values in
            "replacePathRegex:\n  regex: \(yamlString(values.value("regex")))\n  replacement: \(yamlString(values.value("replacement")))"
        },
        MiddlewareWizard(id: "errors", label: String(localized: "Custom Error Pages"), category: categoryAdvanced, fields: [
            .lines(
                key: "status", label: String(localized: "Status codes"), placeholder: "500-599",
                help: String(localized: "One per line. Single codes or ranges. Only these responses are replaced.")
            ),
            .text(
                key: "service", label: String(localized: "Service"), placeholder: "error-pages",
                help: String(localized: "The service that serves the error page. It must already exist in this config.")
            ),
            .text(
                key: "query", label: String(localized: "Query"), placeholder: "/{status}.html",
                help: String(localized: "Optional. {status} is replaced with the response code.")
            ),
        ]) { values in
            var yaml = "errors:\n  status:\n" + listBlock("    ", values.lines("status")) + "\n  service: " + yamlString(values.value("service"))
            let query = values.value("query")
            if !query.isBlank { yaml += "\n  query: " + yamlString(query) }
            return yaml
        },
        MiddlewareWizard(id: "contentType", label: String(localized: "Content Type"), category: categoryAdvanced, fields: [
            .toggle(key: "autoDetect", label: String(localized: "Auto-detect the content type"), defaultValue: false),
        ]) { values in
            "contentType:\n  autoDetect: \(values.flag("autoDetect"))"
        },
        MiddlewareWizard(id: "grpcWeb", label: "gRPC-Web", category: categoryAdvanced, fields: [
            .lines(
                key: "allowOrigins", label: String(localized: "Allowed origins"), placeholder: "https://app.example.com",
                help: String(localized: "One per line. Converts gRPC-Web requests to gRPC before forwarding. * allows any origin.")
            ),
        ]) { values in
            "grpcWeb:\n  allowOrigins:\n" + listBlock("    ", values.lines("allowOrigins"))
        },
        MiddlewareWizard(id: "passTLSClientCert", label: String(localized: "Pass TLS Client Cert"), category: categoryAdvanced, fields: [
            .toggle(key: "pem", label: String(localized: "Pass the whole certificate"), defaultValue: true),
            .toggle(key: "info", label: String(localized: "Also pass selected certificate fields")),
            .toggle(key: "subjectCN", label: String(localized: "Subject Common Name"), defaultValue: true),
            .toggle(key: "issuerCN", label: String(localized: "Issuer Common Name")),
            .toggle(key: "serial", label: String(localized: "Serial Number")),
            .toggle(key: "notAfter", label: String(localized: "Expiry Date")),
        ]) { values in
            var yaml = "passTLSClientCert:\n  pem: \(values.flag("pem"))"
            if values.flag("info") {
                yaml += "\n  info:"
                if values.flag("serial") { yaml += "\n    serialNumber: true" }
                if values.flag("notAfter") { yaml += "\n    notAfter: true" }
                if values.flag("subjectCN") { yaml += "\n    subject:\n      commonName: true" }
                if values.flag("issuerCN") { yaml += "\n    issuer:\n      commonName: true" }
            }
            return yaml
        },
    ]

    static func byId(_ id: String) -> MiddlewareWizard? { all.first { $0.id == id } }

    /// First top-level YAML key, e.g. `basicAuth`.
    static func kind(of yaml: String) -> String {
        guard let first = yaml.components(separatedBy: "\n").map(\.trimmed).first(where: { !$0.isEmpty && !$0.hasPrefix("#") })
        else { return "middleware" }
        return (first.components(separatedBy: ":").first ?? "").trimmed.ifEmpty("middleware")
    }

    static func icon(for kind: String) -> String {
        switch kind {
        case "basicAuth", "digestAuth", "forwardAuth": "lock"
        case "headers": "chevron.left.forwardslash.chevron.right"
        case "ipAllowList", "ipWhiteList": "line.3.horizontal.decrease"
        case "rateLimit", "inFlightReq", "inFlightConn": "speedometer"
        case "redirectScheme", "redirectRegex": "arrow.left.arrow.right"
        case "stripPrefix", "addPrefix", "replacePath", "replacePathRegex": "link"
        case "compress": "wind"
        case "retry", "circuitBreaker", "buffering": "bolt"
        case "chain": "arrow.triangle.swap"
        case "plugin": "puzzlepiece.extension"
        default: "shield"
        }
    }
}

private func buildForwardAuth(_ values: WizardValues) -> String {
    var yaml = "forwardAuth:\n  address: \(yamlString(values.value("address")))\n  trustForwardHeader: \(values.flag("trust"))"
    let headers = values.lines("headers")
    if !headers.isEmpty { yaml += "\n  authResponseHeaders:\n" + headers.map { "    - " + yamlString($0) }.joined(separator: "\n") }
    let maxBody = values.prefilled("maxBody")
    if Int(maxBody) != nil { yaml += "\n  maxResponseBodySize: \(maxBody)" }
    return yaml
}

private func buildIPAllowList(_ values: WizardValues) -> String {
    var yaml = "ipAllowList:\n  sourceRange:\n" + values.lines("cidrs").map { "    - " + yamlString($0) }.joined(separator: "\n")
    switch values.value("strategy", "direct") {
    case "depth":
        let depth = Int(values.value("depth", "1")).flatMap { $0 > 0 ? $0 : nil } ?? 1
        yaml += "\n  ipStrategy:\n    depth: \(depth)"
    case "excluded":
        let excluded = values.lines("excluded")
        if !excluded.isEmpty {
            yaml += "\n  ipStrategy:\n    excludedIPs:\n" + excluded.map { "      - " + yamlString($0) }.joined(separator: "\n")
        }
    default:
        break
    }
    return yaml
}
