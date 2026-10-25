package dev.chr0nzz.traefikmanager.ui.components

import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp

@Composable
fun TopBarTitle(text: String) {
    val size = LocalTextStyle.current.fontSize
    Text(
        text = text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        autoSize = if (size.isSp && size.value > 16f) {
            TextAutoSize.StepBased(minFontSize = 16.sp, maxFontSize = size, stepSize = 1.sp)
        } else {
            null
        },
    )
}
