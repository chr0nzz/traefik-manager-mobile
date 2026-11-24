package dev.chr0nzz.traefikmanager

import androidx.compose.ui.unit.dp
import dev.chr0nzz.traefikmanager.ui.components.cardColumnCount
import org.junit.Assert.assertEquals
import org.junit.Test

class CardColumnsTest {
    @Test
    fun phonesKeepOneColumn() {
        assertEquals(1, cardColumnCount(360.dp))
        assertEquals(1, cardColumnCount(411.dp))
    }

    @Test
    fun unfoldedAndTabletWidthsGetMoreColumns() {
        assertEquals(2, cardColumnCount(620.dp))
        assertEquals(3, cardColumnCount(900.dp))
        assertEquals(4, cardColumnCount(1200.dp))
    }
}
