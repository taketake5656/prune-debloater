package io.github.cyb3rl00m.prune.core.oem

import io.github.cyb3rl00m.prune.R
import io.github.cyb3rl00m.prune.core.device.OneUi
import io.github.cyb3rl00m.prune.core.i18n.str

// メーカー別の統合。ファームウェアのプロパティがなければバージョンが得られないだけで、
// 検出が端末で失敗することはない (説明が大まかになるだけ)。
// 表示文言は文字列リソースから毎回取得する (getter にしているのは表示言語の変更に追従するため)。
// ブランド名・ファームウェア名はルールの照合にも使う識別子なので翻訳しない。

object SamsungIntegration : OemIntegration() {
    override val id = "samsung"
    override val title = "Samsung Galaxy (One UI)"
    override val compat = CompatLevel.PARTIAL
    override val packagePrefixes = listOf("com.samsung.", "com.sec.", "com.sem.", "com.osp.", "com.knox", "com.skms.",
        "com.wssyncmldm", "com.wsomacp")
    override fun matches(d: DeviceIdentity) = d.manufacturerKey == "samsung"
    override fun firmware(d: DeviceIdentity): Firmware {
        val v = OneUi.fromProperty(d.prop("ro.build.version.oneui"))
            ?: OneUi.fromSemPlatformInt(d.prop(SEM_PLATFORM_PROP)?.toIntOrNull())
        return Firmware("One UI", v)
    }
    override fun brandName(d: DeviceIdentity) = "Samsung Galaxy"
    override val developerOptionsSteps get() = listOf(str(R.string.samsung_dev_1), str(R.string.samsung_dev_2))
    override fun limitations(d: DeviceIdentity, services: ServicesInfo) = listOf(
        Limitation(str(R.string.samsung_lim_disable_t), str(R.string.samsung_lim_disable)),
        Limitation("Knox", str(R.string.samsung_lim_knox)),
    )

    /** 合成プロパティ: Samsung のファームウェアでリフレクションにより読む Build.VERSION.SEM_PLATFORM_INT。 */
    const val SEM_PLATFORM_PROP = "prune.sem_platform_int"
}

object GooglePixelIntegration : OemIntegration() {
    override val id = "pixel"
    override val title get() = str(R.string.pixel_title)
    override val compat = CompatLevel.PARTIAL
    override val packagePrefixes = listOf("com.google.android.apps.pixel", "com.google.pixel.")
    override fun matches(d: DeviceIdentity) = d.manufacturerKey == "google" && d.model.startsWith("Pixel", ignoreCase = true)
    override fun firmware(d: DeviceIdentity) = Firmware("Stock Android", d.display.takeIf { it.isNotBlank() })
    override fun brandName(d: DeviceIdentity) = "Google Pixel"
    override fun limitations(d: DeviceIdentity, services: ServicesInfo) = listOf(
        Limitation(str(R.string.pixel_lim_google_t), str(R.string.pixel_lim_google)),
    )
}

object XiaomiIntegration : OemIntegration() {
    override val id = "xiaomi"
    override val title = "Xiaomi, Redmi, POCO (HyperOS / MIUI)"
    override val compat = CompatLevel.EXPERIMENTAL
    override val packagePrefixes = listOf("com.miui.", "com.xiaomi.", "com.mi.", "com.mipay.", "com.duokan.", "com.milink.",
        "com.lbe.security.miui", "com.facemoji.lite.xiaomi")
    override fun matches(d: DeviceIdentity) = d.manufacturerKey == "xiaomi" || d.brandKey in setOf("xiaomi", "redmi", "poco")
    override fun firmware(d: DeviceIdentity): Firmware? {
        d.prop("ro.mi.os.version.name")?.let { return Firmware("HyperOS", it.removePrefix("OS")) }
        d.prop("ro.miui.ui.version.name")?.let { return Firmware("MIUI", it.removePrefix("V")) }
        return null
    }
    override fun brandName(d: DeviceIdentity) = when (d.brandKey) {
        "redmi" -> "Redmi"
        "poco" -> "POCO"
        else -> "Xiaomi"
    }
    override val developerOptionsSteps get() = listOf(str(R.string.xiaomi_dev_1), str(R.string.xiaomi_dev_2))
    override fun extraSetupSteps(d: DeviceIdentity) = listOf(str(R.string.xiaomi_extra))
    override fun limitations(d: DeviceIdentity, services: ServicesInfo) = listOf(
        Limitation(str(R.string.xiaomi_lim_usb_t), str(R.string.xiaomi_lim_usb)),
        Limitation(str(R.string.lim_updates_t), str(R.string.xiaomi_lim_updates)),
        Limitation(str(R.string.xiaomi_lim_core_t), str(R.string.xiaomi_lim_core)),
    )
}

