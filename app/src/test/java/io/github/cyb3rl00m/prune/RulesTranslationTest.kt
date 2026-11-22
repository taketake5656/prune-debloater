package io.github.cyb3rl00m.prune

import io.github.cyb3rl00m.prune.core.db.PackageDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** ルールファイルの日本語訳 (assets/rules/ja) が読み込まれ、判定に使う値を変えずに文言だけを置き換えることを確認する。 */
class RulesTranslationTest {
    init { TestStrings.install() }

    private val ja: PackageDatabase by lazy {
        PackageDatabase.parse(
            TestData.ruleFiles.map { File(TestData.assets, "rules/$it").readText() },
            File(TestData.assets, "packages_uad.json").readText(),
            TestData.ruleFiles.map { File(TestData.assets, "rules/ja/$it").takeIf { f -> f.isFile }?.readText() },
        )
    }

    @Test fun everyRuleFileHasAJapaneseTranslation() {
        TestData.ruleFiles.forEach { assertTrue("rules/ja/$it is missing", File(TestData.assets, "rules/ja/$it").isFile) }
    }

    @Test fun translationsParse() {
        TestData.ruleFiles.forEach { PackageDatabase.parseTranslation(File(TestData.assets, "rules/ja/$it").readText()) }
        assertEquals(TestData.db.files.size, ja.files.size)
    }

    @Test fun textIsTranslatedButDecisionsAreNot() {
        val pkg = "com.samsung.android.app.tips"
        val en = TestData.db.curated(pkg)!!.entry
        val jp = ja.curated(pkg)!!.entry
        assertNotEquals(en.note, jp.note)
        assertEquals(en.level, jp.level)
        assertEquals(en.risk, jp.risk)
        assertEquals(TestData.db.curatedCount, ja.curatedCount)
    }

    @Test fun lockedReasonsAndGroupsAreTranslated() {
        assertTrue(ja.lockedReason("com.android.phone")!!.contains("通話"))
        val wallet = ja.ecosystemsByKey["WALLET"]
        assertNotNull(wallet)
        assertTrue(wallet!!.warning.contains("Samsung Wallet"))
        assertEquals(TestData.db.ecosystemsByKey["WALLET"]!!.packages, wallet.packages)
    }

    @Test fun brokenTranslationFallsBackToEnglish() {
        val db = PackageDatabase.parse(
            TestData.ruleFiles.map { File(TestData.assets, "rules/$it").readText() },
            File(TestData.assets, "packages_uad.json").readText(),
            TestData.ruleFiles.map { "{ not json" },
        )
        assertEquals(TestData.db.curated("com.samsung.android.app.tips")!!.entry.note,
            db.curated("com.samsung.android.app.tips")!!.entry.note)
    }
}
