package dev.chr0nzz.traefikmanager

import dev.chr0nzz.traefikmanager.data.repo.InUse
import dev.chr0nzz.traefikmanager.data.repo.InUseException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class InUseTest {

    private fun refusal(code: Int, body: String) = HttpException(
        Response.error<Any>(code, body.toResponseBody("application/json".toMediaType())),
    )

    @Test
    fun `a service refusal carries the routers and the parents`() {
        val thrown = InUse.from(
            refusal(
                409,
                """{"ok":false,"error":"pool is still used by web; still a backend of wrap",
                   "inUseBy":["web"],"parents":["wrap"]}""",
            ),
            "Could not delete the service",
        )
        assertTrue(thrown is InUseException)
        val blocked = thrown as InUseException
        assertEquals("pool is still used by web; still a backend of wrap", blocked.message)
        assertEquals(listOf("web"), blocked.routers)
        assertEquals(listOf("wrap"), blocked.parents)
    }

    @Test
    fun `a middleware refusal uses message rather than error`() {
        val thrown = InUse.from(
            refusal(409, """{"ok":false,"message":"auth is still used by api, web","inUseBy":["api","web"]}"""),
            "Could not delete the middleware",
        )
        val blocked = thrown as InUseException
        assertEquals("auth is still used by api, web", blocked.message)
        assertEquals(listOf("api", "web"), blocked.routers)
        assertEquals(emptyList<String>(), blocked.parents)
    }

    @Test
    fun `a refusal with neither array still reports its reason`() {
        val thrown = InUse.from(
            refusal(409, """{"ok":false,"error":"pool-backend-1 is still used by other"}"""),
            "Could not delete the service",
        )
        val blocked = thrown as InUseException
        assertEquals("pool-backend-1 is still used by other", blocked.message)
        assertTrue(blocked.routers.isEmpty() && blocked.parents.isEmpty())
    }

    @Test
    fun `anything that is not a 409 is an ordinary failure`() {
        val thrown = InUse.from(
            refusal(403, """{"ok":false,"error":"That service is not managed here"}"""),
            "Could not delete the service",
        )
        assertTrue(thrown !is InUseException)
        assertEquals("That service is not managed here", thrown.message)
    }

    @Test
    fun `an unreadable body falls back to the caller's wording`() {
        val thrown = InUse.from(refusal(500, "not json at all"), "Could not delete the service")
        assertTrue(thrown !is InUseException)
        assertEquals("Could not delete the service (HTTP 500)", thrown.message)
    }
}
