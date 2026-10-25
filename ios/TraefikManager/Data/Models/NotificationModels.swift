import Foundation

enum NotificationSeverity { case success, info, warning, error }

struct TmNotification: Codable, Hashable, Sendable {
    var ts = ""
    var type = "info"
    var msg = ""
    var category = ""
    var id = 0
    var at: Int64 = 0

    private enum CodingKeys: String, CodingKey { case ts, type, msg, category, id, at }

    init(ts: String, type: String, msg: String, category: String, id: Int, at: Int64) {
        self.ts = ts
        self.type = type
        self.msg = msg
        self.category = category
        self.id = id
        self.at = at
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        ts = c.lenientString(.ts)
        type = c.value(.type, default: "info")
        msg = c.lenientString(.msg)
        category = c.value(.category, default: "")
        id = c.lenientInt(.id)
        at = Int64(c.lenientInt(.at))
    }

    var severity: NotificationSeverity {
        switch type.lowercased() {
        case "success": .success
        case "warning": .warning
        case "error": .error
        default: .info
        }
    }

    /// Server time, formatted like Android's `d MMM, HH:mm:ss`.
    var stamp: String {
        let display = DateFormatter()
        display.dateFormat = "d MMM, HH:mm:ss"
        if at > 0 { return display.string(from: Date(timeIntervalSince1970: TimeInterval(at))) }
        let parser = DateFormatter()
        parser.locale = Locale(identifier: "en_US_POSIX")
        parser.dateFormat = "yyyy-MM-dd HH:mm:ss"
        return parser.date(from: ts).map(display.string(from:)) ?? ts
    }

    func since(_ now: Date = Date()) -> String? {
        guard at > 0 else { return nil }
        let seconds = Int64(now.timeIntervalSince1970) - at
        guard seconds >= 0 else { return nil }
        if seconds < 60 { return String(localized: "just now") }
        if seconds < 3600 { return String(localized: "\(Int(seconds / 60)) min ago") }
        if seconds < 86_400 { return String(localized: "\(Int(seconds / 3600)) h ago") }
        if seconds < 604_800 { return String(localized: "\(Int(seconds / 86_400)) d ago") }
        return nil
    }
}

struct NotificationState: Decodable, Sendable {
    var readUntil = 0
    var count = 0
    var unread = 0

    private enum CodingKeys: String, CodingKey {
        case count, unread
        case readUntil = "read_until"
    }

    init(readUntil: Int, count: Int, unread: Int) {
        self.readUntil = readUntil
        self.count = count
        self.unread = unread
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        readUntil = c.lenientInt(.readUntil)
        count = c.lenientInt(.count)
        unread = c.lenientInt(.unread)
    }
}

struct DeleteNotificationRequest: Encodable {
    var ts: String
    var id: Int?
}

struct MarkReadRequest: Encodable { var id: Int }

struct WebhookTestRequest: Encodable {
    var url: String
    var webhookType: String
    var username: String
    var password: String

    private enum CodingKeys: String, CodingKey {
        case url, username, password
        case webhookType = "webhook_type"
    }
}

struct NotificationChannel: Codable, Identifiable, Hashable, Sendable {
    var id = ""
    var name = ""
    var kind = ""
    var enabled = true
    var url = ""
    var token = ""
    var token2 = ""
    var username = ""
    var password = ""
    var categories: [String] = []
    var minSeverity = "info"
    var digest = "immediate"
    var quietHours = ""
    var breakThrough = false

    private enum CodingKeys: String, CodingKey {
        case id, name, kind, enabled, url, token, token2, username, password, categories, digest
        case minSeverity = "min_severity"
        case quietHours = "quiet_hours"
        case breakThrough = "break_through"
    }

