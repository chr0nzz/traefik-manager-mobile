import Foundation

/// The app language and the tag sent to the server, like `AppLanguage` on Android.
/// iOS only lists a per-app Language in its Settings app once the device has more than one
/// preferred language, so the app also offers its own picker through `AppleLanguages`.
enum AppLanguage {
    private static let overrideKey = "app_language_override"

    /// The language picked in the app, or nil to follow the system.
    static var selected: String? {
        UserDefaults.standard.string(forKey: overrideKey)
    }

    /// Takes effect the next time the app starts, which is when iOS reads `AppleLanguages`.
    static func select(_ tag: String?) {
        let defaults = UserDefaults.standard
        if let tag {
            defaults.set(tag, forKey: overrideKey)
            defaults.set([tag], forKey: "AppleLanguages")
        } else {
            defaults.removeObject(forKey: overrideKey)
            defaults.removeObject(forKey: "AppleLanguages")
        }
    }

    /// The language the app is running in right now.
    static var running: String { Bundle.main.preferredLocalizations.first ?? "en" }

    /// Each shipped language with its own name, sorted by that name.
    static var options: [(tag: String, name: String)] {
        shipped.filter { $0 != "en" }.map { tag in
            let name = Locale(identifier: tag).localizedString(forIdentifier: tag) ?? tag
            return (tag, name.capitalized(with: Locale(identifier: tag)))
        }
        .sorted { $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending }
    }

    static func name(of tag: String) -> String {
        (Locale(identifier: tag).localizedString(forIdentifier: tag) ?? tag).capitalized(with: Locale(identifier: tag))
    }

    /// Languages the app ships translations for. Grows with the translation phase.
    static var shipped: [String] {
        Bundle.main.localizations.filter { $0 != "Base" }
    }

    static var serverTag: String? {
        guard let preferred = Bundle.main.preferredLocalizations.first else { return nil }
        let locale = Locale(identifier: preferred)
        guard let language = locale.language.languageCode?.identifier,
              shipped.contains(where: { $0.hasPrefix(language) }) else { return nil }
        return tag(for: locale)
    }

    static func tag(for locale: Locale) -> String? {
        guard let language = locale.language.languageCode?.identifier else { return nil }
        let region = locale.region?.identifier ?? ""
        let script = locale.language.script?.identifier ?? ""
        switch language {
        case "en":
            return ["GB": "en-GB", "US": "en-US"][region]
        case "fr":
            return region == "CA" ? "fr-CA" : "fr"
        case "pt":
            return region == "BR" ? "pt-BR" : "pt"
        case "zh":
            if script == "Hans" { return "zh-Hans" }
            return script.isEmpty && ["", "CN", "SG"].contains(region) ? "zh-Hans" : nil
        default:
            return language
        }
    }
}
