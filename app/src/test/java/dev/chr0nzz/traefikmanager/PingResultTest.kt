package dev.chr0nzz.traefikmanager

import dev.chr0nzz.traefikmanager.data.model.PingResult
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PingResultTest {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Test
    fun `a ping with a dead backend member reads as degraded`() {
        val ping = json.decodeFromString<PingResult>(
            """{"ok":true,"latency_ms":12,"status_code":200,"state":"degraded","source":"servers",
               "servers":{"up":1,"total":2},"down_servers":["http://10.0.0.6:80"]}""",
        )
        assertTrue(ping.ok)
        assertTrue(ping.degraded)
        assertEquals(1, ping.servers?.up)
        assertEquals(listOf("http://10.0.0.6:80"), ping.downServers)
    }

    @Test
    fun `an older server without state is never degraded`() {
        val ping = json.decodeFromString<PingResult>("""{"ok":true,"latency_ms":8,"status_code":200}""")
        assertFalse(ping.degraded)
        assertEquals(null, ping.servers)
    }
}
