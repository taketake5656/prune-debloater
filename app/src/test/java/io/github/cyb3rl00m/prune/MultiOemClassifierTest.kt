package io.github.cyb3rl00m.prune

import io.github.cyb3rl00m.prune.core.db.Classifier
import io.github.cyb3rl00m.prune.core.db.DeviceContext
import io.github.cyb3rl00m.prune.core.db.DynamicProtections
import io.github.cyb3rl00m.prune.core.db.PackageDatabase
import io.github.cyb3rl00m.prune.core.history.Batch
import io.github.cyb3rl00m.prune.core.history.HistoryEntry
import io.github.cyb3rl00m.prune.core.history.HistoryFile
import io.github.cyb3rl00m.prune.core.model.ChangeType
import io.github.cyb3rl00m.prune.core.model.Risk
import io.github.cyb3rl00m.prune.core.model.Source
import io.github.cyb3rl00m.prune.core.oem.AccessSnapshot
import io.github.cyb3rl00m.prune.core.oem.CompatibilityReport
import io.github.cyb3rl00m.prune.core.oem.HuaweiIntegration
import io.github.cyb3rl00m.prune.core.oem.OpStatus
import io.github.cyb3rl00m.prune.core.oem.Operation
import io.github.cyb3rl00m.prune.core.oem.ServicesInfo
import io.github.cyb3rl00m.prune.core.oem.XiaomiIntegration
import io.github.cyb3rl00m.prune.core.plan.DebloatLevel
import io.github.cyb3rl00m.prune.core.plan.PlanBuilder
import io.github.cyb3rl00m.prune.core.plan.PlannedChange
import io.github.cyb3rl00m.prune.core.plan.ProfileOptions
import io.github.cyb3rl00m.prune.core.settings.AppSettings
import io.github.cyb3rl00m.prune.core.settings.SettingsStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiOemClassifierTest {
    init { TestStrings.install() }
    private val c = TestData.classifier
    private val all = TestData.db.ecosystems.map { it.key }.toSet()

    @Test fun manufacturerCriticalPackagesLocked() {
        val critical = listOf(
            "com.lbe.security.miui", "com.miui.securitycenter", "com.android.updater", "com.miui.rom",
            "com.coloros.safecenter", "com.oplus.securitypermission", "com.coloros.securitypermission",
            "com.huawei.systemmanager", "com.huawei.ims", "com.hihonor.systemmanager", "com.hihonor.keychain",
            "com.motorola.android.providers.settings", "com.vivo.permissionmanager",
        )
        for (p in critical) assertEquals(p, Risk.LOCKED, c.classify(TestData.facts(p)).risk)
    }

    @Test fun manufacturerLabels() {
        val miui = c.classify(TestData.facts("com.miui.msa.global"))
        assertEquals(Source.OEM, miui.source)
        assertEquals("Xiaomi", miui.manufacturer)
        assertEquals("Xiaomi", miui.sourceLabel)
        assertEquals(1, miui.level)
        assertEquals("OPPO / OnePlus / realme", c.classify(TestData.facts("com.heytap.market")).manufacturer)
        assertEquals("Motorola", c.classify(TestData.facts("com.motorola.demo")).manufacturer)
        assertEquals(Source.GOOGLE, c.classify(TestData.facts("com.google.android.apps.tips")).source)
    }

    @Test fun everyManufacturerHasMinimalCandidates() {
        val apps = TestData.curated.map { TestData.app(it.key) }
        val sel = PlanBuilder.candidates(apps, ProfileOptions.defaults(DebloatLevel.MINIMAL, all))
            .filter { it.selected && it.keptBecause == null }.map { it.app.classification.sourceLabel }.toSet()
        for (m in listOf("Samsung", "Xiaomi", "Huawei", "OPPO / OnePlus / realme", "Motorola")) assertTrue("$m in $sel", m in sel)
    }

    @Test fun ecosystemsOfOtherBrandsAreKept() {
        val apps = listOf("com.miui.cloudservice", "com.coloros.oshare", "com.huawei.health", "com.xiaomi.finddevice").map { TestData.app(it) }
        val cands = PlanBuilder.candidates(apps, ProfileOptions.defaults(DebloatLevel.AGGRESSIVE, all))
        assertTrue(cands.all { it.keptBecause != null })
    }

    @Test fun huaweiWithoutGmsKeepsHmsLocked() {
        val dyn = DynamicProtections(locked = HuaweiIntegration.dynamicProtections(ServicesInfo(false, false, true)))
        assertEquals(Risk.LOCKED, c.classify(TestData.facts("com.huawei.hwid"), dyn).risk)
        assertEquals(Risk.HIGH, c.classify(TestData.facts("com.huawei.hwid")).risk)
    }

    @Test fun unknownManufacturerPackageIsUnknown() {
        val r = c.classify(TestData.facts("com.fairphone.somethingnew"))
        assertEquals(Risk.UNKNOWN, r.risk)
        assertEquals(4, r.level)
        assertTrue(r.recovery.contains("pm install-existing"))
    }

    @Test fun preloadInstallerMakesUserAppAPreload() {
        val f = TestData.facts("com.example.game", system = false).copy(installer = "com.facebook.system")
        val r = c.classify(f)
        assertEquals(Source.PRELOAD, r.source)
        assertEquals(1, r.level)
        val own = c.classify(TestData.facts("com.example.game", system = false).copy(installer = "com.android.vending"))
        assertEquals(Source.USER, own.source)
        assertNull(own.level)
    }

    @Test fun regionalVariant() {
        assertTrue(c.classify(TestData.facts("com.mi.android.globalFileexplorer.global")).regional)
        assertFalse(c.classify(TestData.facts("com.hihonor.id")).regional)
    }

    @Test fun sdkAndFirmwareScopedEntries() {
        val core = """{"schema":2,"manufacturer":"Generic Android","updated":"x","entries":{
            "com.example.a":{"level":1,"risk":"SAFE","minSdk":34},
            "com.example.b":{"level":1,"risk":"SAFE","firmware":["HyperOS"]}}}"""
        val db = PackageDatabase.parse(listOf(core), "{}")
        assertNull(db.curated("com.example.a", DeviceContext(sdkInt = 33)))
        assertNotNull(db.curated("com.example.a", DeviceContext(sdkInt = 34)))
        assertNull(db.curated("com.example.b", DeviceContext(firmware = "MIUI")))
        assertNotNull(db.curated("com.example.b", DeviceContext(firmware = "HyperOS")))
        val cls = Classifier(db, DeviceContext(sdkInt = 30))
        assertEquals(Risk.UNKNOWN, cls.classify(TestData.facts("com.example.a")).risk)
    }

    @Test fun damagedRuleFileIsSkipped() {
        val db = PackageDatabase.parse(listOf("""{"schema":2,"updated":"x"}""", "{ not json"), "{}")
        assertEquals(1, db.files.size)
    }

    @Test fun ecosystemAnswersFromVersion1() {
        // Bixby を使わないと答えた 1.x のユーザー: Bixby は対象外のまま、新しいブランドの機能は「使用中」と見なす。
        val v1 = AppSettings(usedEcosystems = SettingsStore.V1_ECOSYSTEMS - "BIXBY", knownEcosystems = SettingsStore.V1_ECOSYSTEMS)
        val used = SettingsStore.effectiveUsed(v1, all)
        assertFalse("BIXBY" in used)
        assertTrue("XIAOMI_CLOUD" in used)
        assertEquals(all, SettingsStore.effectiveUsed(AppSettings(), all))
    }

    @Test fun compatibilityReport() {
        val d = MockDevices.xiaomiHyperOs
        val change = PlannedChange(type = ChangeType.DISABLE, packageName = "com.miui.msa.global", label = "x", explanation = "")
        val history = HistoryFile(batches = listOf(Batch("b", "t", "o", 0, "Shizuku", listOf(
            HistoryEntry("1", change, "enabled", "disabled", true),
            HistoryEntry("2", change, "enabled", "enabled", false, message = "Shell cannot change component state"),
        ))))
        val noAccess = AccessSnapshot("Standard", false, false, false, false, false, false, false)
        val r = CompatibilityReport.build("Xiaomi 15", d, XiaomiIntegration, XiaomiIntegration.firmware(d),
            ServicesInfo(true, true, false), noAccess, history)
        val disable = r.operations.first { it.operation == Operation.DISABLE }
        assertEquals(OpStatus.NEEDS_ACCESS, disable.status)
        assertEquals(1, disable.succeeded)
        assertEquals(1, disable.failed)
        assertTrue(disable.lastError!!.contains("Shell cannot"))
        assertTrue(r.operations.first { it.operation == Operation.PERMISSIONS }.caveat!!.contains("Security settings"))
        assertEquals(OpStatus.AVAILABLE, r.operations.first { it.operation == Operation.SCAN }.status)
        val old = CompatibilityReport.build("old", d.copy(sdkInt = 30), XiaomiIntegration, null, ServicesInfo(true, true, false),
            noAccess.copy(privileged = true), HistoryFile())
        assertEquals(OpStatus.UNSUPPORTED, old.operations.first { it.operation == Operation.NOTIFICATIONS }.status)
        assertTrue(r.asText("2.0.0").contains("HyperOS 2.0"))
    }
}
