import SwiftUI

/// A sheet presented above the tab bar / sidebar shells instead of from the screen that asked for it.
///
/// Folding or unfolding iPhone Duo, or resizing a window across the compact/regular boundary,
/// swaps one shell for the other and rebuilds every screen beneath it. A sheet presented from
/// a screen goes with it, along with whatever the user had typed. Editors and one-time results
/// (a new API key) use `shellSheet` instead, which hands the sheet to `shellSheetHost` on
/// `RootView`. That view stays put, so the sheet and its contents survive the switch.
struct ShellSheet: Identifiable {
    let id = UUID()
    let content: () -> AnyView
    /// Tells the screen that asked for the sheet that it closed.
    var onDismiss: () -> Void = {}
}

extension View {
    /// Like `sheet(item:)`, but survives the app switching between tab bar and sidebar.
    func shellSheet<Item: Identifiable, Sheet: View>(item: Binding<Item?>, @ViewBuilder content: @escaping (Item) -> Sheet) -> some View {
        modifier(ShellSheetModifier(item: item, sheet: content))
    }

    /// Like `sheet(isPresented:)`, but survives the app switching between tab bar and sidebar.
    func shellSheet<Sheet: View>(isPresented: Binding<Bool>, @ViewBuilder content: @escaping () -> Sheet) -> some View {
        shellSheet(item: Binding(
            get: { isPresented.wrappedValue ? PresentedFlag() : nil },
            set: { isPresented.wrappedValue = $0 != nil }
        )) { _ in content() }
    }
}

private struct PresentedFlag: Identifiable {
    var id: Int { 0 }
}

/// Mirrors the screen's own binding into `AppModel.sheet`.
private struct ShellSheetModifier<Item: Identifiable, Sheet: View>: ViewModifier {
    @Environment(AppModel.self) private var app
    @Binding var item: Item?
    let sheet: (Item) -> Sheet
    @State private var shown: UUID?

    func body(content: Content) -> some View {
        content
            // Covers bindings that start out set, like debug launch forms.
            .onAppear(perform: sync)
            .onChange(of: item?.id) { sync() }
    }

    private func sync() {
        guard let item else {
            // The screen cleared its binding to close the sheet.
            if let shown, app.sheet?.id == shown { app.sheet = nil }
            shown = nil
            return
        }
        let binding = $item
        let next = ShellSheet(content: { [sheet] in AnyView(sheet(item)) }, onDismiss: { binding.wrappedValue = nil })
        shown = next.id
        app.sheet = next
    }
}

extension View {
    /// Shows `AppModel.sheet`. Attach once, above the view that switches between tab bar and sidebar.
    func shellSheetHost() -> some View {
        modifier(ShellSheetHost())
    }
}

private struct ShellSheetHost: ViewModifier {
    @Environment(AppModel.self) private var app

    func body(content: Content) -> some View {
        content.sheet(item: Binding(
            get: { app.sheet },
            set: { next in
                // Tell the requesting screen its sheet went away, so its own binding resets.
                if let current = app.sheet, current.id != next?.id { current.onDismiss() }
                app.sheet = next
            }
        )) { $0.content() }
    }
}
