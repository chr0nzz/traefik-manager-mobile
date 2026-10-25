package dev.chr0nzz.traefikmanager

import dev.chr0nzz.traefikmanager.data.store.SnapshotStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SnapshotStoreTest {

    @Test
    fun `each server gets its own slot`() {
        val host = SnapshotStore.slug("https://tm.example.com|")
        val agent = SnapshotStore.slug("https://tm.example.com|agent-7")
        assertNotEquals(host, agent)
    }

    @Test
    fun `two hosts never share a slot`() {
        assertNotEquals(
            SnapshotStore.slug("https://one.example.com|"),
            SnapshotStore.slug("https://two.example.com|"),
        )
    }

    @Test
    fun `the same server is stable across launches`() {
        assertEquals(
            SnapshotStore.slug("https://tm.example.com|agent-7"),
            SnapshotStore.slug("https://tm.example.com|agent-7"),
        )
    }

    @Test
    fun `a blank server still produces a slot`() {
        assertEquals(SnapshotStore.slug("host"), SnapshotStore.slug(""))
    }

    @Test
    fun `a slot is filename safe`() {
        val slug = SnapshotStore.slug("https://tm.example.com/sub path|agent 7")
        assertEquals(slug, slug.filter { it.isLetterOrDigit() })
    }
}