object HuaweiIntegration : OemIntegration() {
    override val id = "huawei"
    override val title get() = str(R.string.huawei_title)
    override val compat = CompatLevel.EXPERIMENTAL
    override val packagePrefixes = listOf("com.huawei.", "com.hicloud.", "com.vmall.")
    override fun matches(d: DeviceIdentity) = d.manufacturerKey == "huawei" || d.brandKey == "huawei"
    override fun firmware(d: DeviceIdentity): Firmware? {
        if (isHarmony(d)) return Firmware("HarmonyOS", d.prop("hw_sc.build.platform.version"))
        d.prop("ro.build.version.emui")?.let { return Firmware("EMUI", it.substringAfter('_')) }
        return null
    }
    override val disableOftenRefused = true
    override fun limitations(d: DeviceIdentity, services: ServicesInfo) = buildList {
        add(Limitation(str(R.string.lim_disable_refused_t), str(R.string.huawei_lim_disable)))
        if (!services.gms) add(Limitation(str(R.string.lim_no_gms_t), str(R.string.huawei_lim_no_gms)))
        if (isHarmony(d)) add(Limitation("HarmonyOS", str(R.string.huawei_lim_harmony)))
    }
    override fun dynamicProtections(services: ServicesInfo): Map<String, String> =
        if (services.gms) emptyMap() else mapOf(
            "com.huawei.hwid" to str(R.string.huawei_protect_hms),
            "com.huawei.appmarket" to str(R.string.huawei_protect_appgallery),
        )

    fun isHarmony(d: DeviceIdentity) = d.prop("hw_sc.build.platform.version") != null ||
        d.prop(HARMONY_BRAND_PROP).equals("harmony", ignoreCase = true)

    /** 合成プロパティ: リフレクションで読む com.huawei.system.BuildEx.getOsBrand()。 */
    const val HARMONY_BRAND_PROP = "prune.huawei.os_brand"
}

object HonorIntegration : OemIntegration() {
    override val id = "honor"
    override val title = "Honor (MagicOS)"
    override val compat = CompatLevel.EXPERIMENTAL
    override val packagePrefixes = listOf("com.hihonor.", "com.honor.")
    override fun matches(d: DeviceIdentity) = d.manufacturerKey == "honor" || d.brandKey == "honor"
    override fun firmware(d: DeviceIdentity): Firmware? {
        d.prop("ro.build.version.magic")?.let { return Firmware("MagicOS", it.substringAfter('_')) }
        d.prop("ro.build.version.emui")?.let { return Firmware("EMUI", it.substringAfter('_')) }
        return null
    }
    override val disableOftenRefused = true
    override fun limitations(d: DeviceIdentity, services: ServicesInfo) = buildList {
        add(Limitation(str(R.string.lim_disable_refused_t), str(R.string.honor_lim_disable)))
        if (!services.gms) add(Limitation(str(R.string.lim_no_gms_t), str(R.string.honor_lim_no_gms)))
    }
    override fun dynamicProtections(services: ServicesInfo): Map<String, String> =
        if (services.gms) emptyMap() else mapOf("com.hihonor.id" to str(R.string.honor_protect_hms))
}

