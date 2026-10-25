import Foundation

/// Language tags shared with the server. iOS handles the per-app language itself
/// (Settings > Apps > Traefik Manager > Language), so this only maps the active locale
/// onto the tag the Traefik Manager API understands, like `AppLanguage.serverTag` on Android.
enum AppLanguage {
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
