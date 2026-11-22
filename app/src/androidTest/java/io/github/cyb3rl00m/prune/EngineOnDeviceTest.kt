package io.github.cyb3rl00m.prune

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.cyb3rl00m.prune.core.model.ChangeType
import io.github.cyb3rl00m.prune.core.model.PackageStatus
import io.github.cyb3rl00m.prune.core.model.Risk
import io.github.cyb3rl00m.prune.core.plan.Inverse
import io.github.cyb3rl00m.prune.core.plan.Plan
import io.github.cyb3rl00m.prune.core.plan.PlannedChange
import io.github.cyb3rl00m.prune.core.shell.Commands
import io.github.cyb3rl00m.prune.core.shell.OutputParsers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Prune の実際のコマンドをエミュレータ上で shell ユーザーとして実行し、Android から状態を読み戻して結果を確認する。
 * Shizuku を使う実機でアプリが行うのとまったく同じ方法。
 */
@RunWith(AndroidJUnit4::class)
class EngineOnDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val repo = context.repository
    private val shell = UiAutomationShell()
    private val self = context.packageName
    private val user = android.os.Process.myUid() / 100_000

    private fun target(): String {
        runBlocking { repo.rescan() }
        val candidates = listOf("com.android.egg", "com.android.dreams.basic", "com.android.dreams.phototable",
            "com.android.wallpaper.livepicker", "com.android.bips", "com.android.musicfx", "com.android.traceur")
        return candidates.first { p -> repo.app(p)?.let { it.facts.isSystem && it.status == PackageStatus.ENABLED && !it.isLocked } == true }
    }

    private fun applyOne(change: PlannedChange) = runBlocking {
        repo.executor.apply(Plan(change.label, "test", listOf(change)), shell).entries.single()
    }

    @Test fun scanAndClassifyRealSystem() {
        runBlocking { repo.rescan() }
        val apps = repo.scan.value.apps
        assertTrue("scanned ${apps.size}", apps.size > 100)
        assertEquals(Risk.LOCKED, repo.app("com.android.systemui")!!.risk)
        assertEquals(Risk.LOCKED, repo.app("android")!!.risk)
        assertEquals(Risk.LOCKED, repo.app("com.android.settings")!!.risk)
        assertEquals(Risk.LOCKED, repo.app(self)!!.risk)
        val launcher = repo.scanner.dynamicProtections().locked.entries.firstOrNull { it.value.contains("home screen") }
        assertNotNull("default launcher detected", launcher)
        assertEquals(Risk.LOCKED, repo.app(launcher!!.key)!!.risk)
        assertNotNull(repo.device.value)
    }

    @Test fun deviceIdentityAndCompatibilityOnEmulator() {
        runBlocking { repo.rescan() }
        val d = repo.device.value!!
        // CI のエミュレータはエミュレータとして認識されなければならない。Pixel や他のブランドと誤認してはならない。
        assertEquals(io.github.cyb3rl00m.prune.core.oem.EmulatorIntegration, d.integration)
        assertTrue("getprop returned properties", d.identity.props.size > 50)
        assertTrue(d.services.gms)
        assertEquals("all rule files load", 9, repo.database.files.size)
        val report = io.github.cyb3rl00m.prune.core.oem.CompatibilityReport.build(d.fullName, d.identity, d.integration,
            d.firmware, d.services, io.github.cyb3rl00m.prune.core.oem.AccessSnapshot("test", true, false, false, false, false, true, true),
            repo.history.state.value)
        assertTrue(report.operations.all { it.status == io.github.cyb3rl00m.prune.core.oem.OpStatus.AVAILABLE })
        // 重複検出が実際の PackageManager 上で失敗せずに動くこと。
        repo.duplicateFinder.find()
    }

    @Test fun disableEnableRoundTrip() {
        val pkg = target()
        val app = repo.app(pkg)!!
        val disable = PlannedChange(type = ChangeType.DISABLE, packageName = pkg, label = app.label, explanation = "")
        val e1 = applyOne(disable)
        assertEquals(e1.message, true, e1.success)
        assertEquals("enabled", e1.before)
        assertEquals("disabled", e1.after)
        assertEquals(PackageStatus.DISABLED, repo.scanner.read(pkg)!!.status)

        val e2 = applyOne(Inverse.of(e1.change, e1.before)!!)
        assertEquals(e2.message, true, e2.success)
        assertEquals("enabled", repo.executor.packageState(pkg))
    }

    @Test fun uninstallForUserAndReinstall() {
        val pkg = target()
        val e1 = applyOne(PlannedChange(type = ChangeType.UNINSTALL_USER, packageName = pkg, label = pkg, explanation = ""))
        assertEquals(e1.message, true, e1.success)
        assertEquals("removed", repo.executor.packageState(pkg))
        val inv = Inverse.of(e1.change, e1.before)!!
        assertEquals(ChangeType.ENABLE, inv.type)
        val e2 = applyOne(inv)
        assertEquals(e2.message, true, e2.success)
        assertEquals("enabled", repo.executor.packageState(pkg))
    }

    @Test fun backgroundAppOpRoundTrip() {
        val change = PlannedChange(type = ChangeType.APPOP_SET, packageName = self, label = "bg",
            params = mapOf("op" to "RUN_ANY_IN_BACKGROUND", "mode" to "ignore"), explanation = "")
        val e1 = applyOne(change)
        assertEquals(e1.message, true, e1.success)
        assertEquals("ignore", runBlocking { OutputParsers.appOpMode(shell.run(Commands.appOpGet(self, "RUN_ANY_IN_BACKGROUND", user)).stdout, "RUN_ANY_IN_BACKGROUND") })
        val restricted = runBlocking { OutputParsers.appOpQueryPackages(shell.run(Commands.appOpQuery("RUN_ANY_IN_BACKGROUND", "ignore", user)).stdout) }
        assertTrue("query-op lists $self: $restricted", self in restricted)
        val e2 = applyOne(Inverse.of(e1.change, e1.before)!!)
        assertEquals(e2.message, true, e2.success)
    }

    @Test fun batteryExemptionRoundTrip() {
        val add = PlannedChange(type = ChangeType.DEVICEIDLE_ADD, packageName = self, label = "add", explanation = "")
        val e1 = applyOne(add)
        assertEquals(e1.message, true, e1.success)
        val list = runBlocking { OutputParsers.deviceIdleWhitelist(shell.run(Commands.deviceIdleList()).stdout) }
        assertTrue(self in list.user)
        val e2 = applyOne(Inverse.of(e1.change, e1.before)!!)
        assertEquals(e2.message, true, e2.success)
        assertEquals("not-exempt", e2.after)
    }

    @Test fun animationSettingRoundTrip() {
        val change = repo.optimizer.animationChanges(0.5f).first()
        val e1 = applyOne(change)
        assertEquals(e1.message, true, e1.success)
        assertEquals(0.5f, repo.optimizer.animationState().window)
        val e2 = applyOne(Inverse.of(e1.change, e1.before)!!)
        assertEquals(e2.message, true, e2.success)
    }

    @Test fun selfGrantPermissions() {
        repo.access.testShell = shell
        repo.access.refresh()
        try {
            runBlocking { repo.access.grantSelfPermissions() }
            repo.access.refresh()
            assertTrue("usage access", repo.access.state.value.usageAccess)
            assertTrue("write secure settings", repo.access.state.value.writeSecureSettings)
        } finally {
            repo.access.testShell = null
            repo.access.refresh()
        }
    }
}
