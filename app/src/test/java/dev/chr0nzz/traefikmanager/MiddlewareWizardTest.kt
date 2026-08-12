package dev.chr0nzz.traefikmanager

import dev.chr0nzz.traefikmanager.data.model.MiddlewareDef
import dev.chr0nzz.traefikmanager.data.model.MiddlewareTemplates
import dev.chr0nzz.traefikmanager.data.model.MiddlewareUsage
import dev.chr0nzz.traefikmanager.data.model.Route
import dev.chr0nzz.traefikmanager.data.model.WizardValues
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MiddlewareWizardTest {

    private fun build(
        id: String,
        text: Map<String, String> = emptyMap(),
        toggles: Map<String, Boolean> = emptyMap(),
    ): String {
        val wizard = MiddlewareTemplates.byId(id) ?: error("no wizard $id")
        return wizard.build(WizardValues(text, toggles, wizard.fields))
    }

    @Test
    fun `all 24 built-in templates exist and are grouped`() {
        assertEquals(24, MiddlewareTemplates.all.size)
        assertEquals(
            listOf("Auth", "Security", "Routing", "Advanced"),
            MiddlewareTemplates.categories,
        )
        MiddlewareTemplates.all.forEach { wizard ->
            assertTrue(wizard.id, wizard.category in MiddlewareTemplates.categories)
            assertTrue(wizard.id, wizard.fields.isNotEmpty())
        }
    }

    @Test
    fun `basic auth matches the web output`() {
        assertEquals(
            "basicAuth:\n  users:\n    - \"admin:\$apr1\$x\$y\"\n  realm: \"My Area\"",
            build(
                "basicAuth",
                mapOf("users" to "admin:\$apr1\$x\$y", "realm" to "My Area"),
            ),
        )
    }

    @Test
    fun `basic auth omits the realm when blank`() {
        assertEquals(
            "basicAuth:\n  users:\n    - \"a:b\"",
            build("basicAuth", mapOf("users" to "a:b")),
        )
    }

    @Test
    fun `forward auth defaults trust to true and omits an invalid max body`() {
        assertEquals(
            "forwardAuth:\n  address: \"http://auth:4181\"\n  trustForwardHeader: true",
            build("forwardAuth", mapOf("address" to "http://auth:4181")),
        )
        val withBody = build(
            "forwardAuth",
            mapOf("address" to "http://a", "maxBody" to "not-a-number"),
        )
        assertTrue(withBody, !withBody.contains("maxResponseBodySize"))
    }

    @Test
    fun `the authentik variant prefills its address and headers`() {
        val yaml = build("forwardAuthAuthentik")
        assertTrue(yaml, yaml.contains("outpost.goauthentik.io"))
        assertTrue(yaml, yaml.contains("X-authentik-username"))
    }

    @Test
    fun `gatekeeper composes the verify url and pins Authorization first`() {
        val yaml = build(
            "forwardAuthGatekeeper",
            text = mapOf("url" to "https://auth.example.com/", "policy" to "admin", "headers" to "X-User"),
            toggles = mapOf("authorization" to true),
        )
        assertTrue(yaml, yaml.contains("\"https://auth.example.com/auth/verify?policy=admin\""))
        val authIndex = yaml.indexOf("Authorization")
        val userIndex = yaml.indexOf("X-User")
        assertTrue("Authorization must come first", authIndex in 1..<userIndex)
    }

    @Test
    fun `ip allow list honours the strategy`() {
        assertEquals(
            "ipAllowList:\n  sourceRange:\n    - \"10.0.0.0/8\"",
            build("ipAllowList", mapOf("cidrs" to "10.0.0.0/8")),
        )
        assertTrue(
            build("ipAllowList", mapOf("cidrs" to "10.0.0.0/8", "strategy" to "depth", "depth" to "2"))
                .endsWith("ipStrategy:\n    depth: 2"),
        )
        assertTrue(
            build("ipAllowList", mapOf("cidrs" to "10.0.0.0/8", "strategy" to "depth", "depth" to "0"))
                .endsWith("depth: 1"),
        )
        assertTrue(
            build(
                "ipAllowList",
                mapOf("cidrs" to "10.0.0.0/8", "strategy" to "excluded", "excluded" to "1.1.1.1/32"),
            ).contains("excludedIPs:\n      - \"1.1.1.1/32\""),
        )
    }

    @Test
    fun `the private ranges variant prefills the RFC1918 blocks`() {
        val yaml = build("ipAllowListPrivate")
        assertTrue(yaml, yaml.contains("10.0.0.0/8"))
        assertTrue(yaml, yaml.contains("192.168.0.0/16"))
    }

    @Test
    fun `secure headers emit only the ticked options`() {
        assertEquals(
            "headers:\n  sslRedirect: true\n  contentTypeNosniff: true",
            build(
                "secureHeaders",
                toggles = mapOf(
                    "ssl" to true, "hsts" to false, "nosniff" to true,
                    "xss" to false, "frame" to false, "referrer" to false,
                ),
            ),
        )
    }

    @Test
    fun `hsts sub-options only appear when hsts is on`() {
        val yaml = build(
            "secureHeaders",
            toggles = mapOf(
                "ssl" to false, "hsts" to true, "subdomains" to true, "preload" to false,
                "nosniff" to false, "xss" to false, "frame" to false, "referrer" to false,
            ),
        )
        assertTrue(yaml, yaml.contains("stsSeconds: 315360000"))
        assertTrue(yaml, yaml.contains("stsIncludeSubdomains: true"))
        assertTrue(yaml, !yaml.contains("stsPreload"))
    }

    @Test
    fun `encoded characters collapses to an empty map when nothing is ticked`() {
        assertEquals("encodedCharacters: {}", build("encodedCharacters"))
        assertEquals(
            "encodedCharacters:\n  allowEncodedSlash: true",
            build("encodedCharacters", toggles = mapOf("allowEncodedSlash" to true)),
        )
    }

    @Test
    fun `chain and scopes are unquoted lists`() {
        assertEquals(
            "chain:\n  middlewares:\n    - redirect-https\n    - secure-headers",
            build("chain", mapOf("middlewares" to "redirect-https\nsecure-headers")),
        )
    }

    @Test
    fun `numeric defaults are applied when a field is left blank`() {
        assertEquals("inFlightReq:\n  amount: 10", build("inFlightReq"))
        assertEquals("compress:\n  minResponseBodyBytes: 1200", build("compress"))
        assertEquals("retry:\n  attempts: 4\n  initialInterval: 100ms", build("retry"))
        assertEquals(
            "rateLimit:\n  average: 100\n  burst: 50\n  period: 1s",
            build("rateLimit"),
        )
    }

    @Test
    fun `oidc builds the plugin block with claim headers`() {
        val yaml = build(
            "oidcAuth",
            mapOf(
                "providerUrl" to "https://idp.example.com",
                "clientId" to "abc",
                "headers" to "X-User: preferred_username",
            ),
        )
        assertTrue(yaml, yaml.startsWith("plugin:\n  traefik-oidc-auth:"))
        assertTrue(yaml, yaml.contains("Url: \"https://idp.example.com\""))
        assertTrue(yaml, yaml.contains("MaxAge: 86400"))
        assertTrue(yaml, yaml.contains(".claims.preferred_username"))
    }

    @Test
    fun `quotes and backslashes are escaped rather than breaking the yaml`() {
        val yaml = build("addPrefix", mapOf("prefix" to """/a"b\c"""))
        assertEquals("""addPrefix:
  prefix: "/a\"b\\c"""", yaml)
    }

    @Test
    fun `every wizard produces something parseable-looking for its defaults`() {
        MiddlewareTemplates.all.forEach { wizard ->
            val yaml = wizard.build(WizardValues(emptyMap(), emptyMap(), wizard.fields))
            assertTrue("${wizard.id} produced blank yaml", yaml.isNotBlank())
            assertTrue("${wizard.id} has no top-level key", yaml.trimStart().contains(":"))
            assertTrue("${wizard.id} must not nest under http:", !yaml.startsWith("http:"))
        }
    }

    @Test
    fun `the kind is read from the first yaml key`() {
        assertEquals("forwardAuth", MiddlewareTemplates.kindOf("forwardAuth:\n  address: \"x\""))
        assertEquals("headers", MiddlewareTemplates.kindOf("# comment\nheaders:\n  frameDeny: true"))
        assertEquals("middleware", MiddlewareTemplates.kindOf(""))
    }

    @Test
    fun `usage counts routes, entry points and chain members`() {
        val routes = listOf(
            Route(
                id = "r1",
                name = "a",
                middlewares = JsonArray(listOf(JsonPrimitive("auth@file"))),
                entrypointMiddlewares = listOf("crowdsec@file"),
            ),
            Route(id = "r2", name = "b", middlewares = JsonArray(listOf(JsonPrimitive("bundle")))),
        )
        val middlewares = listOf(
            MiddlewareDef(name = "auth", yaml = "forwardAuth:\n  address: \"x\"\n"),
            MiddlewareDef(name = "crowdsec", yaml = "plugin:\n  crowdsec: {}\n"),
            MiddlewareDef(name = "bundle", yaml = "chain:\n  middlewares:\n    - inner\n"),
            MiddlewareDef(name = "inner", yaml = "headers:\n  frameDeny: true\n"),
            MiddlewareDef(name = "orphan", yaml = "compress: {}\n"),
        )

        val counts = MiddlewareUsage.countsFor(routes, middlewares)

        assertEquals(1, counts["auth"])
        assertEquals(1, counts["crowdsec"])
        assertEquals(1, counts["bundle"])
        assertEquals(1, counts["inner"])
        assertEquals(0, counts["orphan"])
        assertNotNull(counts["orphan"])
    }
}
