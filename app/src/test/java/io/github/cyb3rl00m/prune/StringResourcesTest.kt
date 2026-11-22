package io.github.cyb3rl00m.prune

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** 英語 (values) と日本語 (values-ja) の文字列リソースが対応しているかを確認する。 */
class StringResourcesTest {
    private val res = listOf(File("src/main/res"), File("app/src/main/res")).first { it.isDirectory }

    private fun load(dir: String): Map<String, String> {
        val out = mutableMapOf<String, String>()
        val factory = DocumentBuilderFactory.newInstance()
        File(res, dir).listFiles { f -> f.extension == "xml" }!!.forEach { file ->
            val nodes = factory.newDocumentBuilder().parse(file).getElementsByTagName("string")
            for (i in 0 until nodes.length) {
                val n = nodes.item(i)
                val name = n.attributes.getNamedItem("name").nodeValue
                assertTrue("Duplicate string $name in $dir", out.put(name, n.textContent) == null)
            }
        }
        return out
    }

    private val en = load("values")
    private val ja = load("values-ja")

    /** 書式指定子 (%1$s、%2$d、%% など) の集合。 */
    private fun specs(s: String) = Regex("""%(\d+\$)?[sdf%]""").findAll(s).map { it.value }.sorted().toList()

    @Test fun everyStringHasJapanese() {
        val missing = en.keys - ja.keys - setOf("app_name")
        assertTrue("Missing Japanese: $missing", missing.isEmpty())
    }

    @Test fun noJapaneseWithoutEnglish() {
        val extra = ja.keys - en.keys
        assertTrue("Japanese without English: $extra", extra.isEmpty())
    }

    @Test fun formatArgumentsMatch() {
        for ((k, v) in en) {
            val j = ja[k] ?: continue
            assertEquals("Format arguments differ for $k", specs(v), specs(j))
        }
    }
}
