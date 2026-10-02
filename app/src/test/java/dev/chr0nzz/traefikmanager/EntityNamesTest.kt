package dev.chr0nzz.traefikmanager

import org.robolectric.RobolectricTestRunner
import org.junit.runner.RunWith
import dev.chr0nzz.traefikmanager.data.model.EntityNames
import dev.chr0nzz.traefikmanager.data.model.MiddlewareForm
import dev.chr0nzz.traefikmanager.data.model.RouteForm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@RunWith(RobolectricTestRunner::class)
class EntityNamesTest {

    @Test
    fun `names the server accepts pass`() {
        assertNull(EntityNames.problem("my-app").english())
        assertNull(EntityNames.problem("my app.v2_beta").english())
        assertNull(EntityNames.problem("  padded  ").english())
        assertNull(EntityNames.problem("a".repeat(100)).english())
    }

    @Test
    fun `the server rule is mirrored message for message`() {
        assertEquals("Give it a name", EntityNames.problem("   ").english())
        assertEquals("That name is reserved", EntityNames.problem(".").english())
        assertEquals("That name is reserved", EntityNames.problem(" .. ").english())
        assertEquals("Keep the name to 100 characters or fewer", EntityNames.problem("a".repeat(101)).english())
        listOf("a@file", "a/b", "a,b", "a:b", "a{b", "a}b", "a\tb", "ab").forEach {
            assertEquals("A name cannot contain @ / , : { or }", EntityNames.problem(it).english())
        }
    }

    @Test
    fun `route and middleware forms refuse the name before saving`() {
        assertEquals("A name cannot contain @ / , : { or }", RouteForm(name = "app@file").validationError.english())
        assertEquals(
            "A name cannot contain @ / , : { or }",
            MiddlewareForm(name = "auth:basic", yaml = "basicAuth: {}").validationError.english(),
        )
        assertNull(MiddlewareForm(name = "auth-basic", yaml = "basicAuth: {}").validationError.english())
    }
}
