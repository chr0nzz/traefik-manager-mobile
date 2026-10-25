import Foundation

struct Connection: Equatable, Sendable {
    var baseURL: URL
    var apiKey: String?
    var demo: Bool
}

/// The saved Traefik Manager instance. The URL lives in UserDefaults, the API key in the Keychain.
struct ConnectionStore {
    var defaults: UserDefaults = .standard

    private static let baseURLKey = "connection.base_url"
    private static let demoKey = "connection.demo"
    private static let apiKeyAccount = "api_key"
    static let demoBaseURL = URL(string: "https://demo.invalid")!

    func load() -> Connection? {
        guard let text = defaults.string(forKey: Self.baseURLKey), let url = URL(string: text) else { return nil }
        let demo = defaults.bool(forKey: Self.demoKey)
        return Connection(baseURL: url, apiKey: demo ? nil : Keychain.string(for: Self.apiKeyAccount), demo: demo)
    }

    func save(baseURL: URL, apiKey: String?) {
        defaults.set(baseURL.absoluteString, forKey: Self.baseURLKey)
        defaults.set(false, forKey: Self.demoKey)
        Keychain.set(apiKey, for: Self.apiKeyAccount)
    }

    func enterDemo() {
        defaults.set(Self.demoBaseURL.absoluteString, forKey: Self.baseURLKey)
        defaults.set(true, forKey: Self.demoKey)
        Keychain.set(nil, for: Self.apiKeyAccount)
    }

    func clear() {
        defaults.removeObject(forKey: Self.baseURLKey)
        defaults.removeObject(forKey: Self.demoKey)
        Keychain.set(nil, for: Self.apiKeyAccount)
    }
}
