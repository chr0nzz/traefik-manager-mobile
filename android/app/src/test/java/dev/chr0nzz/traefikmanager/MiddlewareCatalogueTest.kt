package dev.chr0nzz.traefikmanager

import dev.chr0nzz.traefikmanager.data.model.MiddlewareTemplates
import org.junit.Assert.assertEquals
import org.junit.Test

class MiddlewareCatalogueTest {

    private val web = listOf(
        "basicAuth", "digestAuth", "forwardAuth", "forwardAuthAuthentik", "forwardAuthAuthelia",
        "forwardAuthGatekeeper", "oidcAuth", "ipAllowList", "ipAllowListPrivate", "rateLimit",
        "secureHeaders", "corsHeaders", "encodedCharacters", "redirectScheme", "redirectRegex",
        "stripPrefix", "addPrefix", "replacePath", "compress", "retry", "circuitBreaker",
        "buffering", "chain", "inFlightReq", "stripPrefixRegex", "replacePathRegex", "errors",
        "contentType", "grpcWeb", "passTLSClientCert",
    )

    @Test
    fun `the app offers every wizard the web offers`() {
        val app = MiddlewareTemplates.all.map { it.id }
        assertEquals(
            "wizards on the web that this app does not have. Add them to MiddlewareTemplates, " +
                "then add the id here",
            emptyList<String>(),
            web - app.toSet(),
        )
        assertEquals(
            "wizards in this app that the web does not have. Update the list here if the web " +
                "gained them, otherwise they will confuse anyone comparing the two",
            emptyList<String>(),
            app - web.toSet(),
        )
    }

    @Test
    fun `no wizard id is declared twice`() {
        val ids = MiddlewareTemplates.all.map { it.id }
        assertEquals(ids.distinct(), ids)
    }
}
