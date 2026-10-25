import SwiftUI
import UIKit

/// Monospaced YAML editor with live syntax colouring. Port of `YamlEditor` / `highlightYaml`.
struct YamlEditor: UIViewRepresentable {
    @Binding var text: String
    var editable = true

    func makeUIView(context: Context) -> UITextView {
        let view = UITextView()
        view.delegate = context.coordinator
        view.backgroundColor = .clear
        view.autocapitalizationType = .none
        view.autocorrectionType = .no
        view.spellCheckingType = .no
        view.smartQuotesType = .no
        view.smartDashesType = .no
        view.smartInsertDeleteType = .no
        view.keyboardType = .asciiCapable
        view.alwaysBounceVertical = true
        view.textContainerInset = UIEdgeInsets(top: 12, left: 8, bottom: 12, right: 8)
        view.isEditable = editable
        view.adjustsFontForContentSizeCategory = true
        view.attributedText = YamlHighlighter.highlight(text)
        return view
    }

    func updateUIView(_ view: UITextView, context: Context) {
        view.isEditable = editable
        if view.text != text {
            let selection = view.selectedRange
            view.attributedText = YamlHighlighter.highlight(text)
            view.selectedRange = NSRange(location: min(selection.location, (text as NSString).length), length: 0)
        }
    }

    func makeCoordinator() -> Coordinator { Coordinator(text: $text) }

    final class Coordinator: NSObject, UITextViewDelegate {
        var text: Binding<String>

        init(text: Binding<String>) { self.text = text }

        func textViewDidChange(_ view: UITextView) {
            let selection = view.selectedRange
            view.attributedText = YamlHighlighter.highlight(view.text)
            view.selectedRange = selection
            text.wrappedValue = view.text
        }

        /// Keep the indentation of the current line on return, which YAML editing relies on.
        func textView(_ view: UITextView, shouldChangeTextIn range: NSRange, replacementText replacement: String) -> Bool {
            guard replacement == "\n" else { return true }
            let source = view.text as NSString
            let lineStart = source.lineRange(for: NSRange(location: range.location, length: 0)).location
            let line = source.substring(with: NSRange(location: lineStart, length: range.location - lineStart))
            var indent = String(line.prefix { $0 == " " })
            if line.trimmingCharacters(in: .whitespaces).hasSuffix(":") { indent += "  " }
            view.replace(view.textRange(from: range) ?? UITextRange(), withText: "\n" + indent)
            return false
        }
    }
}

private extension UITextView {
    func textRange(from range: NSRange) -> UITextRange? {
        guard let start = position(from: beginningOfDocument, offset: range.location),
              let end = position(from: start, offset: range.length) else { return nil }
        return textRange(from: start, to: end)
    }
}

enum YamlHighlighter {
    private static let key = try! NSRegularExpression(pattern: #"^(\s*)(-\s+)?([A-Za-z0-9_.\-\[\]]+)(\s*:)"#)
    private static let listItem = try! NSRegularExpression(pattern: #"^(\s*)(-)(\s|$)"#)
    private static let number = try! NSRegularExpression(pattern: #"^-?\d+(\.\d+)?$"#)
    private static let booleans: Set<String> = ["true", "false", "yes", "no", "null", "~", "on", "off"]

    static func highlight(_ source: String) -> NSAttributedString {
        let font = UIFontMetrics(forTextStyle: .body).scaledFont(for: .monospacedSystemFont(ofSize: 14, weight: .regular))
        let result = NSMutableAttributedString(
            string: source,
            attributes: [.font: font, .foregroundColor: UIColor(Palette.text)]
        )
        var lineStart = 0
        for line in source.components(separatedBy: "\n") {
            let ns = line as NSString
            let commentAt = commentIndex(line)
            let codeEnd = commentAt ?? ns.length
            let code = ns.substring(to: codeEnd)
            let codeRange = NSRange(location: 0, length: (code as NSString).length)

            if let commentAt {
                color(result, lineStart + commentAt, ns.length - commentAt, Palette.muted)
            }
            if let match = listItem.firstMatch(in: code, range: codeRange) {
                let dash = match.range(at: 2)
                color(result, lineStart + dash.location, dash.length, Palette.muted)
            }
            if let match = key.firstMatch(in: code, range: codeRange) {
                let keyRange = match.range(at: 3)
                let colon = match.range(at: 4)
                color(result, lineStart + keyRange.location, keyRange.length, Palette.blue)
                color(result, lineStart + colon.location, colon.length, Palette.muted)
                styleValue(result, code, from: NSMaxRange(match.range), to: codeEnd, lineStart: lineStart)
            } else {
                let dashEnd = listItem.firstMatch(in: code, range: codeRange).map { NSMaxRange($0.range) } ?? 0
                styleValue(result, code, from: dashEnd, to: codeEnd, lineStart: lineStart)
            }
            lineStart += ns.length + 1
        }
        return result
    }

    private static func styleValue(_ result: NSMutableAttributedString, _ line: String, from: Int, to: Int, lineStart: Int) {
        let ns = line as NSString
        guard from < to, to <= ns.length else { return }
        let raw = ns.substring(with: NSRange(location: from, length: to - from))
        let trimmed = raw.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty else { return }
        let offset = (raw as NSString).range(of: trimmed).location
        let color: Color
        if trimmed.hasPrefix("\"") || trimmed.hasPrefix("'") || trimmed.hasPrefix("`") {
            color = Palette.green
        } else if booleans.contains(trimmed.lowercased()) {
            color = Palette.purple
        } else if number.firstMatch(in: trimmed, range: NSRange(location: 0, length: (trimmed as NSString).length)) != nil {
            color = Palette.orange
        } else {
            color = Palette.green
        }
        Self.color(result, lineStart + from + offset, (trimmed as NSString).length, color)
    }

    private static func color(_ result: NSMutableAttributedString, _ location: Int, _ length: Int, _ color: Color) {
        guard length > 0, location + length <= result.length else { return }
        result.addAttribute(.foregroundColor, value: UIColor(color), range: NSRange(location: location, length: length))
    }

    /// Index of a `#` that starts a comment (not inside quotes), in UTF-16 units.
    private static func commentIndex(_ line: String) -> Int? {
        var singles = 0
        var doubles = 0
        for (index, unit) in line.utf16.enumerated() {
            switch unit {
            case 39: singles += 1
            case 34: doubles += 1
            case 35 where singles % 2 == 0 && doubles % 2 == 0: return index
            default: break
            }
        }
        return nil
    }
}
