package io.github.cyb3rl00m.prune

import io.github.cyb3rl00m.prune.core.model.ChangeType
import io.github.cyb3rl00m.prune.core.model.Risk
import io.github.cyb3rl00m.prune.core.model.RemovalMethod
import io.github.cyb3rl00m.prune.core.plan.DebloatLevel
import io.github.cyb3rl00m.prune.core.plan.Inverse
import io.github.cyb3rl00m.prune.core.plan.PlanBuilder
import io.github.cyb3rl00m.prune.core.plan.ProfileOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanBuilderTest {
    init { TestStrings.install() }
    private val all = TestData.db.ecosystems.map { it.key }.toSet()

    /** 現実的な Galaxy のパッケージ構成: キュレーション済みエントリ全件、ロック ID 全件、コミュニティのエントリ数件。 */
    private val apps by lazy {
        val ids = TestData.curated.map { it.key } + TestData.locked.map { it.id } +
            TestData.db.uad.keys.filter { it.startsWith("com.samsung") || it.startsWith("com.google") }.take(400)
        ids.distinct().map { TestData.app(it) }
    }

    @Test fun lockedNeverCandidates() {
        for (level in DebloatLevel.entries) {
            val cands = PlanBuilder.candidates(apps, ProfileOptions.defaults(level, emptySet()).copy(includeGoogle = true))
            assertTrue(cands.none { it.app.risk == Risk.LOCKED })
        }
    }

    @Test fun levelsAreNested() {
        fun selected(l: DebloatLevel) = PlanBuilder.candidates(apps, ProfileOptions.defaults(l, all).copy(includeGoogle = false))
            .filter { it.selected && it.keptBecause == null }.map { it.app.packageName }.toSet()
        val l1 = selected(DebloatLevel.MINIMAL)
        val l2 = selected(DebloatLevel.BALANCED)
        val l3 = selected(DebloatLevel.AGGRESSIVE)
        assertTrue(l1.isNotEmpty())
        assertTrue(l2.containsAll(l1))
        assertTrue(l3.containsAll(l2))
        assertTrue(l3.size > l1.size)
    }

    @Test fun highRiskNeverPreselected() {
        val cands = PlanBuilder.candidates(apps, ProfileOptions.defaults(DebloatLevel.CUSTOM, emptySet()).copy(includeGoogle = true))
        assertTrue(cands.any { it.app.risk == Risk.HIGH })
        assertTrue(cands.filter { it.app.classification.needsCare }.none { it.selected })
    }

    @Test fun usedEcosystemsAreKept() {
        val cands = PlanBuilder.candidates(apps, ProfileOptions.defaults(DebloatLevel.AGGRESSIVE, all))
        val pay = cands.first { it.app.packageName == "com.samsung.android.spay" }
        assertFalse(pay.selected)
        assertNotNull(pay.keptBecause)
        val without = PlanBuilder.candidates(apps, ProfileOptions.defaults(DebloatLevel.AGGRESSIVE, all - "WALLET"))
        assertTrue(without.first { it.app.packageName == "com.samsung.android.spay" }.selected)
    }

    @Test fun minimalKeepsEcosystemAndGoogle() {
        val cands = PlanBuilder.candidates(apps, ProfileOptions.defaults(DebloatLevel.MINIMAL, all))
        val selected = cands.filter { it.selected && it.keptBecause == null }.map { it.app }
        assertTrue(selected.none { it.classification.groups.any { g -> g in all } })
        assertTrue(selected.none { it.classification.source == io.github.cyb3rl00m.prune.core.model.Source.GOOGLE })
        assertTrue(selected.all { (it.classification.level ?: 9) == 1 })
    }

    @Test fun recentlyUsedNotPreselected() {
        val now = System.currentTimeMillis()
        val used = TestData.app("com.samsung.android.app.tips", lastUsed = now - 2 * 24 * 3600_000L)
        val c = PlanBuilder.candidates(listOf(used), ProfileOptions.defaults(DebloatLevel.MINIMAL, all), now).single()
        assertFalse(c.selected)
        assertNotNull(c.note)
    }

    @Test fun alreadyDisabledNotCandidates() {
        val off = TestData.app("com.samsung.android.app.tips", enabled = false)
        assertTrue(PlanBuilder.candidates(listOf(off), ProfileOptions.defaults(DebloatLevel.MINIMAL, all)).isEmpty())
    }

    @Test fun userAppsAreDisabledNotUninstalled() {
        val fb = TestData.app("com.facebook.katana", system = false)
        val change = PlanBuilder.changeFor(fb, RemovalMethod.UNINSTALL_USER, true)
        assertEquals(ChangeType.DISABLE, change.type)
        assertFalse(change.fallbackUninstall)
        val sys = PlanBuilder.changeFor(TestData.app("com.samsung.android.app.tips"), RemovalMethod.UNINSTALL_USER, true)
        assertEquals(ChangeType.UNINSTALL_USER, sys.type)
    }

    @Test fun inverses() {
        val tips = TestData.app("com.samsung.android.app.tips")
        val disable = PlanBuilder.changeFor(tips, RemovalMethod.DISABLE, false)
        assertEquals(ChangeType.ENABLE, Inverse.of(disable, "enabled")!!.type)
        assertNull(Inverse.of(disable, "disabled"))
        val uninstall = PlanBuilder.changeFor(tips, RemovalMethod.UNINSTALL_USER, false)
        assertEquals(ChangeType.ENABLE, Inverse.of(uninstall, "enabled")!!.type)
        assertEquals(ChangeType.REINSTALL_USER, Inverse.of(uninstall, "disabled")!!.type)
        val setting = io.github.cyb3rl00m.prune.core.plan.PlannedChange(type = ChangeType.SETTING_PUT, label = "x",
            params = mapOf("key" to "window_animation_scale", "value" to "0.5"), explanation = "")
        assertEquals("1.0", Inverse.of(setting, "1.0")!!.params["value"])
        assertNull(Inverse.of(setting, "null")!!.params["value"])
    }
}
