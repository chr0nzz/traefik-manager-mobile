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

/// Card surface used across the app, like `TmCard` on Android.
struct Card<Content: View>: View {
    @ViewBuilder var content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) { content }
            .padding(Spacing.lg)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Palette.card, in: RoundedRectangle(cornerRadius: Radius.lg, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: Radius.lg, style: .continuous)
                    .strokeBorder(Palette.border, lineWidth: 1)
            )
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
