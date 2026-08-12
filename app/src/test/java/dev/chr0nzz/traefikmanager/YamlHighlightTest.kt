package dev.chr0nzz.traefikmanager

import androidx.compose.ui.graphics.Color
import dev.chr0nzz.traefikmanager.ui.components.YamlColors
import dev.chr0nzz.traefikmanager.ui.components.highlightYaml
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YamlHighlightTest {

    private val colors = YamlColors(
        key = Color.Blue,
        string = Color.Green,
        number = Color.Yellow,
        boolean = Color.Magenta,
        comment = Color.Gray,
        punctuation = Color.DarkGray,
        text = Color.White,
    )

    private fun colorAt(source: String, index: Int): Color? =
        highlightYaml(source, colors).spanStyles
            .firstOrNull { index >= it.start && index < it.end }
            ?.item
            ?.color

    @Test
    fun `the highlighted text is identical to the source`() {
        val source = "http:\n  routers:\n    app:\n      rule: Host(`a.example.com`)\n"
        assertEquals(source, highlightYaml(source, colors).text)
    }

    @Test
    fun `keys are coloured as keys`() {
        val source = "rule: Host(`a.example.com`)"
        assertEquals(colors.key, colorAt(source, 0))
    }

    @Test
    fun `booleans and numbers are told apart from strings`() {
        assertEquals(colors.boolean, colorAt("passHostHeader: true", 16))
        assertEquals(colors.number, colorAt("priority: 23", 10))
        assertEquals(colors.string, colorAt("service: my-app", 9))
    }

    @Test
    fun `comments are coloured to the end of the line`() {
        val source = "rule: Host(`a`)  # keep this"
        assertEquals(colors.comment, colorAt(source, source.indexOf('#')))
    }

    @Test
    fun `a hash inside a quoted value is not a comment`() {
        val source = """password: "a#b""""
        assertTrue(colorAt(source, source.indexOf('#')) != colors.comment)
    }

    @Test
    fun `list markers are punctuation and the item still highlights`() {
        val source = "  - url: http://10.0.0.5:8080"
        assertEquals(colors.punctuation, colorAt(source, 2))
        assertEquals(colors.key, colorAt(source, 4))
    }

    @Test
    fun `an empty document does not throw`() {
        assertEquals("", highlightYaml("", colors).text)
    }
}
