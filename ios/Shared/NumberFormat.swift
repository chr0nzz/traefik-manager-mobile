import Foundation

enum NumberFormat {
    static var locale: Locale {
        locale(language: Bundle.main.preferredLocalizations.first ?? "en")
    }

    static func locale(language: String, base: Locale = .current) -> Locale {
        let numbering = Locale(identifier: language).numberingSystem
        guard numbering.identifier != "latn" else { return base }
        var components = Locale.Components(locale: base)
        components.languageComponents = Locale.Language.Components(identifier: language)
        components.numberingSystem = numbering
        components.calendar = base.calendar.identifier
        return Locale(components: components)
    }

    static func count<T: BinaryInteger>(_ value: T, locale: Locale = NumberFormat.locale) -> String {
        Int(value).formatted(.number.locale(locale))
    }

    static func decimal(_ value: Double, digits: ClosedRange<Int>, locale: Locale = NumberFormat.locale) -> String {
        value.formatted(.number.precision(.fractionLength(digits)).locale(locale))
    }
}

extension BinaryInteger {
    var fmt: String { NumberFormat.count(self) }
}
