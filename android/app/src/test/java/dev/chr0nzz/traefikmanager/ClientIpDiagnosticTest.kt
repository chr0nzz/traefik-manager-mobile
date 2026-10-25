package dev.chr0nzz.traefikmanager

import dev.chr0nzz.traefikmanager.data.model.ClientIpDiagnostic
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClientIpDiagnosticTest {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Test
    fun `the trusted proxy verdict and list are read`() {
        val diagnostic = json.decodeFromString<ClientIpDiagnostic>(
            """{"effective_ip":"203.0.113.9","socket_peer":"172.18.0.2","proxy_hops":1,
               "proxy_trusted":false,"trusted_proxies":["10.0.0.0/8","172.16.0.0/12"]}""",
        )
        assertEquals(false, diagnostic.proxyTrusted)
        assertEquals(listOf("10.0.0.0/8", "172.16.0.0/12"), diagnostic.trustedProxies)
    }

    @Test
    fun `an older server leaves the verdict unknown`() {
        val diagnostic = json.decodeFromString<ClientIpDiagnostic>("""{"effective_ip":"203.0.113.9","proxy_hops":1}""")
        assertNull(diagnostic.proxyTrusted)
        assertEquals(emptyList<String>(), diagnostic.trustedProxies)
    }
}