    init() {}

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.lenientString(.id)
        name = c.value(.name, default: "")
        kind = c.value(.kind, default: "")
        enabled = c.value(.enabled, default: true)
        url = c.value(.url, default: "")
        token = c.value(.token, default: "")
        token2 = c.value(.token2, default: "")
        username = c.value(.username, default: "")
        password = c.value(.password, default: "")
        categories = c.value(.categories, default: [])
        minSeverity = c.value(.minSeverity, default: "info")
        digest = c.value(.digest, default: "immediate")
        quietHours = c.value(.quietHours, default: "")
        breakThrough = c.value(.breakThrough, default: false)
    }

    func value(_ key: String) -> String {
        switch key {
        case "url": url
        case "token": token
        case "token2": token2
        default: ""
        }
    }

    mutating func setValue(_ key: String, _ value: String) {
        switch key {
        case "url": url = value
        case "token": token = value
        case "token2": token2 = value
        default: break
        }
    }

    var missingFields: [String] {
        (ChannelKinds.of(kind)?.fields ?? []).filter { value($0.key).isBlank }.map(\.label)
    }

    /// One line describing the filters, port of `NotificationChannel.summary`.
    var summary: String {
        var parts: [String] = []
        let known = categories.filter { key in ChannelKinds.categories.contains { $0.key == key } }
        parts.append(known.isEmpty || known.count == ChannelKinds.categories.count
            ? String(localized: "All categories")
            : known.map(ChannelKinds.categoryLabel).joined(separator: ", "))
        if minSeverity != "info" { parts.append(String(localized: "\(ChannelKinds.severityLabel(minSeverity)) and above")) }
        if digest == "hourly" { parts.append(String(localized: "Hourly digest")) }
        if digest == "daily" { parts.append(String(localized: "Daily digest")) }
        if !quietHours.isBlank {
            parts.append(breakThrough ? String(localized: "Quiet \(quietHours), errors break through") : String(localized: "Quiet \(quietHours)"))
        }
        return parts.joined(separator: " · ")
    }

    /// The request body, dropping fields the kind does not use. Port of `ChannelDraft.payload`.
    var payload: NotificationChannel {
        let spec = ChannelKinds.of(kind)
        let uses = Set(spec?.fields.map(\.key) ?? [])
        var out = self
        out.name = name.trimmed
        out.url = uses.contains("url") ? url.trimmed : ""
        out.token = uses.contains("token") ? token.trimmed : ""
        out.token2 = uses.contains("token2") ? token2.trimmed : ""
        out.username = spec?.basicAuth == true ? username.trimmed : ""
        out.password = spec?.basicAuth == true ? password : ""
        return out
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(name, forKey: .name)
        try c.encode(kind, forKey: .kind)
        try c.encode(enabled, forKey: .enabled)
        try c.encode(url, forKey: .url)
        try c.encode(token, forKey: .token)
        try c.encode(token2, forKey: .token2)
        try c.encode(username, forKey: .username)
        try c.encode(password, forKey: .password)
        try c.encode(categories, forKey: .categories)
        try c.encode(minSeverity, forKey: .minSeverity)
        try c.encode(digest, forKey: .digest)
        try c.encode(quietHours, forKey: .quietHours)
        try c.encode(breakThrough, forKey: .breakThrough)
    }
}

struct ChannelListResponse: Decodable, Sendable {
    var channels: [NotificationChannel] = []

    private enum CodingKeys: String, CodingKey { case channels }

    init(from decoder: Decoder) throws {
        channels = (try? decoder.container(keyedBy: CodingKeys.self))?.value(.channels, default: []) ?? []
    }
}

struct ChannelSaveResponse: Decodable, Sendable {
    var ok = false
    var channel: NotificationChannel?
    var error: String?

    private enum CodingKeys: String, CodingKey { case ok, channel, error }

    init(ok: Bool, channel: NotificationChannel?) {
        self.ok = ok
        self.channel = channel
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        ok = c.value(.ok, default: false)
        channel = try? c.decodeIfPresent(NotificationChannel.self, forKey: .channel)
        error = c.optional(.error)
    }
}

struct ChannelTestResult: Decodable, Sendable {
    var ok = false
    var detail = ""
    var error: String?

    private enum CodingKeys: String, CodingKey { case ok, detail, error }

    init(ok: Bool) { self.ok = ok }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        ok = c.value(.ok, default: false)
        detail = c.value(.detail, default: "")
        error = c.optional(.error)
    }
}

