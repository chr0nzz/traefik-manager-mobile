package dev.chr0nzz.traefikmanager

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class StringFormatTest {

    private val res = File("src/main/res")
    private val placeholder = Regex("""%(\d+\$)?([sdf%])""")

    private fun texts(file: File): Map<String, List<String>> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val found = mutableMapOf<String, List<String>>()
        val strings = document.getElementsByTagName("string")
        for (index in 0 until strings.length) {
            val node = strings.item(index) as Element
            if (node.getAttribute("formatted") == "false") continue
            found[node.getAttribute("name")] = listOf(node.textContent)
        }
        val plurals = document.getElementsByTagName("plurals")
        for (index in 0 until plurals.length) {
            val node = plurals.item(index) as Element
            val items = node.getElementsByTagName("item")
            found[node.getAttribute("name")] = (0 until items.length).map { items.item(it).textContent }
        }
        return found
    }

    private fun shape(text: String): Set<String> =
        placeholder.findAll(text).map { it.value }.filterNot { it == "%%" }.toSet()

    private fun formats(text: String): Boolean {
        val args = placeholder.findAll(text).filterNot { it.value == "%%" }.sortedBy { it.groupValues[1] }.map {
            when (it.groupValues[2]) {
                "d" -> 1 as Any
                "f" -> 1.0 as Any
                else -> "x" as Any
            }
        }.toList()
        return runCatching { String.format(text.replace("\\'", "'"), *args.toTypedArray<Any>()) }.isSuccess
    }

    private fun folders(): List<File> =
        res.listFiles { file -> file.isDirectory && file.name.startsWith("values") }.orEmpty()
            .filter { File(it, "strings.xml").exists() }

    @Test
    fun `every string formats with the arguments its placeholders ask for`() {
        val broken = folders().flatMap { folder ->
            texts(File(folder, "strings.xml")).flatMap { (name, values) ->
                values.filterNot(::formats).map { "${folder.name}/$name: $it" }
            }
        }
        assertTrue(broken.joinToString("\n  ", prefix = "\n  "), broken.isEmpty())
    }

    @Test
    fun `translations keep the placeholders of the English text`() {
        val base = texts(File(res, "values/strings.xml"))
        val wrong = folders().filter { it.name != "values" }.flatMap { folder ->
            texts(File(folder, "strings.xml")).mapNotNull { (name, values) ->
                val source = base[name] ?: return@mapNotNull "${folder.name}/$name has no English text"
                val expected = source.map(::shape).reduce { a, b -> a + b }
                val bad = values.filter { shape(it) - expected != emptySet<String>() || (values.size == 1 && shape(it) != expected) }
                if (bad.isEmpty()) null else "${folder.name}/$name: ${bad.first()}"
            }
        }
        assertTrue(wrong.joinToString("\n  ", prefix = "\n  "), wrong.isEmpty())
    }

    @Test
    fun `the English strings are being read`() {
        assertTrue(texts(File(res, "values/strings.xml")).size > 1000)
    }
}