object OplusIntegration : OemIntegration() {
    override val id = "oplus"
    override val title = "OPPO, OnePlus, realme (ColorOS / OxygenOS / realme UI)"
    override val compat = CompatLevel.EXPERIMENTAL
    override val packagePrefixes = listOf("com.oplus.", "com.coloros.", "com.heytap.", "com.oppo.", "com.nearme.", "com.oneplus.",
        "net.oneplus.", "com.realme.", "com.redteamobile.")
    override fun matches(d: DeviceIdentity) = d.manufacturerKey in setOf("oppo", "oneplus", "realme") || d.brandKey in setOf("oppo", "oneplus", "realme")
    override fun firmware(d: DeviceIdentity): Firmware? {
        val oplus = d.prop("ro.build.version.oplusrom")?.removePrefix("V")
        return when {
            d.brandKey == "realme" || d.manufacturerKey == "realme" ->
                Firmware("realme UI", d.prop("ro.build.version.realmeui")?.removePrefix("V") ?: oplus)
            d.brandKey == "oneplus" || d.manufacturerKey == "oneplus" ->
                Firmware("OxygenOS", d.prop("ro.oxygen.version") ?: oplus)
            else -> Firmware("ColorOS", oplus)
        }
    }
    override fun brandName(d: DeviceIdentity) = when {
        d.brandKey == "oneplus" -> "OnePlus"
        d.brandKey == "realme" -> "realme"
        else -> "OPPO"
    }
    override val developerOptionsSteps get() = listOf(str(R.string.oplus_dev_1), str(R.string.oplus_dev_2))
    override fun extraSetupSteps(d: DeviceIdentity) = listOf(str(R.string.oplus_extra))
    override fun limitations(d: DeviceIdentity, services: ServicesInfo) = listOf(
        Limitation(str(R.string.oplus_lim_monitoring_t), str(R.string.oplus_lim_monitoring)),
        Limitation(str(R.string.oplus_lim_security_t), str(R.string.oplus_lim_security)),
    )
}

object MotorolaIntegration : OemIntegration() {
    override val id = "motorola"
    override val title = "Motorola (My UX)"
    override val compat = CompatLevel.EXPERIMENTAL
    override val packagePrefixes = listOf("com.motorola.", "com.motocare.")
    override fun matches(d: DeviceIdentity) = d.manufacturerKey == "motorola" || d.brandKey == "motorola"
    override fun firmware(d: DeviceIdentity) = Firmware("My UX", null)
    override fun limitations(d: DeviceIdentity, services: ServicesInfo) = listOf(
        Limitation(str(R.string.moto_lim_overlays_t), str(R.string.moto_lim_overlays)),
    )
}

object SonyIntegration : OemIntegration() {
    override val id = "sony"
    override val title = "Sony Xperia"
    override val compat = CompatLevel.GENERIC
    override val packagePrefixes = listOf("com.sonymobile.", "com.sony.")
    override fun matches(d: DeviceIdentity) = d.manufacturerKey == "sony"
    override fun firmware(d: DeviceIdentity) = Firmware("Xperia (Android)", null)
    override fun brandName(d: DeviceIdentity) = "Sony Xperia"
}

object NothingIntegration : OemIntegration() {
    override val id = "nothing"
    override val title = "Nothing (Nothing OS)"
    override val compat = CompatLevel.GENERIC
    override val packagePrefixes = listOf("com.nothing.")
    override fun matches(d: DeviceIdentity) = d.manufacturerKey == "nothing" || d.brandKey == "nothing"
    override fun firmware(d: DeviceIdentity) = Firmware("Nothing OS", d.prop("ro.build.nothing.version") ?: d.prop("ro.nothing.version"))
}

object AsusIntegration : OemIntegration() {
    override val id = "asus"
    override val title = "ASUS (ZenUI / ROG UI)"
    override val compat = CompatLevel.GENERIC
    override val packagePrefixes = listOf("com.asus.")
    override fun matches(d: DeviceIdentity) = d.manufacturerKey == "asus"
    override fun brandName(d: DeviceIdentity) = "ASUS"
}

