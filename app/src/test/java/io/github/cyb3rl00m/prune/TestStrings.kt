package io.github.cyb3rl00m.prune

import io.github.cyb3rl00m.prune.core.i18n.I18n
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

/**
 * JVM ユニットテスト用の文字列リゾルバ。Android の Context がないため、res/values 配下の XML (英語) を直接読み、
 * R.string の ID をリフレクションでリソース名に対応付ける。aapt と同じ規則でエスケープを解除し、
 * 引数がある場合だけ書式化する (Context.getString と同じ挙動)。
 */
object TestStrings {
    private val res: File by lazy { listOf(File("src/main/res"), File("app/src/main/res")).first { it.isDirectory } }

    private val byName: Map<String, String> by lazy {
        val out = mutableMapOf<String, String>()
        val factory = DocumentBuilderFactory.newInstance()
        File(res, "values").listFiles { f -> f.extension == "xml" }!!.forEach { file ->
            val nodes = factory.newDocumentBuilder().parse(file).getElementsByTagName("string")
            for (i in 0 until nodes.length) {
                val n = nodes.item(i)
                out[n.attributes.getNamedItem("name").nodeValue] = unescape(n.textContent)
            }
        }
        out
    }

    private val nameById: Map<Int, String> by lazy {
        R.string::class.java.fields.associate { it.getInt(null) to it.name }
    }

    /** 英語として I18n に登録する。何度呼んでもよい。 */
    fun install() {
        I18n.install("en") { id, args ->
            val name = nameById[id] ?: error("Unknown string id $id")
            val text = byName[name] ?: error("String $name is not in res/values")
            if (args.isEmpty()) text else String.format(Locale.US, text, *args)
        }
    }

    fun get(name: String): String = byName.getValue(name)

    /** aapt の文字列エスケープ解除 (二重引用符で囲まれた部分、\n \t \' \" \\ \@ \?) の簡易版。 */
    private fun unescape(raw: String): String {
        val sb = StringBuilder()
        var i = 0
        var quoted = false
        while (i < raw.length) {
            val c = raw[i]
            when {
                c == '\\' && i + 1 < raw.length -> {
                    when (val n = raw[i + 1]) {
                        'n' -> sb.append('\n')
                        't' -> sb.append('\t')
                        else -> sb.append(n)
                    }
                    i += 2; continue
                }
                c == '"' -> quoted = !quoted
                !quoted && c.isWhitespace() -> {
                    // 引用符の外では連続する空白を 1 つにまとめる。
                    if (sb.isEmpty() || !sb.last().isWhitespace() || sb.last() == '\n') sb.append(' ')
                    while (i + 1 < raw.length && raw[i + 1].isWhitespace()) i++
                }
                else -> sb.append(c)
            }
            i++
        }
        return sb.toString().trim()
    }
}
