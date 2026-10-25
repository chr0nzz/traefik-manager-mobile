package dev.chr0nzz.traefikmanager

import dev.chr0nzz.traefikmanager.data.model.MiddlewareDef
import dev.chr0nzz.traefikmanager.ui.components.distinctKeys
import org.junit.Assert.assertEquals
import org.junit.Test

class DistinctKeysTest {

    @Test
    fun `unique keys stay as they are`() {
        assertEquals(listOf("a", "b", "c"), distinctKeys(listOf("a", "b", "c")) { it })
    }

    @Test
    fun `a repeated key gets a counter so a lazy list never sees it twice`() {
        assertEquals(listOf("a", "a#2", "b", "a#3"), distinctKeys(listOf("a", "a", "b", "a")) { it })
    }

    @Test
    fun `the counter skips a key that is already taken`() {
        val keys = distinctKeys(listOf("a#2", "a", "a")) { it }
        assertEquals(listOf("a#2", "a", "a#3"), keys)
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun `empty keys are made distinct too`() {
        assertEquals(listOf("", "#2"), distinctKeys(listOf("", "")) { it })
    }

    @Test
    fun `an http and a tcp middleware with the same name in the same file get different keys`() {
        val middlewares = listOf(
            MiddlewareDef(name = "allowlist", type = "http", configFile = "dynamic.yml"),
            MiddlewareDef(name = "allowlist", type = "tcp", configFile = "dynamic.yml"),
        )
        assertEquals(
            listOf("http:allowlist:dynamic.yml", "tcp:allowlist:dynamic.yml"),
            distinctKeys(middlewares) { "${it.type}:${it.name}:${it.configFile}" },
        )
    }
}
