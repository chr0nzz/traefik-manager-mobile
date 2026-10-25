import SwiftUI

/// Small coloured dot for server / backend health.
struct HealthDot: View {
    var reachable: Bool

    var body: some View {
        Circle()
            .fill(reachable ? Palette.green : Palette.red)
            .frame(width: 8, height: 8)
            .accessibilityLabel(reachable ? Text("Reachable") : Text("Unreachable"))
    }
}

/// Card surface used across the app, like `TmCard` on Android: optional status rail on the leading edge.
struct Card<Content: View>: View {
    var accent: Color?
    var spacing: CGFloat = Spacing.sm
    @ViewBuilder var content: Content

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: Radius.md, style: .continuous)
        VStack(alignment: .leading, spacing: spacing) { content }
            .padding(Spacing.md)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            .padding(.leading, accent == nil ? 0 : 3)
            .background(alignment: .leading) {
                if let accent { Rectangle().fill(accent).frame(width: 3) }
            }
            .background(Palette.card)
            .clipShape(shape)
            .overlay(shape.strokeBorder(Palette.border, lineWidth: 1))
    }
}

/// Small uppercase caption above a card's content, like `SectionLabel`.
struct SectionLabel: View {
    var text: String
    var lines = 1

    var body: some View {
        Text(text.uppercased())
            .font(.caption2.weight(.semibold))
            .kerning(0.6)
            .foregroundStyle(Palette.muted)
            .lineLimit(lines)
    }
}

/// Placeholder for screens that are not ported yet.
struct ComingSoonView: View {
    let destination: Destination

    var body: some View {
        ContentUnavailableView {
            Label(destination.label, systemImage: destination.systemImage)
        } description: {
            Text("This screen is coming in a later build.")
        }
        .navigationTitle(destination.label)
    }
}
