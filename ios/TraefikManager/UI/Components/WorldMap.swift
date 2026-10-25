import SwiftUI

/// Country outlines from the same `world-map.svg` the Android app ships. Only M/L/Z commands are used.
enum WorldMapShapes {
    static let viewBox = CGRect(x: 0, y: 10, width: 1000, height: 415)

    static let shapes: [(code: String, path: Path)] = {
        guard let url = Bundle.main.url(forResource: "world-map", withExtension: "svg"),
              let svg = try? String(contentsOf: url, encoding: .utf8) else { return [] }
        return svg.matches(of: #/data-cc="([A-Z]{2})"[^>]*?\sd="([^"]+)"/#).map { match in
            (String(match.output.1), parse(String(match.output.2)))
        }
    }()

    private static func parse(_ data: String) -> Path {
        var path = Path()
        var command: Character = "M"
        var numbers: [CGFloat] = []
        func flush() {
            var index = 0
            while index + 1 < numbers.count {
                let point = CGPoint(x: numbers[index], y: numbers[index + 1])
                if command == "M" && index == 0 { path.move(to: point) } else { path.addLine(to: point) }
                index += 2
            }
            numbers.removeAll()
        }
        var token = ""
        for character in data {
            if character == "M" || character == "L" || character == "Z" {
                if !token.isEmpty, let value = Double(token) { numbers.append(value) }
                token = ""
                flush()
                if character == "Z" { path.closeSubpath() }
                command = character
            } else if character == " " || character == "," {
                if !token.isEmpty, let value = Double(token) { numbers.append(value) }
                token = ""
            } else {
                token.append(character)
            }
        }
        if !token.isEmpty, let value = Double(token) { numbers.append(value) }
        flush()
        return path
    }
}

/// Choropleth of traffic by country, tinted on a log scale. Port of `WorldMap`.
struct WorldMap: View {
    let counts: [String: Int]
    var selected: String?

    var body: some View {
        let max = counts.values.max() ?? 0
        Canvas { context, size in
            let box = WorldMapShapes.viewBox
            let scale = size.width / box.width
            context.scaleBy(x: scale, y: scale)
            context.translateBy(x: -box.minX, y: -box.minY)
            for shape in WorldMapShapes.shapes {
                let count = counts[shape.code] ?? 0
                let colour: Color
                if count <= 0 {
                    colour = Palette.muted.opacity(0.18)
                } else {
                    let ratio = log(Double(count + 1)) / log(Double(max + 1))
                    colour = Palette.blue.opacity(min(Swift.max(0.28 + 0.72 * ratio, 0.28), 1))
                }
                context.fill(shape.path, with: .color(colour))
                if shape.code == selected {
                    context.stroke(shape.path, with: .color(Palette.blue), lineWidth: 1.4 / scale)
                }
            }
        }
        .aspectRatio(WorldMapShapes.viewBox.width / WorldMapShapes.viewBox.height, contentMode: .fit)
        .accessibilityElement()
        .accessibilityLabel(counts.isEmpty ? String(localized: "World map, no located traffic") : String(localized: "World map, \(counts.count) countries with traffic"))
    }
}

/// Map plus the top countries, tap to filter. Port of `CountryStrip` (card version).
struct GeographyCard: View {
    let countries: [CountryCount]
    let selected: String?
    var limit = 6
    let onSelect: (String) -> Void

    var body: some View {
        let total = max(countries.reduce(0) { $0 + $1.count }, 1)
        Card(spacing: Spacing.xs) {
            HStack {
                SectionLabel(text: String(localized: "Geography"))
                Spacer()
                Text(String(localized: "\(countries.count) countries")).font(.caption2).foregroundStyle(Palette.muted)
            }
            if !countries.isEmpty {
                WorldMap(counts: Dictionary(uniqueKeysWithValues: countries.map { ($0.code, $0.count) }), selected: selected)
                    .padding(.vertical, Spacing.xs)
            }
            ForEach(countries.prefix(limit)) { country in
                Button { onSelect(country.code) } label: {
                    HStack(spacing: Spacing.sm) {
                        Text(country.flag).accessibilityHidden(true)
                        Text(country.name).font(.caption2).foregroundStyle(selected == country.code ? Palette.blue : Palette.text).lineLimit(1)
                        Spacer()
                        Text(verbatim: "\(country.count)").font(.caption2.monospaced().weight(.bold)).foregroundStyle(Palette.muted)
                        Text(verbatim: "\(country.count * 100 / total)%").font(.caption2).foregroundStyle(Palette.muted)
                    }
                    .padding(.vertical, 3)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }
            if countries.count > limit {
                Text(String(localized: "+\(countries.count - limit) more countries")).font(.caption2).foregroundStyle(Palette.muted)
            }
        }
    }
}
