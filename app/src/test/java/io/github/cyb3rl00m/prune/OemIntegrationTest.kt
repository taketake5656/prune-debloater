package io.github.cyb3rl00m.prune

import io.github.cyb3rl00m.prune.core.oem.CompatLevel
import io.github.cyb3rl00m.prune.core.oem.DeviceIdentity
import io.github.cyb3rl00m.prune.core.oem.EmulatorIntegration
import io.github.cyb3rl00m.prune.core.oem.GenericAndroidIntegration
import io.github.cyb3rl00m.prune.core.oem.GooglePixelIntegration
import io.github.cyb3rl00m.prune.core.oem.HonorIntegration
import io.github.cyb3rl00m.prune.core.oem.HuaweiIntegration
import io.github.cyb3rl00m.prune.core.oem.MotorolaIntegration
import io.github.cyb3rl00m.prune.core.oem.OemRegistry
import io.github.cyb3rl00m.prune.core.oem.OplusIntegration
import io.github.cyb3rl00m.prune.core.oem.SamsungIntegration
import io.github.cyb3rl00m.prune.core.oem.ServicesInfo
import io.github.cyb3rl00m.prune.core.oem.TranssionIntegration
import io.github.cyb3rl00m.prune.core.oem.VivoIntegration
import io.github.cyb3rl00m.prune.core.oem.XiaomiIntegration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * シミュレートした端末。各ファームウェアが設定するとされるプロパティに対して検出ロジックを確認する。
 * 実機テストではない。
 */
object MockDevices {
    fun device(manufacturer: String, brand: String, model: String, sdk: Int = 35, release: String = "15",
               props: Map<String, String> = emptyMap(), fingerprint: String = "$brand/$model/x:$release/ABC/1:user/release-keys") =
        DeviceIdentity(manufacturer, brand, model, "dev", "prod", fingerprint, sdk, release, "BUILD.123", props)

    val samsungS25Fe = device("samsung", "samsung", "SM-S731B", 36, "16", mapOf("ro.build.version.oneui" to "80000", "ro.csc.sales_code" to "EUX"))
    val samsungOld = device("samsung", "samsung", "SM-A505F", 30, "11", mapOf(SamsungIntegration.SEM_PLATFORM_PROP to "120100"))
    val pixel = device("Google", "google", "Pixel 9")
    val xiaomiHyperOs = device("Xiaomi", "Xiaomi", "24129PN74G", props = mapOf("ro.mi.os.version.name" to "OS2.0", "ro.miui.ui.version.name" to "V816"))
    val redmiMiui = device("Xiaomi", "Redmi", "23021RAA2Y", 33, "13", mapOf("ro.miui.ui.version.name" to "V14"))
    val poco = device("Xiaomi", "POCO", "23113RKC6G", 34, "14", mapOf("ro.mi.os.version.name" to "OS1.0"))
    val huaweiEmuiNoGms = device("HUAWEI", "HUAWEI", "ELS-NX9", 29, "10", mapOf("ro.build.version.emui" to "EmotionUI_12.0.0"))
    val huaweiHarmony = device("HUAWEI", "HUAWEI", "NOH-NX9", 29, "10", mapOf("hw_sc.build.platform.version" to "3.0.0", "ro.build.version.emui" to "EmotionUI_12.0.0"))
    val honor = device("HONOR", "HONOR", "PGT-N19", 34, "14", mapOf("ro.build.version.magic" to "MagicOS_8.0.0"))
    val onePlus = device("OnePlus", "OnePlus", "CPH2581", 35, "15", mapOf("ro.build.version.oplusrom" to "V15.0.0"))
    val oppo = device("OPPO", "OPPO", "CPH2557", 34, "14", mapOf("ro.build.version.oplusrom" to "V14.0.0"))
    val realme = device("realme", "realme", "RMX3851", 34, "14", mapOf("ro.build.version.realmeui" to "V5.0", "ro.build.version.oplusrom" to "V14.0.0"))
    val motorola = device("motorola", "motorola", "moto g84 5G", 34, "14")
    val vivo = device("vivo", "vivo", "V2250", 34, "14", mapOf("ro.vivo.os.name" to "Funtouch", "ro.vivo.os.version" to "14.0"))
    val tecno = device("TECNO", "TECNO", "TECNO CK8n", 33, "13")
    val unknown = device("Fairphone", "Fairphone", "FP5", 34, "14")
    val emulator = device("Google", "google", "sdk_gphone64_x86_64", fingerprint = "google/sdk_gphone64_x86_64/emu64xa:15/AE3A/1:userdebug/dev-keys",
        props = mapOf("ro.kernel.qemu" to "1"))
    val weird = DeviceIdentity("", "", "", "", "", "", 28, "", "", emptyMap())
}

class OemIntegrationTest {
    init { TestStrings.install() }
    private val gms = ServicesInfo(gms = true, playStore = true, hms = false)
    private val noGms = ServicesInfo(gms = false, playStore = false, hms = true)

