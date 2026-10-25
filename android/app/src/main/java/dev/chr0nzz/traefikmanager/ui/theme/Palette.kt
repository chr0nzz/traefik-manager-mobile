package dev.chr0nzz.traefikmanager.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class TmPalette(
    val bg: Color,
    val card: Color,
    val border: Color,
    val text: Color,
    val muted: Color,
    val blue: Color,
    val green: Color,
    val yellow: Color,
    val red: Color,
    val orange: Color,
    val purple: Color,
    val teal: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
)

val TmDarkPalette = TmPalette(
    bg = Color(0xFF0D1117),
    card = Color(0xFF161B22),
    border = Color(0xFF30363D),
    text = Color(0xFFE6EDF3),
    muted = Color(0xFF7D8590),
    blue = Color(0xFF24A1DE),
    green = Color(0xFF22C55E),
    yellow = Color(0xFFF59E0B),
    red = Color(0xFFEF4444),
    orange = Color(0xFFF0883E),
    purple = Color(0xFFA371F7),
    teal = Color(0xFF1ABC9C),
    secondaryContainer = Color(0xFF1C3A50),
    onSecondaryContainer = Color(0xFF9ECFEF),
)

val TmLightPalette = TmPalette(
    bg = Color(0xFFF6F8FA),
    card = Color(0xFFFFFFFF),
    border = Color(0xFFD0D7DE),
    text = Color(0xFF1F2328),
    muted = Color(0xFF636E7B),
    blue = Color(0xFF0969DA),
    green = Color(0xFF1A7F37),
    yellow = Color(0xFF9A6700),
    red = Color(0xFFCF222E),
    orange = Color(0xFFBC4C00),
    purple = Color(0xFF8250DF),
    teal = Color(0xFF0E7069),
    secondaryContainer = Color(0xFFCCE5F6),
    onSecondaryContainer = Color(0xFF003A57),
)

val LocalTmPalette = staticCompositionLocalOf { TmDarkPalette }
