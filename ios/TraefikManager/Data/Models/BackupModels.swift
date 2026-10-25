import Foundation

enum BackupKind: String, Sendable {
    case routes, `static`, certs

    init(raw: String) {
        switch raw.lowercased() {
        case "static": self = .static
        case "certs": self = .certs
        default: self = .routes
        }
    }
}

struct BackupEntry: Identifiable, Hashable, Sendable {
    var name: String
    var size: Int64
    var stamp: String
    var kind: BackupKind
    var id: String { name }
}

/// `api/backups` on the hub (array) or an agent (object). Port of `HubBackup` / `AgentBackupsResponse`.
struct BackupsList: Decodable, Sendable {
    var entries: [BackupEntry] = []
    var staticConfigured: Bool?
    var kindKnown = true

    private struct Row: Decodable {
        var name = ""
        var size: Int64 = 0
        var stamp = ""
        var kind = ""

        private enum CodingKeys: String, CodingKey { case name, size, modified, date, kind }

        init(from decoder: Decoder) throws {
            let c = try decoder.container(keyedBy: CodingKeys.self)
            name = c.lenientString(.name)
            size = Int64(c.lenientInt(.size))
            stamp = c.value(.modified, default: c.value(.date, default: ""))
            kind = c.value(.kind, default: "")
        }
    }

    private enum CodingKeys: String, CodingKey {
        case backups
        case staticConfigured = "static_configured"
    }

    init(entries: [BackupEntry], staticConfigured: Bool? = nil) {
        self.entries = entries
        self.staticConfigured = staticConfigured
    }

    init(from decoder: Decoder) throws {
        if let rows = try? decoder.singleValueContainer().decode([Row].self) {
            entries = rows.map { BackupEntry(name: $0.name, size: $0.size, stamp: $0.stamp, kind: BackupKind(raw: $0.kind)) }
            return
        }
        let c = try decoder.container(keyedBy: CodingKeys.self)
        let rows: [Row] = c.value(.backups, default: [])
        entries = rows.map { BackupEntry(name: $0.name, size: $0.size, stamp: $0.stamp, kind: BackupKind(raw: $0.kind)) }
        staticConfigured = c.value(.staticConfigured, default: false)
        kindKnown = rows.isEmpty || rows.contains { !$0.kind.isEmpty }
    }
}

struct CreateBackupResponse: Decodable, Sendable {
    var success = false
    var ok = false
    var names: [String] = []
    var name = ""
    var error: String?

    var worked: Bool { success || ok }
    var created: [String] { names.isEmpty ? (name.isEmpty ? [] : [name]) : names }

    private enum CodingKeys: String, CodingKey { case success, ok, names, name, error }

    init(ok: Bool, names: [String]) {
        self.ok = ok
        self.names = names
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        success = c.value(.success, default: false)
        ok = c.value(.ok, default: false)
        names = c.value(.names, default: [])
        name = c.value(.name, default: "")
        error = c.optional(.error)
    }
}

struct RestoreResponse: Decodable, Sendable {
    var success = false
    var ok = false
    var error: String?
    var restarted: Bool?
    var restartError = ""

    var worked: Bool { success || ok }

    private enum CodingKeys: String, CodingKey {
        case success, ok, error, restarted
        case restartError = "restart_error"
    }

    init(ok: Bool, restarted: Bool? = nil) {
        self.ok = ok
        self.restarted = restarted
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        success = c.value(.success, default: false)
        ok = c.value(.ok, default: false)
        error = c.optional(.error)
        restarted = c.optional(.restarted)
        restartError = c.value(.restartError, default: "")
    }
}

struct GitStatus: Decodable, Sendable {
    var enabled = false
    var configured = false
    var lastSha = ""
    var lastPush = ""
    var branch = ""

    private enum CodingKeys: String, CodingKey {
        case enabled, configured, branch
        case lastSha = "last_sha"
        case lastPush = "last_push"
    }

    init(enabled: Bool, configured: Bool, lastSha: String = "", lastPush: String = "", branch: String = "") {
        self.enabled = enabled
        self.configured = configured
        self.lastSha = lastSha
        self.lastPush = lastPush
        self.branch = branch
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        enabled = c.value(.enabled, default: false)
        configured = c.value(.configured, default: false)
        lastSha = c.value(.lastSha, default: "")
        lastPush = c.value(.lastPush, default: "")
        branch = c.value(.branch, default: "")
    }
}

