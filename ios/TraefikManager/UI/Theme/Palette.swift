import SwiftUI
import UIKit

/// Brand palette shared with the Android app (`Palette.kt`), resolving light/dark automatically.
enum Palette {
    static let bg = dynamic(light: 0xF6F8FA, dark: 0x0D1117)
    static let card = dynamic(light: 0xFFFFFF, dark: 0x161B22)
    static let border = dynamic(light: 0xD0D7DE, dark: 0x30363D)
    static let text = dynamic(light: 0x1F2328, dark: 0xE6EDF3)
    static let muted = dynamic(light: 0x636E7B, dark: 0x7D8590)
    static let blue = dynamic(light: 0x0969DA, dark: 0x24A1DE)
    static let green = dynamic(light: 0x1A7F37, dark: 0x22C55E)
    static let yellow = dynamic(light: 0x9A6700, dark: 0xF59E0B)
    static let red = dynamic(light: 0xCF222E, dark: 0xEF4444)
    static let orange = dynamic(light: 0xBC4C00, dark: 0xF0883E)
    static let purple = dynamic(light: 0x8250DF, dark: 0xA371F7)
    static let teal = dynamic(light: 0x0E7069, dark: 0x1ABC9C)
    static let secondaryContainer = dynamic(light: 0xCCE5F6, dark: 0x1C3A50)
    static let onSecondaryContainer = dynamic(light: 0x003A57, dark: 0x9ECFEF)

    private static func dynamic(light: UInt32, dark: UInt32) -> Color {
        Color(UIColor { traits in
            UIColor(hex: traits.userInterfaceStyle == .dark ? dark : light)
        })
    }
}

enum Spacing {
    static let xs: CGFloat = 4
    static let sm: CGFloat = 8
    static let md: CGFloat = 12
    static let lg: CGFloat = 16
    static let xl: CGFloat = 24
    static let xxl: CGFloat = 32
}

enum Radius {
    static let sm: CGFloat = 8
    static let md: CGFloat = 10
    static let lg: CGFloat = 16
}

private extension UIColor {
    convenience init(hex: UInt32) {
        self.init(
            red: CGFloat((hex >> 16) & 0xFF) / 255,
            green: CGFloat((hex >> 8) & 0xFF) / 255,
            blue: CGFloat(hex & 0xFF) / 255,
            alpha: 1
        )
    }
}
