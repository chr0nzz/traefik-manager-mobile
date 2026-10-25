import CryptoKit
import Foundation

/// Last-known API responses per server, so screens open instantly while a refresh runs. Port of `SnapshotStore`.
enum SnapshotStore {
    private static var directory: URL {
        let base = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]
        return base.appendingPathComponent("snapshots", isDirectory: true)
    }

    static func key(baseURL: URL, agentId: String?) -> String {
        let digest = SHA256.hash(data: Data("\(baseURL.absoluteString)|\(agentId ?? "")".utf8))
        return digest.prefix(12).map { String(format: "%02x", $0) }.joined()
    }

    static func read<T: Decodable>(_ type: T.Type, name: String, key: String) -> T? {
        let url = directory.appendingPathComponent("\(name)-\(key).json")
        guard let data = try? Data(contentsOf: url) else { return nil }
        return try? JSONDecoder().decode(T.self, from: data)
    }

    static func write<T: Encodable>(_ value: T, name: String, key: String) {
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        guard let data = try? JSONEncoder().encode(value) else { return }
        try? data.write(to: directory.appendingPathComponent("\(name)-\(key).json"), options: .atomic)
    }

    static func clear() {
        try? FileManager.default.removeItem(at: directory)
    }
}
