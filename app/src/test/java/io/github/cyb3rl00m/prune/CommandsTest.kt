package io.github.cyb3rl00m.prune

import io.github.cyb3rl00m.prune.core.device.OneUi
import io.github.cyb3rl00m.prune.core.device.SamsungModels
import io.github.cyb3rl00m.prune.core.shell.Commands
import io.github.cyb3rl00m.prune.core.shell.OutputParsers
import io.github.cyb3rl00m.prune.core.shell.ShellResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandsTest {
    init { TestStrings.install() }
    @Test fun commandShapes() {
        assertEquals(listOf("pm", "disable-user", "--user", "0", "com.samsung.android.app.tips"), Commands.disable("com.samsung.android.app.tips", 0))
        assertEquals(listOf("pm", "uninstall", "-k", "--user", "0", "a.b"), Commands.uninstallForUser("a.b", 0))
        assertEquals(listOf("pm", "install-existing", "--user", "10", "a.b"), Commands.installExisting("a.b", 10))
        assertEquals(listOf("cmd", "appops", "set", "--user", "0", "a.b", "RUN_ANY_IN_BACKGROUND", "ignore"),
            Commands.appOpSet("a.b", "RUN_ANY_IN_BACKGROUND", "ignore", 0))
        assertEquals(listOf("cmd", "deviceidle", "whitelist", "-a.b"), Commands.deviceIdleRemove("a.b"))
    }

    @Test fun rejectsInjection() {
        for (bad in listOf("a.b; reboot", "a.b && rm -rf /", "\$(id)", "a b", "", "a.b\nreboot", "-k", "../x")) {
            assertFalse(bad, Commands.isValidPackage(bad))
            assertThrows(IllegalArgumentException::class.java) { Commands.disable(bad, 0) }
        }
        assertThrows(IllegalArgumentException::class.java) { Commands.appOpSet("a.b", "SYSTEM_ALERT_WINDOW", "allow", 0) }
        assertThrows(IllegalArgumentException::class.java) { Commands.settingsPut("adb_enabled", "0") }
        assertThrows(IllegalArgumentException::class.java) { Commands.settingsPut("window_animation_scale", "1;reboot") }
        assertThrows(IllegalArgumentException::class.java) { Commands.disable("a.b", -1) }
    }

    @Test fun quoting() {
        assertEquals("pm disable-user --user 0 a.b", Commands.toShell(Commands.disable("a.b", 0)))
        assertEquals("'it'\\''s'", Commands.quote("it's"))
        assertEquals("'a b'", Commands.quote("a b"))
    }

    @Test fun pmOutputs() {
        assertTrue(OutputParsers.disableSucceeded(ShellResult(0, "Package com.x new state: disabled-user\n", "")))
        assertFalse(OutputParsers.disableSucceeded(ShellResult(0, "", "java.lang.SecurityException: Shell cannot change component state for com.x to 3")))
        assertTrue(OutputParsers.enableSucceeded(ShellResult(0, "Package com.x new state: enabled", "")))
        assertTrue(OutputParsers.uninstallSucceeded(ShellResult(0, "Success\n", "")))
        assertFalse(OutputParsers.uninstallSucceeded(ShellResult(1, "Failure [DELETE_FAILED_INTERNAL_ERROR]", "")))
        assertTrue(OutputParsers.installExistingSucceeded(ShellResult(0, "Package com.x installed for user: 0", "")))
        assertFalse(ShellResult(0, "", "Exception occurred while executing").ok)
    }

    @Test fun appOps() {
        assertEquals("ignore", OutputParsers.appOpMode("RUN_ANY_IN_BACKGROUND: ignore; time=+1d2h ago", "RUN_ANY_IN_BACKGROUND"))
        assertEquals("allow", OutputParsers.appOpMode("Uid mode: RUN_ANY_IN_BACKGROUND: allow", "RUN_ANY_IN_BACKGROUND"))
        assertEquals("default", OutputParsers.appOpMode("No operations.", "RUN_ANY_IN_BACKGROUND"))
        assertEquals(setOf("com.a", "com.b.c"), OutputParsers.appOpQueryPackages("com.a\ncom.b.c\n\nNo operations."))
    }

    @Test fun deviceIdle() {
        val w = OutputParsers.deviceIdleWhitelist("system-excidle,com.android.shell,2000\nsystem,com.google.android.gms,10120\nuser,com.whatsapp,10200\nuser,com.example.app,10201\n")
        assertEquals(setOf("com.whatsapp", "com.example.app"), w.user)
        assertEquals(setOf("com.android.shell", "com.google.android.gms"), w.system)
    }

    @Test fun batteryStats() {
        val out = """
            Statistics since last charge:
              Estimated power use (mAh):
                Capacity: 4900, Computed drain: 812, actual drain: 700-750
                Global
                  screen: 300 apps: 300
                UID u0a234: 41.2 ( cpu=20.1 wakelock=1.2 )
                UID 1000: 55.9 ( cpu=50 )
                UID u10a12: 3.5
              All partial wake locks:
                UID u0a999: 99.0
        """.trimIndent()
        val m = OutputParsers.batteryUsePerUid(out)
        assertEquals(41.2, m[10234]!!, 0.001)
        assertEquals(55.9, m[1000]!!, 0.001)
        assertEquals(3.5, m[10 * 100_000 + 10_012]!!, 0.001)
        assertNull(m[10999])
    }

    @Test fun oneUi() {
        assertEquals("6.1.1", OneUi.fromProperty("60101"))
        assertEquals("8.0", OneUi.fromProperty("80000"))
        assertEquals("8.5", OneUi.fromProperty("80500"))
        assertNull(OneUi.fromProperty(""))
        assertNull(OneUi.fromProperty(null))
        assertEquals("2.5", OneUi.fromSemPlatformInt(110500))
        assertEquals("4.0", OneUi.fromSemPlatformInt(130000))
        assertNull(OneUi.fromSemPlatformInt(0))
    }

    @Test fun models() {
        assertEquals("Galaxy S25 FE", SamsungModels.marketName("SM-S731B"))
        assertEquals("Galaxy S25 Ultra", SamsungModels.marketName("SM-S938B"))
        assertNull(SamsungModels.marketName("Pixel 9"))
        assertEquals(2000, OutputParsers.uidOf("uid=2000(shell) gid=2000(shell)"))
    }
}