    @Test fun detection() {
        val expected = mapOf(
            MockDevices.samsungS25Fe to SamsungIntegration, MockDevices.samsungOld to SamsungIntegration,
            MockDevices.pixel to GooglePixelIntegration, MockDevices.xiaomiHyperOs to XiaomiIntegration,
            MockDevices.redmiMiui to XiaomiIntegration, MockDevices.poco to XiaomiIntegration,
            MockDevices.huaweiEmuiNoGms to HuaweiIntegration, MockDevices.huaweiHarmony to HuaweiIntegration,
            MockDevices.honor to HonorIntegration, MockDevices.onePlus to OplusIntegration, MockDevices.oppo to OplusIntegration,
            MockDevices.realme to OplusIntegration, MockDevices.motorola to MotorolaIntegration, MockDevices.vivo to VivoIntegration,
            MockDevices.tecno to TranssionIntegration, MockDevices.unknown to GenericAndroidIntegration,
            MockDevices.emulator to EmulatorIntegration, MockDevices.weird to GenericAndroidIntegration,
        )
        for ((d, i) in expected) assertSame("${d.manufacturer} ${d.model}", i, OemRegistry.detect(d))
    }

    @Test fun firmware() {
        fun fw(d: DeviceIdentity) = OemRegistry.firmwareSafe(OemRegistry.detect(d), d)?.toString()
        assertEquals("One UI 8.0", fw(MockDevices.samsungS25Fe))
        assertEquals("One UI 3.1", fw(MockDevices.samsungOld))
        assertEquals("HyperOS 2.0", fw(MockDevices.xiaomiHyperOs))
        assertEquals("MIUI 14", fw(MockDevices.redmiMiui))
        assertEquals("HyperOS 1.0", fw(MockDevices.poco))
        assertEquals("EMUI 12.0.0", fw(MockDevices.huaweiEmuiNoGms))
        assertEquals("HarmonyOS 3.0.0", fw(MockDevices.huaweiHarmony))
        assertEquals("MagicOS 8.0.0", fw(MockDevices.honor))
        assertEquals("OxygenOS 15.0.0", fw(MockDevices.onePlus))
        assertEquals("ColorOS 14.0.0", fw(MockDevices.oppo))
        assertEquals("realme UI 5.0", fw(MockDevices.realme))
        assertEquals("My UX", fw(MockDevices.motorola))
        assertEquals("Funtouch OS 14.0", fw(MockDevices.vivo))
        assertEquals("HiOS", fw(MockDevices.tecno))
        assertEquals("Android 14", fw(MockDevices.unknown))
    }

    @Test fun brandNames() {
        assertEquals("Redmi", XiaomiIntegration.brandName(MockDevices.redmiMiui))
        assertEquals("POCO", XiaomiIntegration.brandName(MockDevices.poco))
        assertEquals("OnePlus", OplusIntegration.brandName(MockDevices.onePlus))
        assertEquals("realme", OplusIntegration.brandName(MockDevices.realme))
        assertEquals("Samsung Galaxy", SamsungIntegration.brandName(MockDevices.samsungS25Fe))
    }

    @Test fun compatibilityLevelsAreHonest() {
        // TESTED を名乗れるのは (自動テストを実行している) エミュレータだけ。
        for (i in OemRegistry.integrations + GenericAndroidIntegration) assertTrue(i.id, i.compat != CompatLevel.TESTED)
        assertEquals(CompatLevel.TESTED, EmulatorIntegration.compat)
        assertEquals(CompatLevel.PARTIAL, SamsungIntegration.compat)
        assertEquals(CompatLevel.EXPERIMENTAL, XiaomiIntegration.compat)
        assertEquals(CompatLevel.GENERIC, GenericAndroidIntegration.compat)
    }

    @Test fun huaweiWithoutGoogleServicesLocksHms() {
        val locks = HuaweiIntegration.dynamicProtections(noGms)
        assertTrue("com.huawei.hwid" in locks)
        assertTrue("com.huawei.appmarket" in locks)
        assertTrue(HuaweiIntegration.dynamicProtections(gms).isEmpty())
        val lim = HuaweiIntegration.limitations(MockDevices.huaweiHarmony, noGms).map { it.title }
        assertTrue("No Google services" in lim)
        assertTrue("HarmonyOS" in lim)
        assertFalse("No Google services" in HuaweiIntegration.limitations(MockDevices.huaweiEmuiNoGms, gms).map { it.title })
    }

    @Test fun firmwareSetupSteps() {
        assertTrue(XiaomiIntegration.extraSetupSteps(MockDevices.xiaomiHyperOs).single().contains("USB debugging (Security settings)"))
        assertTrue(OplusIntegration.extraSetupSteps(MockDevices.oppo).single().contains("Disable permission monitoring"))
        assertTrue(GenericAndroidIntegration.extraSetupSteps(MockDevices.unknown).isEmpty())
    }

    @Test fun manufacturerOfPackages() {
        assertSame(XiaomiIntegration, OemRegistry.manufacturerOf("com.miui.msa.global"))
        assertSame(OplusIntegration, OemRegistry.manufacturerOf("com.coloros.safecenter"))
        assertSame(HonorIntegration, OemRegistry.manufacturerOf("com.hihonor.id"))
        assertSame(HuaweiIntegration, OemRegistry.manufacturerOf("com.huawei.hwid"))
        assertSame(SamsungIntegration, OemRegistry.manufacturerOf("com.sec.android.app.launcher"))
        assertNull(OemRegistry.manufacturerOf("org.fdroid.fdroid"))
    }

    @Test fun getpropParsing() {
        val m = DeviceIdentity.parseGetprop("[ro.build.version.oneui]: [80000]\n[ro.miui.ui.version.name]: [V14]\n[empty]: []\ngarbage line\n")
        assertEquals("80000", m["ro.build.version.oneui"])
        assertEquals("V14", m["ro.miui.ui.version.name"])
        assertEquals("", m["empty"])
        assertEquals(3, m.size)
    }
}