object VivoIntegration : OemIntegration() {
    override val id = "vivo"
    override val title = "vivo, iQOO (Funtouch OS / OriginOS)"
    override val compat = CompatLevel.GENERIC
    override val packagePrefixes = listOf("com.vivo.", "com.bbk.", "com.iqoo.")
    override fun matches(d: DeviceIdentity) = d.manufacturerKey == "vivo" || d.brandKey in setOf("vivo", "iqoo")
    override fun firmware(d: DeviceIdentity): Firmware? {
        val name = d.prop("ro.vivo.os.name") ?: return null
        val pretty = when {
            name.contains("origin", true) -> "OriginOS"
            name.contains("funtouch", true) -> "Funtouch OS"
            else -> name
        }
        return Firmware(pretty, d.prop("ro.vivo.os.version"))
    }
}

object TranssionIntegration : OemIntegration() {
    override val id = "transsion"
    override val title = "Tecno, Infinix, itel (HiOS / XOS / itel OS)"
    override val compat = CompatLevel.GENERIC
    override val packagePrefixes = listOf("com.transsion.", "com.itel.", "com.tecno.", "com.infinix.", "com.afmobi.")
    override fun matches(d: DeviceIdentity) = d.brandKey in setOf("tecno", "infinix", "itel") ||
        d.manufacturerKey.contains("tecno") || d.manufacturerKey.contains("infinix") || d.manufacturerKey == "itel"
    override fun firmware(d: DeviceIdentity): Firmware {
        val name = when {
            d.brandKey == "infinix" || d.manufacturerKey.contains("infinix") -> "XOS"
            d.brandKey == "itel" -> "itel OS"
            else -> "HiOS"
        }
        return Firmware(name, d.prop("ro.tranos.version"))
    }
}

object HmdIntegration : OemIntegration() {
    override val id = "hmd"
    override val title = "Nokia / HMD"
    override val compat = CompatLevel.GENERIC
    override val packagePrefixes = listOf("com.hmdglobal.", "com.evenwell.", "com.nokia.", "com.fih.")
    override fun matches(d: DeviceIdentity) = d.manufacturerKey.contains("hmd") || d.brandKey == "nokia" || d.brandKey == "hmd"
}

object LenovoIntegration : OemIntegration() {
    override val id = "lenovo"
    override val title = "Lenovo"
    override val compat = CompatLevel.GENERIC
    override val packagePrefixes = listOf("com.lenovo.", "com.zui.")
    override fun matches(d: DeviceIdentity) = d.manufacturerKey == "lenovo"
}

object ZteIntegration : OemIntegration() {
    override val id = "zte"
    override val title = "ZTE, nubia"
    override val compat = CompatLevel.GENERIC
    override val packagePrefixes = listOf("com.zte.", "cn.nubia.", "com.nubia.")
    override fun matches(d: DeviceIdentity) = d.manufacturerKey in setOf("zte", "nubia")
}

object LgIntegration : OemIntegration() {
    override val id = "lg"
    override val title = "LG"
    override val compat = CompatLevel.GENERIC
    override val packagePrefixes = listOf("com.lge.")
    override fun matches(d: DeviceIdentity) = d.manufacturerKey == "lge" || d.manufacturerKey == "lg"
    override fun brandName(d: DeviceIdentity) = "LG"
}

/** その他すべての Android 端末: 汎用ルールとコミュニティデータのみ。 */
object GenericAndroidIntegration : OemIntegration() {
    override val id = "generic"
    override val title get() = str(R.string.generic_title)
    override val compat = CompatLevel.GENERIC
    override fun matches(d: DeviceIdentity) = true
    override fun firmware(d: DeviceIdentity) = Firmware("Android", d.release.takeIf { it.isNotBlank() })
    override val developerOptionsSteps get() = listOf(str(R.string.generic_dev_1), str(R.string.generic_dev_2))
}

/** Android エミュレータ: Prune の自動テストを実行する環境。 */
object EmulatorIntegration : OemIntegration() {
    override val id = "emulator"
    override val title get() = str(R.string.emulator_title)
    override val compat = CompatLevel.TESTED
    override fun matches(d: DeviceIdentity) = d.isEmulator
    override fun firmware(d: DeviceIdentity) = Firmware("Android emulator", d.release)
    override fun brandName(d: DeviceIdentity) = str(R.string.emulator_title)
}
