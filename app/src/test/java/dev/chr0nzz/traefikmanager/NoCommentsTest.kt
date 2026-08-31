package dev.chr0nzz.traefikmanager

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class NoCommentsTest {

    private val roots = listOf("src/main/java", "src/test/java")

    private fun sources(): List<File> = roots
        .map { File(it) }
        .filter { it.isDirectory }
        .flatMap { it.walkTopDown().filter { file -> file.isFile && file.extension == "kt" } }

    private fun offenders(file: File): List<String> {
        val found = mutableListOf<String>()
        var inBlock = false
        var inRaw = false
        file.readLines().forEachIndexed { index, line ->
            val text = line.trim()
            when {
                inRaw -> if (text.split("\"\"\"").size % 2 == 0) inRaw = false
                inBlock -> if (text.contains("*/")) inBlock = false
                text.split("\"\"\"").size % 2 == 0 -> inRaw = true
                text.startsWith("//") ->
                    found += "${file.path}:${index + 1}  ${text.take(60)}"
                text.startsWith("/*") -> {
                    if (!text.contains("*/")) inBlock = true
                    found += "${file.path}:${index + 1}  ${text.take(60)}"
                }
            }
        }
        return found
    }

    @Test
    fun `the sources are actually being scanned`() {
        val count = sources().size
        assertTrue("expected to find Kotlin sources, found $count", count > 100)
    }

    @Test
    fun `no comments anywhere`() {
        val found = sources().flatMap(::offenders)
        assertTrue(
            "code carries ${found.size} comment(s); this project does not use them:\n  " +
                found.take(60).joinToString("\n  ") +
                if (found.size > 60) "\n  ... and ${found.size - 60} more" else "",
            found.isEmpty(),
        )
    }
}
