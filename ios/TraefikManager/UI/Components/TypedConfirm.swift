import SwiftUI

/// Destructive confirmation that requires typing a word, like Android's `TypedConfirmDialog`.
struct TypedConfirmModifier: ViewModifier {
    @Binding var isPresented: Bool
    let title: String
    let message: String
    let actionLabel: String
    var word = String(localized: "Delete").uppercased()
    let onConfirm: () -> Void

    @State private var typed = ""

    func body(content: Content) -> some View {
        content.alert(title, isPresented: $isPresented) {
            TextField(word, text: $typed)
                .textInputAutocapitalization(.characters)
                .autocorrectionDisabled()
            Button(actionLabel, role: .destructive) {
                if typed.trimmed.uppercased() == word { onConfirm() }
                typed = ""
            }
            Button("Cancel", role: .cancel) { typed = "" }
        } message: {
            Text(message + "\n\n" + String(localized: "Type \(word) to confirm."))
        }
    }
}

extension View {
    func typedConfirm(
        isPresented: Binding<Bool>, title: String, message: String, actionLabel: String, onConfirm: @escaping () -> Void
    ) -> some View {
        modifier(TypedConfirmModifier(isPresented: isPresented, title: title, message: message, actionLabel: actionLabel, onConfirm: onConfirm))
    }
}
