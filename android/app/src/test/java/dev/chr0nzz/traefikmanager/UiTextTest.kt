package dev.chr0nzz.traefikmanager

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.chr0nzz.traefikmanager.data.model.UiText
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UiTextTest {

    private val resources = ApplicationProvider.getApplicationContext<Context>().resources

    @Test
    fun `raw text is returned as it is`() {
        assertEquals("upstream said no", UiText.Raw("upstream said no").resolve(resources))
    }

    @Test
    fun `a resource is read with its arguments`() {
        assertEquals("Save", UiText.of(R.string.common_save).resolve(resources))
        assertEquals("Server error 502", UiText.of(R.string.connect_error_server, 502).resolve(resources))
    }

    @Test
    fun `a nested text is resolved before it is placed`() {
        val text = UiText.of(R.string.nav_editor_move_up, UiText.of(R.string.nav_routes))
        assertEquals("Move Routes up", text.resolve(resources))
    }

    @Test
    fun `a plural picks its form from the count`() {
        assertEquals("1 alert", UiText.plural(R.plurals.nav_badge_alerts, 1).resolve(resources))
        assertEquals("4 alerts", UiText.plural(R.plurals.nav_badge_alerts, 4).resolve(resources))
    }

    @Test
    fun `two texts with the same resource and arguments are equal`() {
        assertEquals(UiText.of(R.string.connect_error_server, 500), UiText.of(R.string.connect_error_server, 500))
    }
}
