import SwiftUI
import UIKit

/// Loads small remote images (route icons), sending the API key when the image lives on the
/// Traefik Manager host, like `IconAuthInterceptor`. Results are cached in memory.
actor ImageLoader {
    static let shared = ImageLoader()

    private let cache = NSCache<NSURL, UIImage>()
    private var failed = Set<URL>()
    private var inFlight: [URL: Task<UIImage?, Never>] = [:]

    func image(for url: URL, auth: Connection?) async -> UIImage? {
        if let cached = cache.object(forKey: url as NSURL) { return cached }
        if failed.contains(url) { return nil }
        if let task = inFlight[url] { return await task.value }
        let task = Task<UIImage?, Never> {
            var request = URLRequest(url: url, timeoutInterval: 15)
            if let auth, let key = auth.apiKey, !key.isEmpty,
               url.host()?.caseInsensitiveCompare(auth.baseURL.host() ?? "") == .orderedSame {
                request.setValue(key, forHTTPHeaderField: "X-Api-Key")
                request.setValue("fetch", forHTTPHeaderField: "X-Requested-With")
            }
            guard let (data, response) = try? await URLSession.shared.data(for: request),
                  (response as? HTTPURLResponse).map({ (200..<300).contains($0.statusCode) }) ?? true
            else { return nil }
            return UIImage(data: data)
        }
        inFlight[url] = task
        let image = await task.value
        inFlight[url] = nil
        if let image { cache.setObject(image, forKey: url as NSURL) } else { failed.insert(url) }
        return image
    }
}

struct RemoteImage<Placeholder: View>: View {
    @Environment(AppModel.self) private var app
    let url: URL?
    @ViewBuilder var placeholder: Placeholder

    @State private var image: UIImage?

    var body: some View {
        Group {
            if let image {
                Image(uiImage: image).resizable().scaledToFit()
            } else {
                placeholder
            }
        }
        .task(id: url) {
            image = nil
            guard let url else { return }
            image = await ImageLoader.shared.image(for: url, auth: app.connection)
        }
    }
}
