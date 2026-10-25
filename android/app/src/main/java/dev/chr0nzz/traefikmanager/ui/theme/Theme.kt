package dev.chr0nzz.traefikmanager.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.materialkolor.PaletteStyle
import com.materialkolor.ktx.harmonize
import com.materialkolor.rememberDynamicColorScheme

val TmSeedColor = TmDarkPalette.blue

private val OnBrandDark = Color(0xFF04141D)
private val OnErrorDark = Color(0xFF3B0A0A)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TmTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val palette = if (darkTheme) TmDarkPalette else TmLightPalette

    val brandScheme = rememberDynamicColorScheme(
        seedColor = TmSeedColor,
        isDark = darkTheme,
        neutral = TmDarkPalette.card,
        neutralVariant = TmDarkPalette.border,
        style = PaletteStyle.TonalSpot,
        modifyColorScheme = { scheme ->
            scheme.copy(
                primary = palette.blue,
                onPrimary = if (darkTheme) OnBrandDark else Color.White,
                error = palette.red,
                onError = if (darkTheme) OnErrorDark else Color.White,
                background = palette.bg,
                onBackground = palette.text,
                surface = palette.bg,
                onSurface = palette.text,
                outlineVariant = palette.border,
            )
        },
    )

    val useWallpaper = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colorScheme: ColorScheme = if (useWallpaper) {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        brandScheme
    }

    CompositionLocalProvider(
        LocalTmPalette provides palette.alignedWith(colorScheme, harmonize = useWallpaper),
    ) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            motionScheme = MotionScheme.expressive(),
            typography = TmTypography,
            shapes = TmShapes,
            content = content,
        )
    }
}

private fun TmPalette.alignedWith(scheme: ColorScheme, harmonize: Boolean): TmPalette = copy(
    bg = scheme.background,
    card = scheme.surfaceContainer,
    border = scheme.outlineVariant,
    text = scheme.onSurface,
    muted = scheme.onSurfaceVariant,
    blue = scheme.primary,
    red = scheme.error,
    green = if (harmonize) green.harmonize(scheme.primary) else green,
    yellow = if (harmonize) yellow.harmonize(scheme.primary) else yellow,
    orange = if (harmonize) orange.harmonize(scheme.primary) else orange,
    purple = if (harmonize) purple.harmonize(scheme.primary) else purple,
    teal = if (harmonize) teal.harmonize(scheme.primary) else teal,
    secondaryContainer = scheme.secondaryContainer,
    onSecondaryContainer = scheme.onSecondaryContainer,
)