struct ChannelField: Hashable {
    var key: String
    var label: String
    var help: String
    var placeholder = ""
    var secret = false
}

struct ChannelKind: Identifiable, Hashable {
    var key: String
    var label: String
    var pickerLabel: String
    var fields: [ChannelField]
    var basicAuth = false
    var id: String { key }
}

struct ChannelOption: Hashable {
    var key: String
    var label: String
}

/// Notification channel types and their fields. Port of `ChannelKinds`.
enum ChannelKinds {
    static var all: [ChannelKind] {
        let webhook = String(localized: "Webhook URL")
        return [
            ChannelKind(key: "discord", label: "Discord", pickerLabel: "Discord", fields: [
                ChannelField(key: "url", label: webhook, help: String(localized: "Where notifications are delivered."), placeholder: "https://discord.com/api/webhooks/..."),
            ]),
            ChannelKind(key: "slack", label: "Slack", pickerLabel: "Slack", fields: [
                ChannelField(key: "url", label: webhook, help: String(localized: "Incoming webhook created in your Slack workspace."), placeholder: "https://hooks.slack.com/services/..."),
            ]),
            ChannelKind(key: "ntfy", label: "ntfy", pickerLabel: "ntfy.sh / self-hosted ntfy", fields: [
                ChannelField(key: "url", label: "URL", help: String(localized: "Full topic URL on ntfy.sh or your own server."), placeholder: "https://ntfy.sh/my-topic"),
            ], basicAuth: true),
            ChannelKind(key: "generic", label: String(localized: "Generic JSON"), pickerLabel: String(localized: "Generic JSON"), fields: [
                ChannelField(key: "url", label: "URL", help: String(localized: "Receives a JSON body you can shape downstream."), placeholder: "https://example.com/hooks/traefik"),
            ], basicAuth: true),
            ChannelKind(key: "gotify", label: "Gotify", pickerLabel: "Gotify", fields: [
                ChannelField(key: "url", label: String(localized: "Server URL"), help: String(localized: "Base URL of your Gotify server."), placeholder: "https://gotify.example.com"),
                ChannelField(key: "token", label: String(localized: "App Token"), help: String(localized: "Application token from Gotify. Stored encrypted."), secret: true),
            ]),
            ChannelKind(key: "pushover", label: "Pushover", pickerLabel: "Pushover", fields: [
                ChannelField(key: "token", label: String(localized: "App Token"), help: String(localized: "Application token from your Pushover app. Stored encrypted."), secret: true),
                ChannelField(key: "token2", label: String(localized: "User Key"), help: String(localized: "Your Pushover user or group key. Stored encrypted."), secret: true),
            ]),
            ChannelKind(key: "pushbullet", label: "Pushbullet", pickerLabel: "Pushbullet", fields: [
                ChannelField(key: "token", label: String(localized: "Access Token"), help: String(localized: "Access token from your Pushbullet account. Stored encrypted."), secret: true),
            ]),
            ChannelKind(key: "unifiedpush", label: String(localized: "Mobile app"), pickerLabel: String(localized: "Mobile app"), fields: [
                ChannelField(key: "url", label: String(localized: "Device endpoint"), help: String(localized: "Registered by the Traefik Manager app on your phone. Editing it stops push to that device.")),
            ]),
            ChannelKind(key: "telegram", label: "Telegram", pickerLabel: "Telegram", fields: [
                ChannelField(key: "token", label: String(localized: "Bot Token"), help: String(localized: "Token issued by BotFather. Stored encrypted."), secret: true),
                ChannelField(key: "token2", label: String(localized: "Chat ID"), help: String(localized: "Target chat, group or channel to post into."), placeholder: "-1001234567890"),
            ]),
        ]
    }

    static func of(_ key: String) -> ChannelKind? { all.first { $0.key == key } }
    static func label(_ key: String) -> String { of(key)?.label ?? key }

