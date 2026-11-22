package io.github.cyb3rl00m.prune

import io.github.cyb3rl00m.prune.core.db.Classifier
import io.github.cyb3rl00m.prune.core.db.DynamicProtections
import io.github.cyb3rl00m.prune.core.db.PackageDatabase
import io.github.cyb3rl00m.prune.core.model.AppPackage
import io.github.cyb3rl00m.prune.core.model.PackageFacts
import java.io.File

/** APK に同梱される実際のデータベース資産を読み込む。 */
object TestData {
    init { TestStrings.install() }
    val assets: File by lazy { listOf(File("src/main/assets"), File("app/src/main/assets")).first { it.isDirectory } }

    /** アプリが読み込む順 (rules/index.json) に並べたルールファイル。 */
    val ruleFiles: List<String> by lazy {
        Regex("\"([a-z_]+\\.json)\"").findAll(File(assets, "rules/index.json").readText()).map { it.groupValues[1] }.toList()
    }

    val db: PackageDatabase by lazy {
        PackageDatabase.parse(ruleFiles.map { File(assets, "rules/$it").readText() }, File(assets, "packages_uad.json").readText())
    }

    /** 全ルールファイルのキュレーション済みエントリ。 */
    val curated by lazy { db.files.flatMap { it.entries.entries } }
    val locked by lazy { db.files.flatMap { it.locked } }
    val classifier by lazy { Classifier(db) }

    fun facts(pkg: String, system: Boolean = true, enabled: Boolean = true, installed: Boolean = true,
              persistent: Boolean = false, sharedUid: String? = null) =
        PackageFacts(pkg, pkg.substringAfterLast('.'), system, false, persistent, sharedUid, installed, enabled, "1.0", 10_123)

    fun app(pkg: String, system: Boolean = true, enabled: Boolean = true, lastUsed: Long? = null,
            dynamic: DynamicProtections = DynamicProtections(), installed: Boolean = true): AppPackage {
        val f = facts(pkg, system, enabled, installed)
        return AppPackage(f, classifier.classify(f, dynamic), lastUsed)
    }
}
