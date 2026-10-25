import Foundation

enum HTTPMethod: String {
    case get = "GET"
    case post = "POST"
    case put = "PUT"
    case delete = "DELETE"
}

enum APIError: LocalizedError, Equatable {
    case invalidURL
    case http(status: Int, message: String?)
    case unreachable(String)
    case decoding(String)
    case server(String)

    var errorDescription: String? {
        switch self {
        case .invalidURL:
            String(localized: "The server URL is not valid")
        case let .http(status, message):
            message ?? String(localized: "Server error \(status)")
        case let .unreachable(detail):
            detail
        case let .decoding(detail):
            String(localized: "Unexpected response from the server: \(detail)")
        case let .server(message):
            message
        }
    }
}

/// Talks to one Traefik Manager instance, optionally scoped to an agent.
///
/// Mirrors the Android OkHttp interceptor chain: every request carries
/// `X-Requested-With: fetch`, the API key when set, the app language as `lang`,
/// and agent-scoped paths are rewritten onto `/api/agents/proxy/{id}/…`.
struct APIClient: Sendable {
    let baseURL: URL
    let apiKey: String?
    let agentId: String?
    var languageTag: @Sendable () -> String? = { AppLanguage.serverTag }
    var session: URLSession = .shared

    static let decoder = JSONDecoder()
    static let encoder = JSONEncoder()

    func scoped(to agentId: String?) -> APIClient {
        APIClient(baseURL: baseURL, apiKey: apiKey, agentId: agentId, languageTag: languageTag, session: session)
    }

    func get<T: Decodable>(_ path: String, query: [URLQueryItem] = []) async throws -> T {
        try await send(.get, path, query: query, body: Optional<Empty>.none)
    }

    func post<T: Decodable, B: Encodable>(_ path: String, query: [URLQueryItem] = [], body: B?) async throws -> T {
        try await send(.post, path, query: query, body: body)
    }

    func post<T: Decodable>(_ path: String, query: [URLQueryItem] = []) async throws -> T {
        try await send(.post, path, query: query, body: Optional<Empty>.none)
    }

    func put<T: Decodable, B: Encodable>(_ path: String, body: B) async throws -> T {
        try await send(.put, path, query: [], body: body)
    }

    func delete<T: Decodable>(_ path: String, query: [URLQueryItem] = []) async throws -> T {
        try await send(.delete, path, query: query, body: Optional<Empty>.none)
    }

    /// POSTs `application/x-www-form-urlencoded` fields, used by the legacy `/save` and `/delete` endpoints.
    func postForm<T: Decodable>(_ path: String, fields: [(String, String)]) async throws -> T {
        var request = try makeRequest(.post, path, query: [])
        var allowed = CharacterSet.alphanumerics
        allowed.insert(charactersIn: "-._~")
        let encode = { (text: String) in text.addingPercentEncoding(withAllowedCharacters: allowed) ?? text }
        request.httpBody = Data(fields.map { "\(encode($0.0))=\(encode($0.1))" }.joined(separator: "&").utf8)
        request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
        let data = try await perform(request)
        do {
            return try Self.decoder.decode(T.self, from: data)
        } catch {
            throw APIError.decoding(String(describing: error))
        }
    }

    func send<T: Decodable, B: Encodable>(
        _ method: HTTPMethod,
        _ path: String,
        query: [URLQueryItem],
        body: B?
    ) async throws -> T {
        var request = try makeRequest(method, path, query: query)
        if let body {
            request.httpBody = try Self.encoder.encode(body)
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        }
        let data = try await perform(request)
        do {
            return try Self.decoder.decode(T.self, from: data)
        } catch {
            throw APIError.decoding(String(describing: error))
        }
    }

    func makeRequest(_ method: HTTPMethod, _ path: String, query: [URLQueryItem]) throws -> URLRequest {
        let proxied = Self.proxiedPath(path, agentId: agentId, query: query)
        guard var components = URLComponents(url: baseURL, resolvingAgainstBaseURL: false) else {
            throw APIError.invalidURL
        }
        let basePath = components.percentEncodedPath.hasSuffix("/")
            ? String(components.percentEncodedPath.dropLast())
            : components.percentEncodedPath
        components.percentEncodedPath = basePath + "/" + proxied
        var items = query
        if let lang = languageTag(), !lang.isEmpty, !items.contains(where: { $0.name == "lang" }) {
            items.append(URLQueryItem(name: "lang", value: lang))
        }
        components.queryItems = items.isEmpty ? nil : items
        guard let url = components.url else { throw APIError.invalidURL }

        var request = URLRequest(url: url)
        request.httpMethod = method.rawValue
        request.timeoutInterval = 30
        request.setValue("fetch", forHTTPHeaderField: "X-Requested-With")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        if let apiKey, !apiKey.isEmpty {
            request.setValue(apiKey, forHTTPHeaderField: "X-Api-Key")
        }
        return request
    }

    private func perform(_ request: URLRequest) async throws -> Data {
        let data: Data
        let response: URLResponse
        do {
            (data, response) = try await session.data(for: request)
        } catch let error as URLError {
            throw APIError.unreachable(error.localizedDescription)
        }
        guard let http = response as? HTTPURLResponse else { return data }
        guard (200..<300).contains(http.statusCode) else {
            throw APIError.http(status: http.statusCode, message: Self.errorMessage(in: data))
        }
        return data
    }

    static func errorMessage(in data: Data) -> String? {
        guard let object = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return nil }
        for key in ["error", "message", "detail"] {
            if let text = object[key] as? String, !text.isEmpty { return text }
        }
        return nil
    }

    // MARK: - Agent proxy

    private static let proxiedRoots: Set<String> = [
        "traefik", "backups", "backup", "restore", "crowdsec", "static", "configs",
    ]

    /// Rewrites `api/traefik/...` and friends onto the hub's agent proxy, like `AgentProxyInterceptor`.
    static func proxiedPath(_ path: String, agentId: String?, query: [URLQueryItem]) -> String {
        let trimmed = path.hasPrefix("/") ? String(path.dropFirst()) : path
        guard let agentId, !agentId.isEmpty, trimmed.hasPrefix("api/") else { return trimmed }
        if query.contains(where: { $0.name == "agent_id" }) { return trimmed }
        let tail = String(trimmed.dropFirst("api/".count))
        let root = tail.split(separator: "/", maxSplits: 1).first.map(String.init) ?? tail
        let isRawRoute = tail.hasPrefix("routes/") && tail.hasSuffix("/raw")
        guard proxiedRoots.contains(root) || isRawRoute else { return trimmed }
        return "api/agents/proxy/\(agentId)/\(tail)"
    }

    /// Percent-encodes a single path segment such as a route id (`routes.yml::name`).
    static func segment(_ value: String) -> String {
        var allowed = CharacterSet.urlPathAllowed
        allowed.remove(charactersIn: "/?#")
        return value.addingPercentEncoding(withAllowedCharacters: allowed) ?? value
    }
}

struct Empty: Codable, Sendable {}
