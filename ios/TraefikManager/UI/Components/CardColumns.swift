import SwiftUI

/// Narrowest a list card gets before the screen drops a column. The unfolded iPhone Duo has
/// about 590pt beside the sidebar, which fits two at roughly the width of the Dashboard's tiles.
private let minCardWidth: CGFloat = 270

/// How many card columns fit a list of this width, after the list's own side insets.
/// One on phones and folded iPhone Duo; two or more on the unfolded inner display, iPad
/// and wide iPhone Mirroring windows. Based on the width the screen actually has, not
/// on idiom or size class, so every posture and window size gets the right layout.
func cardColumnCount(for width: CGFloat) -> Int {
    let usable = width - Spacing.lg * 2
    return max(1, Int((usable + Spacing.md) / (minCardWidth + Spacing.md)))
}

extension View {
    /// Keeps `columns` in step with the width of this view as it resizes.
    func measuringCardColumns(_ columns: Binding<Int>) -> some View {
        onGeometryChange(for: Int.self) { cardColumnCount(for: $0.size.width) } action: { columns.wrappedValue = $0 }
    }
}

/// Cards laid out `columns` to a row inside an existing `List`, so search, pull to refresh and
/// header rows keep working. Cards in a row share the tallest card's height.
///
/// Rows hold several tappable cards, so cells need their own buttons with `.buttonStyle(.plain)`.
/// A `NavigationLink` in a cell fires for every card in the row when the row is tapped.
/// Per-card actions belong in a context menu rather than swipe actions, which would act on the whole row.
struct CardRows<Item, ID: Hashable, Cell: View>: View {
    let items: [Item]
    let id: KeyPath<Item, ID>
    let columns: Int
    @ViewBuilder let cell: (Item) -> Cell

    init(_ items: [Item], id: KeyPath<Item, ID>, columns: Int, @ViewBuilder cell: @escaping (Item) -> Cell) {
        self.items = items
        self.id = id
        self.columns = max(1, columns)
        self.cell = cell
    }

    var body: some View {
        ForEach(rows) { row in
            HStack(alignment: .top, spacing: Spacing.md) {
                ForEach(row.items, id: id) { item in
                    cell(item)
                        .frame(maxWidth: .infinity, alignment: .topLeading)
                        // Cards are the tap target; a List row would otherwise add a chevron per link.
                        .navigationLinkIndicatorVisibility(.hidden)
                }
                // Keep a short last row's cards the same width as the rows above.
                ForEach(row.items.count..<columns, id: \.self) { _ in
                    Color.clear.frame(maxWidth: .infinity, maxHeight: 0)
                }
            }
            .fixedSize(horizontal: false, vertical: true)
            .listRowBackground(Color.clear)
            .listRowSeparator(.hidden)
            .listRowInsets(EdgeInsets(top: Spacing.xs, leading: Spacing.lg, bottom: Spacing.xs, trailing: Spacing.lg))
        }
    }

    /// A row is keyed by its first card, so filtering doesn't rebuild unchanged rows.
    private var rows: [CardRow<Item, ID>] {
        stride(from: 0, to: items.count, by: columns).map { start in
            let slice = Array(items[start..<min(start + columns, items.count)])
            return CardRow(id: slice[0][keyPath: id], items: slice)
        }
    }
}

private struct CardRow<Item, ID: Hashable>: Identifiable {
    let id: ID
    let items: [Item]
}
