import Foundation

enum AdvisorySeverity: String, Sendable {
    case critical, high, moderate

    var label: String {
        switch self {
        case .critical: String(localized: "Critical")
        case .high: String(localized: "High")
        case .moderate: String(localized: "Moderate")
        }
    }
}

struct TraefikAdvisory: Sendable {
    let id: String
    let severity: AdvisorySeverity
    let url: URL
    let forwardAuthRelated: Bool
    let summary: String
    let fixedIn: [String]
    let affected: @Sendable (Int, Int, Int) -> Bool
}

struct AdvisoryNotice: Sendable {
    let advisory: TraefikAdvisory
    let version: String
    let forwardAuthInUse: Bool

    var message: String {
        var parts = [String(localized: "Your Traefik v\(version) is affected by \(advisory.id) (\(advisory.severity.label)) - \(advisory.summary).")]
        if forwardAuthInUse { parts.append(String(localized: "A forwardAuth middleware is in use, so this is high priority.")) }
        if advisory.fixedIn.count > 1 {
            parts.append(String(localized: "Update Traefik to \(advisory.fixedIn[0]) or \(advisory.fixedIn[1])."))
        } else if let only = advisory.fixedIn.first {
            parts.append(String(localized: "Update Traefik to \(only)."))
        }
        return parts.joined(separator: " ")
    }
}

/// The Traefik security advisories the web dashboard warns about. Port of `TraefikAdvisories`.
enum TraefikAdvisories {
    private static func advisory(
        _ id: String,
        _ severity: AdvisorySeverity,
        _ ghsa: String,
        forwardAuth: Bool,
        _ summary: String,
        fixedIn: [String],
        affected: @escaping @Sendable (Int, Int, Int) -> Bool
    ) -> TraefikAdvisory {
        TraefikAdvisory(
            id: id,
            severity: severity,
            url: URL(string: "https://github.com/traefik/traefik/security/advisories/\(ghsa)")!,
            forwardAuthRelated: forwardAuth,
            summary: summary,
            fixedIn: fixedIn,
            affected: affected
        )
    }

