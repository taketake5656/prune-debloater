package io.github.cyb3rl00m.prune.core.i18n

import android.content.Context
import androidx.annotation.StringRes

/**
 * Context を持たないコード (core/ のロジックや enum のラベル) から文字列リソースを引くための窓口。
 *
 * アプリ起動時に [install] で Application の Context を登録する。JVM ユニットテストでは
 * テスト側が res/values 配下の XML を読む独自のリゾルバを登録するので、英語の文言で検証できる。
 * 文字列は呼び出し時に毎回解決されるため、端末の言語設定に従う。
 */
object I18n {
    @Volatile private var resolver: ((Int, Array<out Any>) -> String)? = null
    @Volatile private var languageProvider: () -> String = { "en" }

    fun install(context: Context) {
        val app = context.applicationContext
        resolver = { id, args -> if (args.isEmpty()) app.getString(id) else app.getString(id, *args) }
        languageProvider = { app.resources.configuration.locales[0]?.language ?: "en" }
    }

    /** テスト用: 任意のリゾルバを登録する。 */
    fun install(language: String = "en", resolve: (Int, Array<out Any>) -> String) {
        resolver = resolve
        languageProvider = { language }
    }

    /** 現在の表示言語 (ISO 639 の言語コード。例: "en", "ja")。 */
    val language: String get() = languageProvider()

    fun s(@StringRes id: Int, vararg args: Any): String =
        checkNotNull(resolver) { "I18n.install() has not been called" }.invoke(id, args)
}

/** [I18n.s] の短縮形。 */
fun str(@StringRes id: Int, vararg args: Any): String = I18n.s(id, *args)
