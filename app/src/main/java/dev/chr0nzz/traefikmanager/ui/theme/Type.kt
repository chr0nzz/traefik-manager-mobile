package dev.chr0nzz.traefikmanager.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import dev.chr0nzz.traefikmanager.R

val InterFamily = FontFamily(
    Font(R.font.inter_variable, FontWeight.Normal),
    Font(R.font.inter_variable, FontWeight.Medium),
    Font(R.font.inter_variable, FontWeight.SemiBold),
    Font(R.font.inter_variable, FontWeight.Bold),
)

val MonoFamily = FontFamily(
    Font(R.font.jetbrains_mono_variable, FontWeight.Normal),
    Font(R.font.jetbrains_mono_variable, FontWeight.Medium),
    Font(R.font.jetbrains_mono_variable, FontWeight.SemiBold),
)

private val base = Typography()

val TmTypography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = InterFamily),
    displayMedium = base.displayMedium.copy(fontFamily = InterFamily),
    displaySmall = base.displaySmall.copy(fontFamily = InterFamily),
    headlineLarge = base.headlineLarge.copy(fontFamily = InterFamily),
    headlineMedium = base.headlineMedium.copy(fontFamily = InterFamily),
    headlineSmall = base.headlineSmall.copy(fontFamily = InterFamily),
    titleLarge = base.titleLarge.copy(fontFamily = InterFamily),
    titleMedium = base.titleMedium.copy(fontFamily = InterFamily),
    titleSmall = base.titleSmall.copy(fontFamily = InterFamily),
    bodyLarge = base.bodyLarge.copy(fontFamily = InterFamily),
    bodyMedium = base.bodyMedium.copy(fontFamily = InterFamily),
    bodySmall = base.bodySmall.copy(fontFamily = InterFamily),
    labelLarge = base.labelLarge.copy(fontFamily = InterFamily),
    labelMedium = base.labelMedium.copy(fontFamily = InterFamily),
    labelSmall = base.labelSmall.copy(fontFamily = InterFamily),
)