    static let all: [TraefikAdvisory] = [
        advisory("CVE-2026-88007", .critical, "GHSA-qqjf-53cj-pwvv", forwardAuth: false,
                 String(localized: "HTTP/3 backend connection reuse leaks NTLM and Kerberos identity between clients"),
                 fixedIn: ["v3.7.13", "v2.11.57"]) { maj, min, pat in
            maj < 2 || (maj == 2 && min == 11 && pat <= 56) || (maj == 3 && (min < 7 || (min == 7 && pat <= 12)))
        },
        advisory("CVE-2026-88009", .high, "GHSA-f52w-8j3h-j724", forwardAuth: true,
                 String(localized: "Rootless request target routes as / but is forwarded verbatim, bypassing path rules, forwardAuth and access logs"),
                 fixedIn: ["v3.7.13", "v2.11.57"]) { maj, min, pat in
            maj < 2 || (maj == 2 && (min < 11 || (min == 11 && pat <= 56))) || (maj == 3 && (min < 7 || (min == 7 && pat <= 12)))
        },
        advisory("CVE-2026-88008", .high, "GHSA-w4v4-9rw7-5326", forwardAuth: true,
                 String(localized: "h2c upgrade headers forwarded to the backend let a client tunnel past the whole middleware chain"),
                 fixedIn: ["v3.7.13", "v2.11.57"]) { maj, min, pat in
            (maj == 2 && min == 11 && pat >= 26 && pat <= 56)
                || (maj == 3 && ((min > 4 && min < 7) || (min == 4 && pat >= 2) || (min == 7 && pat <= 12)))
        },
        advisory("CVE-2026-88004", .high, "GHSA-v67p-phpq-fc8x", forwardAuth: true,
                 String(localized: "Header name sanitization (aliasHeadersStrategy, forwarded header stripping) bypassed via request trailers"),
                 fixedIn: ["v3.7.13"]) { maj, min, pat in
            maj == 3 && ((min >= 2 && min < 7) || (min == 7 && pat <= 12))
        },
        advisory("GHSA-qm9f-w54v-q2qh", .high, "GHSA-qm9f-w54v-q2qh", forwardAuth: false,
                 String(localized: "Preauthenticated Negotiate contaminates the shared backend connection pool"),
                 fixedIn: ["v3.7.14", "v2.11.58"]) { maj, min, pat in
            (maj == 2 && min == 11 && pat <= 57) || (maj == 3 && (min < 7 || (min == 7 && pat <= 13)))
        },
        advisory("GHSA-cw35-4q88-3rmp", .high, "GHSA-cw35-4q88-3rmp", forwardAuth: false,
                 String(localized: "NTLM and Kerberos sticky connections are not keyed by service, so one service TLS client config applies to another"),
                 fixedIn: ["v3.7.14", "v2.11.58"]) { maj, min, pat in
            (maj == 2 && min == 11 && pat <= 57) || (maj == 3 && (min < 7 || (min == 7 && pat <= 13)))
        },
        advisory("GHSA-53qr-784g-cj35", .high, "GHSA-53qr-784g-cj35", forwardAuth: false,
                 String(localized: "FastProxy backend connection pool leaks NTLM and Negotiate identity to unauthenticated clients"),
                 fixedIn: ["v3.7.14"]) { maj, min, pat in
            maj == 3 && ((min >= 2 && min < 7) || (min == 7 && pat <= 13))
        },
        advisory("CVE-2026-39858", .high, "GHSA-5m6w-wvh7-57vm", forwardAuth: true,
                 String(localized: "ForwardAuth authentication bypass via forwarded header aliases"),
                 fixedIn: ["v3.6.14", "v2.11.43"]) { maj, min, pat in
            maj < 2 || (maj == 2 && (min < 11 || (min == 11 && pat < 43))) || (maj == 3 && (min < 6 || (min == 6 && pat < 14)))
        },
        advisory("GHSA-rf44-j88r-hh8c", .moderate, "GHSA-rf44-j88r-hh8c", forwardAuth: true,
                 String(localized: "ForwardAuth identity spoofing via dot-form header aliases"),
                 fixedIn: ["v3.7.12", "v2.11.56"]) { maj, min, pat in
            maj < 2 || (maj == 2 && (min < 11 || (min == 11 && pat <= 55))) || (maj == 3 && (min < 7 || (min == 7 && pat <= 11)))
        },
        advisory("CVE-2026-88010", .moderate, "GHSA-8fcf-v89g-xpg6", forwardAuth: false,
                 String(localized: "BasicAuth request coalescing reintroduces a username enumeration timing oracle"),
                 fixedIn: ["v3.7.13"]) { maj, min, pat in
            maj == 3 && ((min == 6 && pat >= 11) || (min == 7 && pat <= 12))
        },
        advisory("GHSA-2272-4vv8-65ww", .moderate, "GHSA-2272-4vv8-65ww", forwardAuth: false,
                 String(localized: "Negated Host matcher causes a TLS option conflict that disables client certificate authentication on the excluded host"),
                 fixedIn: ["v3.7.13", "v2.11.58"]) { maj, min, pat in
            maj < 2 || (maj == 2 && (min < 11 || (min == 11 && pat <= 57))) || (maj == 3 && (min < 7 || (min == 7 && pat <= 12)))
        },
        advisory("GHSA-mhjw-hmjv-p99x", .moderate, "GHSA-mhjw-hmjv-p99x", forwardAuth: false,
                 String(localized: "Unsynchronized write to the sticky round tripper shared by concurrent HTTP/2 streams"),
                 fixedIn: ["v3.7.14", "v2.11.58"]) { maj, min, pat in
            (maj == 2 && min == 11 && pat <= 57) || (maj == 3 && (min < 7 || (min == 7 && pat <= 13)))
        },
        advisory("GHSA-hrpp-p4fc-wwf8", .high, "GHSA-hrpp-p4fc-wwf8", forwardAuth: true,
                 String(localized: "ForwardAuth shares its auth server connections between clients, leaking NTLM and Negotiate identity"),
                 fixedIn: ["v3.7.16", "v2.11.60"]) { maj, min, pat in
            maj < 2 || (maj == 2 && (min < 11 || (min == 11 && pat <= 59))) || (maj == 3 && (min < 7 || (min == 7 && pat <= 15)))
        },
        advisory("GHSA-ww7c-q3mv-xx22", .high, "GHSA-ww7c-q3mv-xx22", forwardAuth: false,
                 String(localized: "FastProxy data race after an HTTP/1 upgrade lets an unauthenticated client crash Traefik"),
                 fixedIn: ["v3.7.15"]) { maj, min, pat in
            maj == 3 && ((min >= 2 && min < 7) || (min == 7 && pat <= 14))
        },
    ]

    static func parse(_ version: String?) -> (Int, Int, Int)? {
        guard let version,
              let match = version.firstMatch(of: #/(\d+)\.(\d+)\.(\d+)/#),
              let maj = Int(match.1), let min = Int(match.2), let pat = Int(match.3) else { return nil }
        return (maj, min, pat)
    }

    static func firstAffecting(_ version: String?) -> TraefikAdvisory? {
        guard let parts = parse(version) else { return nil }
        return all.first { $0.affected(parts.0, parts.1, parts.2) }
    }

    static func forwardAuthInUse(_ middlewares: [TraefikObject]) -> Bool {
        middlewares.contains { obj in
            [obj.type ?? "", obj.name].contains { $0.range(of: "forward[_-]?auth", options: [.regularExpression, .caseInsensitive]) != nil }
        }
    }

    private static let dismissPrefix = "advisory_dismissed_"

    static func dismissed(_ id: String, defaults: UserDefaults = .standard) -> String? {
        defaults.string(forKey: dismissPrefix + id)
    }

    static func dismiss(_ notice: AdvisoryNotice, defaults: UserDefaults = .standard) {
        defaults.set(notice.version, forKey: dismissPrefix + notice.advisory.id)
    }

    static func notice(version: String?, middlewares: [TraefikObject], dismissed: (String) -> String?) -> AdvisoryNotice? {
        guard let advisory = firstAffecting(version), let parts = parse(version) else { return nil }
        let clean = "\(parts.0).\(parts.1).\(parts.2)"
        if dismissed(advisory.id) == clean { return nil }
        return AdvisoryNotice(advisory: advisory, version: clean, forwardAuthInUse: advisory.forwardAuthRelated && forwardAuthInUse(middlewares))
    }
}
