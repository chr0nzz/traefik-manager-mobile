import SwiftUI

/// Shared health vocabulary, port of `TmStatus`.
enum TMStatus: Int, Codable, Hashable, Sendable {
    case ok, warn, error, unknown, disabled

    var label: String {
        switch self {
        case .ok: String(localized: "healthy")
        case .warn: String(localized: "degraded")
        case .error: String(localized: "down")
        case .unknown: String(localized: "unknown")
        case .disabled: String(localized: "disabled")
        }
    }

    /// Lower sorts first: problems before healthy objects.
    var severity: Int {
        switch self {
        case .error: 0
        case .warn: 1
        case .unknown: 2
        case .disabled: 3
        case .ok: 4
        }
    }

    var color: Color {
        switch self {
        case .ok: Palette.green
        case .warn: Palette.yellow
        case .error: Palette.red
        case .unknown, .disabled: Palette.muted
        }
    }
}

struct StatusDot: View {
    var status: TMStatus
    var size: CGFloat = 8

    var body: some View {
        Circle()
            .fill(status.color)
            .frame(width: size, height: size)
            .accessibilityLabel(status.label)
    }
}

struct HealthLabel: View {
    var status: TMStatus
    var text: String

    var body: some View {
        HStack(spacing: 5) {
            StatusDot(status: status, size: 6).accessibilityHidden(true)
            Text(text)
                .font(.caption.weight(.medium))
                .foregroundStyle(status.color)
        }
    }
}

/// One small square per object, problems first. Port of `SignalStrip`.
struct SignalStrip: View {
    var cells: [TMStatus]
    var cellSize: CGFloat = 6
    var maxCells = 150
    var emptyLabel: String?

    var body: some View {
        if cells.isEmpty {
            if let emptyLabel {
                Text(emptyLabel)
                    .font(.caption2.monospaced())
                    .foregroundStyle(Palette.muted)
            }
        } else {
            let sorted = cells.sorted { $0.severity < $1.severity }
            let shown = sorted.prefix(maxCells)
            let overflow = sorted.count - shown.count
            FlowLayout(spacing: 2) {
                ForEach(Array(shown.enumerated()), id: \.offset) { _, cell in
                    StripCell(status: cell, size: cellSize)
                }
                if overflow > 0 {
                    Text(verbatim: "+\(overflow)")
                        .font(.caption2.monospaced())
                        .foregroundStyle(Palette.muted)
                        .padding(.leading, 3)
                }
            }
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(Self.summary(sorted))
        }
    }

    static func summary(_ cells: [TMStatus]) -> String {
        Dictionary(grouping: cells, by: \.self)
            .sorted { $0.key.severity < $1.key.severity }
            .map { "\($0.value.count) \($0.key.label)" }
            .joined(separator: ", ")
    }
}

private struct StripCell: View {
    var status: TMStatus
    var size: CGFloat

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 1.5)
        switch status {
        case .ok: shape.fill(Palette.muted.opacity(0.6)).frame(width: size, height: size)
        case .warn: shape.fill(Palette.yellow).frame(width: size, height: size)
        case .error: shape.fill(Palette.red).frame(width: size, height: size)
        case .unknown, .disabled:
            shape.strokeBorder(Palette.muted.opacity(0.55), lineWidth: 1).frame(width: size, height: size)
        }
    }
}

/// Wrapping row layout, like Compose `FlowRow`.
struct FlowLayout: Layout {
    var spacing: CGFloat = 4
    var lineSpacing: CGFloat?

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let rows = arrange(proposal: proposal, subviews: subviews)
        let width = rows.map(\.width).max() ?? 0
        let height = rows.reduce(0) { $0 + $1.height } + CGFloat(max(rows.count - 1, 0)) * (lineSpacing ?? spacing)
        return CGSize(width: proposal.width ?? width, height: height)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var y = bounds.minY
        for row in arrange(proposal: ProposedViewSize(width: bounds.width, height: nil), subviews: subviews) {
            var x = bounds.minX
            for index in row.indices {
                let size = subviews[index].sizeThatFits(.unspecified)
                subviews[index].place(
                    at: CGPoint(x: x, y: y + (row.height - size.height) / 2),
                    proposal: ProposedViewSize(size)
                )
                x += size.width + spacing
            }
            y += row.height + (lineSpacing ?? spacing)
        }
    }

    private struct Row {
        var indices: [Int] = []
        var width: CGFloat = 0
        var height: CGFloat = 0
    }

    private func arrange(proposal: ProposedViewSize, subviews: Subviews) -> [Row] {
        let maxWidth = proposal.width ?? .infinity
        var rows: [Row] = []
        var current = Row()
        for index in subviews.indices {
            let size = subviews[index].sizeThatFits(.unspecified)
            let needed = current.indices.isEmpty ? size.width : current.width + spacing + size.width
            if needed > maxWidth, !current.indices.isEmpty {
                rows.append(current)
                current = Row()
            }
            current.width = current.indices.isEmpty ? size.width : current.width + spacing + size.width
            current.height = max(current.height, size.height)
            current.indices.append(index)
        }
        if !current.indices.isEmpty { rows.append(current) }
        return rows
    }
}
