package dev.chr0nzz.traefikmanager

import android.icu.text.PluralRules
import android.icu.util.ULocale
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.w3c.dom.Element

@RunWith(RobolectricTestRunner::class)
class PluralFormsTest {

    private val res = File("src/main/res")
    private val samples = (0L..1000L) + listOf(1_000_000L, 2_000_000L)

    private fun tag(folder: String): String? {
        val qualifier = folder.removePrefix("values-")
        if (qualifier == folder || qualifier == "night") return null
        if (qualifier.startsWith("b+")) return qualifier.removePrefix("b+").replace('+', '-')
        return qualifier.replace("-r", "-")
    }

    private fun needed(tag: String): Set<String> {
        val rules = PluralRules.forLocale(ULocale.forLanguageTag(tag))
        return samples.map { rules.select(it.toDouble()) }.toSet()
    }

    private fun plurals(file: File): Map<String, Set<String>> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = document.getElementsByTagName("plurals")
        return (0 until nodes.length).associate { index ->
            val node = nodes.item(index) as Element
            val items = node.getElementsByTagName("item")
            node.getAttribute("name") to (0 until items.length).map { (items.item(it) as Element).getAttribute("quantity") }.toSet()
        }
    }

    private fun translations(): List<Pair<File, String>> =
        res.listFiles().orEmpty()
            .filter { it.isDirectory && File(it, "strings.xml").exists() }
            .mapNotNull { folder -> tag(folder.name)?.let { File(folder, "strings.xml") to it } }

    @Test
    fun `every translated plural carries each form its language uses for whole numbers`() {
        val missing = translations().flatMap { (file, tag) ->
            val forms = needed(tag)
            plurals(file).mapNotNull { (name, quantities) ->
                val gap = forms - quantities
                if (gap.isEmpty()) null else "${file.parentFile.name}/$name: missing ${gap.sorted().joinToString()}"
            }
        }
        assertTrue(missing.joinToString("\n  ", prefix = "\n  "), missing.isEmpty())
    }

    @Test
    fun `the rules know the shipped languages`() {
        assertTrue(needed("ru") == setOf("one", "few", "many"))
        assertTrue(needed("zh-Hans") == setOf("other"))
    }
}
