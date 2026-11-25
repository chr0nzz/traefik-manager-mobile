package dev.chr0nzz.traefikmanager.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SubdirectoryArrowLeft
import androidx.compose.material.icons.outlined.SubdirectoryArrowRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import dev.chr0nzz.traefikmanager.data.model.UiText

@Composable
@ReadOnlyComposable
fun targetArrowIcon(): ImageVector =
    if (LocalLayoutDirection.current == LayoutDirection.Rtl) Icons.Outlined.SubdirectoryArrowLeft
    else Icons.Outlined.SubdirectoryArrowRight

@Composable
@ReadOnlyComposable
fun rtlIsolated(text: String): String =
    if (LocalLayoutDirection.current != LayoutDirection.Rtl) text
    else text.split(UiText.SEPARATOR).joinToString(RTL_SEPARATOR) { "\u2067$it\u2069" }

private const val RTL_SEPARATOR = " \u2022 "
