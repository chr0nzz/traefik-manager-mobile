package dev.chr0nzz.traefikmanager.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TmTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val palette = if (darkTheme) TmDarkPalette else TmLightPalette
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> darkColorScheme(
            primary = palette.blue,
            onPrimary = Color(0xFF04141D),
            secondary = palette.purple,
            secondaryContainer = palette.secondaryContainer,
            onSecondaryContainer = palette.onSecondaryContainer,
            tertiary = palette.teal,
            background = palette.bg,
            onBackground = palette.text,
            surface = palette.bg,
            onSurface = palette.text,
            surfaceVariant = palette.card,
            onSurfaceVariant = palette.muted,
            surfaceContainer = palette.card,
            surfaceContainerLow = palette.card,
            surfaceContainerHigh = palette.card,
            outline = palette.border,
            outlineVariant = palette.border,
            error = palette.red,
        )
        else -> lightColorScheme(
            primary = palette.blue,
            onPrimary = Color(0xFFFFFFFF),
            secondary = palette.purple,
            secondaryContainer = palette.secondaryContainer,
            onSecondaryContainer = palette.onSecondaryContainer,
            tertiary = palette.teal,
            background = palette.bg,
            onBackground = palette.text,
            surface = palette.bg,
            onSurface = palette.text,
            surfaceVariant = palette.card,
            onSurfaceVariant = palette.muted,
            surfaceContainer = palette.card,
            surfaceContainerLow = palette.card,
            surfaceContainerHigh = palette.card,
            outline = palette.border,
            outlineVariant = palette.border,
            error = palette.red,
        )
    }
    CompositionLocalProvider(LocalTmPalette provides palette) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            typography = TmTypography,
            shapes = TmShapes,
            content = content,
        )
    }
}
