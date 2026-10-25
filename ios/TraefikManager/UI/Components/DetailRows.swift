import SwiftUI

/// Titled card with an icon, used by every detail screen. Port of `DetailSection`.
struct DetailSection<Content: View>: View {
    var title: String
    var systemImage: String
    var tint: Color
    @ViewBuilder var content: Content

    var body: some View {
        Card(spacing: 0) {
            HStack(spacing: Spacing.sm) {
                Image(systemName: systemImage)
                    .font(.system(size: 13))
                    .foregroundStyle(tint)
                Text(title)
                    .font(.subheadline.weight(.semibold))
                    .accessibilityAddTraits(.isHeader)
            }
            .padding(.bottom, Spacing.xs)
            content
        }
    }
}

/// Label / value row with an optional status dot. Port of `DetailRow`.
struct DetailRow: View {
    var label: String
    var value: String
    var mono = false
    var status: TMStatus?
    var last = false

    var body: some View {
        VStack(spacing: 0) {
            HStack(alignment: .firstTextBaseline, spacing: Spacing.md) {
                if !label.isEmpty {
                    SectionLabel(text: label, lines: 2)
                        .frame(width: 118, alignment: .leading)
                }
                HStack(spacing: Spacing.xs) {
                    if let status { StatusDot(status: status, size: 6) }
                    Text(value)
                        .font(mono ? .caption.monospaced() : .subheadline)
                        .foregroundStyle(status == .ok ? Palette.green : Palette.text)
                        .textSelection(.enabled)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .padding(.vertical, Spacing.sm)
            .accessibilityElement(children: .combine)
            if !last { Divider().overlay(Palette.border) }
        }
    }
}
