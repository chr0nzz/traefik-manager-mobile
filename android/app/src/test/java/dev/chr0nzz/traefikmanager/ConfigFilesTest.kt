package dev.chr0nzz.traefikmanager

import dev.chr0nzz.traefikmanager.data.model.ConfigsResponse
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigFilesTest {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Test
    fun `an agent's file list reads its names instead of leaving every row blank`() {
        val agent = json.decodeFromString<ConfigsResponse>(
            """{"files":[{"name":"routes.yml","content":"http: {}"},{"name":"media.yml","content":""},{"name":"media.yml","content":""}]}""",
        ).normalized(onAgent = true)
        assertEquals(listOf("media.yml", "routes.yml"), agent.files.map { it.label })
        assertEquals(agent.files.map { it.label }, agent.files.map { it.path })
        assertTrue("web offers a new file on agents too", agent.configDirSet)
    }

    @Test
    fun `the host list keeps its labels, paths and order`() {
        val host = json.decodeFromString<ConfigsResponse>(
            """{"files":[{"label":"z.yml","path":"/config/z.yml"},{"label":"a.yml","path":"/config/a.yml"}],"configDirSet":false}""",
        ).normalized(onAgent = false)
        assertEquals(listOf("z.yml", "a.yml"), host.files.map { it.label })
        assertEquals("/config/z.yml", host.files.first().path)
        assertFalse(host.configDirSet)
    }
}
