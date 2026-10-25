package dev.chr0nzz.traefikmanager

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class HardcodedTextTest {

    private val root = File("src/main/java/dev/chr0nzz/traefikmanager")
    private val baselineFile = File("src/test/resources/i18n-baseline.txt")
    private val allowedFile = File("src/test/resources/i18n-allowed.txt")
    private val literal = Regex(""""((?:[^"\\]|\\.)*)"""")
    private val rawText = Regex("""UiText\.Raw\("(?:[^"\\]|\\.)*"\)""")
    private val identifier = Regex("""[A-Za-z0-9_.\-/:]+""")
    private val word = Regex("""[A-Z][a-z]+""")
    private val skippedFiles = setOf("DemoApi.kt")
    private val skippedPrefixes = listOf("import ", "package ", "@SerialName", "@Json", "Log.")

    private fun isText(value: String): Boolean {
        if (value.length < 2 || !value.first().isUpperCase() || value.first() !in 'A'..'Z') return false
        return !identifier.matches(value) || word.matches(value)
    }

    private fun folderOf(file: File): String {
        val parts = file.relativeTo(root).invariantSeparatorsPath.split('/')
        return if (parts.size > 2) parts.take(2).joinToString("/") else parts.first()
    }

    private fun allowed(): Set<String> = allowedFile.readLines().filter { it.isNotBlank() }.toSet()

    private fun counts(): Map<String, Int> {
        val found = sortedMapOf<String, Int>()
        val allowed = allowed()
        root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name !in skippedFiles }
            .forEach { file ->
                val path = file.relativeTo(root).invariantSeparatorsPath
                if ("$path|*" in allowed) return@forEach
                val hits = file.readLines()
                    .map { it.trim() }
                    .filterNot { line -> skippedPrefixes.any(line::startsWith) }
                    .map { it.replace(rawText, "") }
                    .sumOf { line ->
                        literal.findAll(line).map { it.groupValues[1] }.count { isText(it) && "$path|$it" !in allowed }
                    }
                if (hits > 0) found.merge(folderOf(file), hits, Int::plus)
            }
        return found
    }

    private fun baseline(): Map<String, Int> = baselineFile.readLines()
        .filter { it.isNotBlank() }
        .associate { it.substringBeforeLast(' ') to it.substringAfterLast(' ').toInt() }

    @Test
    fun `hardcoded text only ever goes down`() {
        val allowed = baseline()
        val actual = counts()
        val problems = (allowed.keys + actual.keys).sorted().mapNotNull { folder ->
            val now = actual[folder] ?: 0
            val limit = allowed[folder] ?: 0
            when {
                now > limit -> "$folder has $now hardcoded strings, the baseline allows $limit: use a string resource"
                now < limit -> "$folder is down to $now hardcoded strings: lower it from $limit in ${baselineFile.path}"
                else -> null
            }
        }
        assertTrue(problems.joinToString("\n  ", prefix = "\n  "), problems.isEmpty())
    }
}