struct GitCommit: Decodable, Identifiable, Hashable, Sendable {
    var sha = ""
    var shaShort = ""
    var timestamp = ""
    var message = ""

    var id: String { sha }

    private enum CodingKeys: String, CodingKey {
        case sha, timestamp, message
        case shaShort = "sha_short"
    }

    init(sha: String, shaShort: String, timestamp: String, message: String) {
        self.sha = sha
        self.shaShort = shaShort
        self.timestamp = timestamp
        self.message = message
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        sha = c.value(.sha, default: "")
        shaShort = c.value(.shaShort, default: String(sha.prefix(8)))
        timestamp = c.value(.timestamp, default: "")
        message = c.value(.message, default: "")
    }
}

struct GitDiffFile: Decodable, Hashable, Sendable {
    var filename = ""
    var status = ""
    var old = ""
    var new = ""

    private enum CodingKeys: String, CodingKey { case filename, status, old, new }

    init(filename: String, status: String, old: String = "", new: String = "") {
        self.filename = filename
        self.status = status
        self.old = old
        self.new = new
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        filename = c.value(.filename, default: "")
        status = c.value(.status, default: "")
        old = c.value(.old, default: "")
        new = c.value(.new, default: "")
    }

    var statusWord: String {
        switch status.first {
        case "A": String(localized: "added")
        case "D": String(localized: "deleted")
        case "M": String(localized: "changed")
        case "R": String(localized: "renamed")
        default: status.lowercased()
        }
    }
}

struct GitDiff: Decodable, Sendable {
    var stat = ""
    var files: [GitDiffFile] = []
    var error: String?

    private enum CodingKeys: String, CodingKey { case stat, files, error }

    init(stat: String, files: [GitDiffFile]) {
        self.stat = stat
        self.files = files
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        stat = c.value(.stat, default: "")
        files = c.value(.files, default: [])
        error = c.optional(.error)
    }
}

/// One line of a unified-style comparison between two file versions.
struct DiffLine: Hashable {
    enum Kind { case same, added, removed }
    var kind: Kind
    var text: String
}

/// Line diff by longest common subsequence; the server only sends old and new text.
enum LineDiff {
    static func lines(old: String, new: String) -> [DiffLine] {
        let a = old.isEmpty ? [] : old.components(separatedBy: "\n")
        let b = new.isEmpty ? [] : new.components(separatedBy: "\n")
        guard a.count * b.count <= 4_000_000 else {
            return a.map { DiffLine(kind: .removed, text: $0) } + b.map { DiffLine(kind: .added, text: $0) }
        }
        var table = Array(repeating: Array(repeating: 0, count: b.count + 1), count: a.count + 1)
        for i in stride(from: a.count - 1, through: 0, by: -1) {
            for j in stride(from: b.count - 1, through: 0, by: -1) {
                table[i][j] = a[i] == b[j] ? table[i + 1][j + 1] + 1 : max(table[i + 1][j], table[i][j + 1])
            }
        }
        var result: [DiffLine] = []
        var i = 0, j = 0
        while i < a.count && j < b.count {
            if a[i] == b[j] {
                result.append(DiffLine(kind: .same, text: a[i])); i += 1; j += 1
            } else if table[i + 1][j] >= table[i][j + 1] {
                result.append(DiffLine(kind: .removed, text: a[i])); i += 1
            } else {
                result.append(DiffLine(kind: .added, text: b[j])); j += 1
            }
        }
        result += a[i...].map { DiffLine(kind: .removed, text: $0) }
        result += b[j...].map { DiffLine(kind: .added, text: $0) }
        return result
    }
}

enum ByteSize {
    static func label(_ bytes: Int64) -> String {
        if bytes >= 1_048_576 { return "\(bytes / 1_048_576) MB" }
        if bytes >= 1024 { return "\(bytes / 1024) KB" }
        return String(localized: "\(Int(bytes)) bytes")
    }
}

struct GitPushRequest: Encodable { var message: String }
