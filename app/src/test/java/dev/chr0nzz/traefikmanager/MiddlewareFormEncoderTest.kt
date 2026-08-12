package dev.chr0nzz.traefikmanager

import dev.chr0nzz.traefikmanager.data.model.MiddlewareForm
import dev.chr0nzz.traefikmanager.data.model.MiddlewareFormEncoder
import dev.chr0nzz.traefikmanager.data.model.MiddlewareProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MiddlewareFormEncoderTest {

    private fun encode(form: MiddlewareForm, agentId: String? = null): Map<String, String> =
        MiddlewareFormEncoder.fields(form, agentId).toMap()

    @Test
    fun `create posts the eight fields the server reads`() {
        val fields = MiddlewareFormEncoder.fields(
            MiddlewareForm(
                name = "  my-auth  ",
                protocol = MiddlewareProtocol.Http,
                yaml = "basicAuth:\n  users:\n    - \"a:b\"\n",
                configFile = "middlewares.yml",
            ),
            agentId = null,
        )

        assertEquals(8, fields.size)
        val map = fields.toMap()
        assertEquals("my-auth", map["middlewareName"])
        assertEquals("basicAuth:\n  users:\n    - \"a:b\"", map["middlewareContent"])
        assertEquals("http", map["mwProtocol"])
        assertEquals("false", map["isMwEdit"])
        assertEquals("", map["originalMwId"])
        assertEquals("http", map["originalMwProtocol"])
        assertEquals("middlewares.yml", map["configFile"])
        assertEquals("", map["agent_id"])
    }

    @Test
    fun `editing carries the original identity so the server can move the entry`() {
        val map = encode(
            MiddlewareForm(
                name = "renamed",
                protocol = MiddlewareProtocol.Tcp,
                yaml = "ipAllowList:\n  sourceRange:\n    - \"10.0.0.0/8\"",
                configFile = "tcp.yml",
                isEdit = true,
                originalName = "old-name",
                originalProtocol = MiddlewareProtocol.Http,
            ),
        )

        assertEquals("true", map["isMwEdit"])
        assertEquals("old-name", map["originalMwId"])
        assertEquals("http", map["originalMwProtocol"])
        assertEquals("tcp", map["mwProtocol"])
        assertEquals("renamed", map["middlewareName"])
    }

    @Test
    fun `the selected agent rides along on every save`() {
        val map = encode(
            MiddlewareForm(name = "n", yaml = "compress: {}"),
            agentId = "agent-7",
        )
        assertEquals("agent-7", map["agent_id"])
    }

    @Test
    fun `a blank name or body is rejected before the request is built`() {
        assertEquals(
            "A middleware name is required.",
            MiddlewareForm(name = "   ", yaml = "compress: {}").validationError,
        )
        assertEquals(
            "Middleware content cannot be empty.",
            MiddlewareForm(name = "n", yaml = "  \n ").validationError,
        )
        assertNull(MiddlewareForm(name = "n", yaml = "compress: {}").validationError)
    }
}
