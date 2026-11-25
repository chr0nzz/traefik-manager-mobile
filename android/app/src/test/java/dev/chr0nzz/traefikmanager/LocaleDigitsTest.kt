package dev.chr0nzz.traefikmanager

import dev.chr0nzz.traefikmanager.data.model.LogParser
import dev.chr0nzz.traefikmanager.data.model.fmt
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Locale

class LocaleDigitsTest {

    private lateinit var saved: Locale

    @Before
    fun keep() {
        saved = Locale.getDefault(Locale.Category.FORMAT)
    }

    @After
    fun restore() {
        Locale.setDefault(Locale.Category.FORMAT, saved)
    }

    private fun persian() = Locale.setDefault(Locale.Category.FORMAT, Locale.forLanguageTag("fa"))

    @Test
    fun `counts use the digits of the language`() {
        persian()
        assertEquals("۸", 8.fmt())
        assertEquals("۱٬۲۳۴", 1234.fmt())
        Locale.setDefault(Locale.Category.FORMAT, Locale.US)
        assertEquals("1,234", 1234.fmt())
    }

    @Test
    fun `durations keep their units and use the digits of the language`() {
        persian()
        assertEquals("۱۲۰ms", LogParser.formatMs(120.0))
        assertEquals("۰٫۵ms", LogParser.formatMs(0.5))
        assertEquals("۱۲s", LogParser.formatMs(12_000.0))
        assertEquals("۲ms", LogParser.formatDuration(1_500_000))
        assertEquals("۵۰۰ns", LogParser.formatDuration(500))
        assertEquals("۱۲۰" to "ms", LogParser.heroMs(120.0))
    }

    @Test
    fun `english output is unchanged`() {
        Locale.setDefault(Locale.Category.FORMAT, Locale.US)
        assertEquals("120ms", LogParser.formatMs(120.0))
        assertEquals("0.5ms", LogParser.formatMs(0.5))
        assertEquals("12s", LogParser.formatMs(12_000.0))
        assertEquals("2ms", LogParser.formatDuration(1_500_000))
    }
}