    static var categories: [ChannelOption] {
        [
            ChannelOption(key: "config", label: String(localized: "Config")),
            ChannelOption(key: "backup", label: String(localized: "Backups")),
            ChannelOption(key: "security", label: String(localized: "Security")),
            ChannelOption(key: "traefik", label: "Traefik"),
            ChannelOption(key: "certs", label: String(localized: "Certificates")),
            ChannelOption(key: "crowdsec", label: "CrowdSec"),
            ChannelOption(key: "agent", label: String(localized: "Agents")),
            ChannelOption(key: "update", label: String(localized: "Updates")),
        ]
    }

    static var severities: [ChannelOption] {
        [
            ChannelOption(key: "info", label: String(localized: "Info")),
            ChannelOption(key: "success", label: String(localized: "Healthy")),
            ChannelOption(key: "warning", label: String(localized: "Warning")),
            ChannelOption(key: "error", label: String(localized: "Error")),
        ]
    }

    static var digests: [ChannelOption] {
        [
            ChannelOption(key: "immediate", label: String(localized: "Immediate")),
            ChannelOption(key: "hourly", label: String(localized: "Hourly")),
            ChannelOption(key: "daily", label: String(localized: "Daily")),
        ]
    }

    static func categoryLabel(_ key: String) -> String { categories.first { $0.key == key }?.label ?? key }
    static func severityLabel(_ key: String) -> String { severities.first { $0.key == key }?.label ?? key }
}

struct RouteHealthSettings: Encodable {
    var enabled: Bool
    var interval: Int
}

struct RouteHealthSaveResponse: Decodable, Sendable {
    var ok = false
    var enabled = true
    var interval = 300
    var error: String?

    private enum CodingKeys: String, CodingKey { case ok, enabled, interval, error }

    init(ok: Bool, enabled: Bool, interval: Int) {
        self.ok = ok
        self.enabled = enabled
        self.interval = interval
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        ok = c.value(.ok, default: false)
        enabled = c.value(.enabled, default: true)
        interval = c.lenientInt(.interval)
        if interval == 0 { interval = 300 }
        error = c.optional(.error)
    }
}

struct ClientIpDiagnostic: Decodable, Sendable {
    static let headerOrder = ["X-Forwarded-For", "X-Real-IP", "CF-Connecting-IP", "X-Forwarded-Proto", "X-Forwarded-Host"]

    var effectiveIp = ""
    var effectiveClass = ""
    var socketPeer = ""
    var socketPeerClass = ""
    var headers: [String: String] = [:]
    var forwardedForChain: [String] = []
    var proxyHops = 0
    var proxyTrusted: Bool?
    var trustedProxies: [String] = []
    var classes: [String: String] = [:]

    private enum CodingKeys: String, CodingKey {
        case headers, classes
        case effectiveIp = "effective_ip"
        case effectiveClass = "effective_class"
        case socketPeer = "socket_peer"
        case socketPeerClass = "socket_peer_class"
        case forwardedForChain = "forwarded_for_chain"
        case proxyHops = "proxy_hops"
        case proxyTrusted = "proxy_trusted"
        case trustedProxies = "trusted_proxies"
    }

    init() {}

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        effectiveIp = c.value(.effectiveIp, default: "")
        effectiveClass = c.value(.effectiveClass, default: "")
        socketPeer = c.value(.socketPeer, default: "")
        socketPeerClass = c.value(.socketPeerClass, default: "")
        headers = c.value(.headers, default: [:])
        forwardedForChain = c.value(.forwardedForChain, default: [])
        proxyHops = c.lenientInt(.proxyHops)
        proxyTrusted = c.optional(.proxyTrusted)
        trustedProxies = c.value(.trustedProxies, default: [])
        classes = c.value(.classes, default: [:])
    }
}

struct GenerateKeyRequest: Encodable {
    var deviceName: String
    private enum CodingKeys: String, CodingKey { case deviceName = "device_name" }
}

struct GenerateKeyResponse: Decodable, Sendable {
    var ok = false
    var key: String?
    var error: String?

    private enum CodingKeys: String, CodingKey { case ok, key, error }

    init(ok: Bool, key: String?) {
        self.ok = ok
        self.key = key
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        ok = c.value(.ok, default: false)
        key = c.optional(.key)
        error = c.optional(.error)
    }
}

struct RevokeKeyRequest: Encodable { var preview: String }
