package dev.chr0nzz.traefikmanager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chr0nzz.traefikmanager.ui.theme.LocalTmPalette
import dev.chr0nzz.traefikmanager.ui.theme.MonoFamily
import dev.chr0nzz.traefikmanager.ui.theme.TmSpacing

data class YamlColors(
    val key: Color,
    val string: Color,
    val number: Color,
    val boolean: Color,
    val comment: Color,
    val punctuation: Color,
    val text: Color,
)

@Composable
fun rememberYamlColors(): YamlColors {
    val palette = LocalTmPalette.current
    return remember(palette) {
        YamlColors(
            key = palette.blue,
            string = palette.green,
            number = palette.orange,
            boolean = palette.purple,
            comment = palette.muted,
            punctuation = palette.muted,
            text = palette.text,
        )
    }
}

private val KEY = Regex("""^(\s*)(-\s+)?([A-Za-z0-9_.\-\[\]]+)(\s*:)""")
private val LIST_ITEM = Regex("""^(\s*)(-)(\s|$)""")
private val NUMBER = Regex("""^-?\d+(\.\d+)?$""")
private val BOOLEAN = setOf("true", "false", "yes", "no", "null", "~", "on", "off")

fun highlightYaml(source: String, colors: YamlColors): AnnotatedString = buildAnnotatedYaml(source, colors)

private fun buildAnnotatedYaml(source: String, colors: YamlColors): AnnotatedString {
    val builder = androidx.compose.ui.text.AnnotatedString.Builder()
    var offset = 0
    source.split('\n').forEachIndexed { index, line ->
        if (index > 0) {
            builder.append("\n")
            offset += 1
        }
        builder.append(line)
        val start = offset
        offset += line.length

        val commentAt = line.indexOf('#').takeIf { it >= 0 && !inQuotes(line, it) }
        val codeEnd = commentAt ?: line.length
        val code = line.substring(0, codeEnd)

        if (commentAt != null) {
            builder.addStyle(SpanStyle(color = colors.comment), start + commentAt, start + line.length)
        }

        LIST_ITEM.find(code)?.let { match ->
            val dash = match.groups[2] ?: return@let
            builder.addStyle(SpanStyle(color = colors.punctuation), start + dash.range.first, start + dash.range.last + 1)
        }

        val keyMatch = KEY.find(code)
        if (keyMatch != null) {
            val key = keyMatch.groups[3]
            val colon = keyMatch.groups[4]
            if (key != null) {
                builder.addStyle(SpanStyle(color = colors.key), start + key.range.first, start + key.range.last + 1)
            }
            if (colon != null) {
                builder.addStyle(
                    SpanStyle(color = colors.punctuation),
                    start + colon.range.first,
                    start + colon.range.last + 1,
                )
            }
            val valueStart = keyMatch.range.last + 1
            styleValue(builder, code, valueStart, codeEnd, start, colors)
        } else {
            val dashEnd = LIST_ITEM.find(code)?.range?.last?.plus(1) ?: 0
            styleValue(builder, code, dashEnd, codeEnd, start, colors)
        }
    }
    return builder.toAnnotatedString()
}

private fun styleValue(
    builder: androidx.compose.ui.text.AnnotatedString.Builder,
    line: String,
    from: Int,
    to: Int,
    lineStart: Int,
    colors: YamlColors,
) {
    if (from >= to) return
    val raw = line.substring(from, to)
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return
    val valueStart = from + raw.indexOf(trimmed)
    val valueEnd = valueStart + trimmed.length
    val color = when {
        trimmed.startsWith("\"") || trimmed.startsWith("'") || trimmed.startsWith("`") -> colors.string
        trimmed.lowercase() in BOOLEAN -> colors.boolean
        NUMBER.matches(trimmed) -> colors.number
        else -> colors.string
    }
    builder.addStyle(SpanStyle(color = color), lineStart + valueStart, lineStart + valueEnd)
}

private fun inQuotes(line: String, index: Int): Boolean {
    var singles = 0
    var doubles = 0
    for (position in 0 until index) {
        when (line[position]) {
            '\'' -> singles++
            '"' -> doubles++
        }
    }
    return singles % 2 == 1 || doubles % 2 == 1
}

@Composable
fun YamlEditor(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
) {
    val palette = LocalTmPalette.current
    val colors = rememberYamlColors()
    val verticalScroll = rememberScrollState()
    val horizontalScroll = rememberScrollState()
    val lineCount = remember(state.text) { state.text.count { it == '\n' } + 1 }

    val style = TextStyle(
        fontFamily = MonoFamily,
        fontSize = 13.sp,
        lineHeight = 20.sp,
        color = palette.text,
    )

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(palette.bg)
            .verticalScroll(verticalScroll),
    ) {
        Column(
            modifier = Modifier
                .width(38.dp)
                .background(palette.card)
                .padding(vertical = TmSpacing.sm),
        ) {
            repeat(lineCount) { line ->
                Text(
                    text = (line + 1).toString(),
                    style = style.copy(color = palette.muted, fontSize = 12.sp),
                    textAlign = TextAlign.End,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = TmSpacing.sm),
                )
            }
        }

        BasicTextField(
            state = state,
            readOnly = readOnly,
            textStyle = style,
            cursorBrush = SolidColor(palette.blue),
            outputTransformation = OutputTransformation {
                val highlighted = highlightYaml(toString(), colors)
                highlighted.spanStyles.forEach { range ->
                    addStyle(range.item, range.start, range.end)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(horizontalScroll)
                .padding(start = TmSpacing.sm, end = TmSpacing.md, top = TmSpacing.sm, bottom = TmSpacing.xxl),
        )
    }
}

@Composable
fun YamlPreview(source: String, modifier: Modifier = Modifier) {
    val colors = rememberYamlColors()
    val palette = LocalTmPalette.current
    Text(
        text = highlightYaml(source, colors),
        style = LocalTextStyle.current.copy(
            fontFamily = MonoFamily,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            color = palette.text,
        ),
        modifier = modifier.horizontalScroll(rememberScrollState()),
    )
}
