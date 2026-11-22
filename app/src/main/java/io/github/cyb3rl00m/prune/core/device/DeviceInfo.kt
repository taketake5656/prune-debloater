package io.github.cyb3rl00m.prune.core.device

import android.app.ActivityManager
import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.telephony.TelephonyManager
import io.github.cyb3rl00m.prune.core.oem.CompatLevel
import io.github.cyb3rl00m.prune.core.oem.DeviceIdentity
import io.github.cyb3rl00m.prune.core.oem.Firmware
import io.github.cyb3rl00m.prune.core.oem.OemIntegration
import io.github.cyb3rl00m.prune.core.oem.OemRegistry
import io.github.cyb3rl00m.prune.core.oem.SamsungIntegration
import io.github.cyb3rl00m.prune.core.oem.ServicesInfo

data class DeviceInfo(
    val identity: DeviceIdentity,
    val integration: OemIntegration,
    val brandName: String,
    val manufacturer: String,
    val brand: String,
    val model: String,
    val marketName: String,
    val device: String,
    val androidVersion: String,
    val sdkInt: Int,
    val firmware: Firmware?,
    val buildNumber: String,
    val securityPatch: String,
    val cscCode: String?,
    val cscCountry: String?,
    val carrier: String?,
    val services: ServicesInfo,
    val totalRamBytes: Long,
    val storageTotalBytes: Long,
    val storageFreeBytes: Long,
    val userId: Int,
) {
    val isSamsung: Boolean get() = integration == SamsungIntegration
    val isEmulator: Boolean get() = identity.isEmulator
    val compat: CompatLevel get() = integration.compat
    val displayName: String get() = if (marketName != model) "$marketName ($model)" else model
    /** 例: "Samsung Galaxy S25 FE"、"Redmi Note 13"、"Pixel 9"、"Motorola moto g84" など。 */
    val fullName: String get() = when {
        marketName.startsWith(brandName, ignoreCase = true) -> marketName
        isSamsung && marketName.startsWith("Galaxy") -> "Samsung $marketName"
        marketName.contains(brandName.substringAfterLast(' '), ignoreCase = true) -> marketName
        else -> "$brandName $marketName"
    }
}

object DeviceInfoReader {

    fun read(context: Context, identity: DeviceIdentity = DeviceIdentity.read()): DeviceInfo {
        val integration = OemRegistry.detect(identity)
        val am = context.getSystemService(ActivityManager::class.java)
        val mem = ActivityManager.MemoryInfo().also { runCatching { am?.getMemoryInfo(it) } }
        val stat = runCatching { StatFs(Environment.getDataDirectory().path) }.getOrNull()
        val tm = context.getSystemService(TelephonyManager::class.java)
        val carrier = runCatching { tm?.simOperatorName?.takeIf { it.isNotBlank() } ?: tm?.networkOperatorName }
            .getOrNull()?.takeIf { it.isNotBlank() }
        val marketProp = listOf("ro.product.marketname", "ro.product.vendor.marketname", "ro.product.system.marketname",
            "ro.product.odm.marketname", "ro.config.marketing_name")
            .firstNotNullOfOrNull { identity.prop(it) }
        return DeviceInfo(
            identity = identity,
            integration = integration,
            brandName = runCatching { integration.brandName(identity) }.getOrDefault(identity.brand),
            manufacturer = identity.manufacturer,
            brand = identity.brand,
            model = identity.model,
            marketName = marketProp ?: SamsungModels.marketName(identity.model) ?: identity.model,
            device = identity.device,
            androidVersion = identity.release,
            sdkInt = identity.sdkInt,
            firmware = OemRegistry.firmwareSafe(integration, identity),
            buildNumber = identity.display,
            securityPatch = android.os.Build.VERSION.SECURITY_PATCH.orEmpty(),
            cscCode = if (integration == SamsungIntegration) identity.prop("ro.csc.sales_code") ?: identity.prop("ro.boot.sales_code") else null,
            cscCountry = if (integration == SamsungIntegration) identity.prop("ro.csc.country_code") else null,
            carrier = carrier,
            services = services(context),
            totalRamBytes = mem.totalMem,
            storageTotalBytes = stat?.totalBytes ?: 0,
            storageFreeBytes = stat?.availableBytes ?: 0,
            userId = android.os.Process.myUid() / 100_000,
        )
    }

    /** Google と Huawei/Honor のサービスフレームワーク。インストール済みで有効なパッケージから検出する (Play Services API は使わない)。 */
    fun services(context: Context): ServicesInfo {
        val pm = context.packageManager
        fun enabled(pkg: String) = runCatching {
            @Suppress("DEPRECATION")
            pm.getApplicationInfo(pkg, 0).enabled
        }.getOrDefault(false)
        return ServicesInfo(
            gms = enabled("com.google.android.gms"),
            playStore = enabled("com.android.vending"),
            hms = enabled("com.huawei.hwid"),
            honorServices = enabled("com.hihonor.id"),
        )
    }
}

/** One UI のバージョンの解読。純粋関数で、ユニットテスト対象。 */
object OneUi {
    /** ro.build.version.oneui = MMmmpp。例: 60101 = One UI 6.1.1、80000 = One UI 8.0。 */
    fun fromProperty(raw: String?): String? {
        val v = raw?.trim()?.toIntOrNull() ?: return null
        if (v < 10000) return null
        val major = v / 10000
        val minor = (v / 100) % 100
        val patch = v % 100
        return if (patch == 0) "$major.$minor" else "$major.$minor.$patch"
    }

    /** SEM_PLATFORM_INT: 100000 = One UI 1.0、110500 = 2.5、130000 = 4.0 (One UI = (x - 90000) / 10000)。 */
    fun fromSemPlatformInt(sem: Int?): String? {
        if (sem == null || sem < 100000) return null
        val v = sem - 90000
        val major = v / 10000
        val minor = (v % 10000) / 100
        return "$major.$minor"
    }
}

/** ファームウェアが販売名を報告しない Samsung の型番に対する販売名。 */
object SamsungModels {
    private val names = linkedMapOf(
        "SM-S731" to "Galaxy S25 FE", "SM-S937" to "Galaxy S25 Edge", "SM-S938" to "Galaxy S25 Ultra",
        "SM-S936" to "Galaxy S25+", "SM-S931" to "Galaxy S25", "SM-S721" to "Galaxy S24 FE",
        "SM-S928" to "Galaxy S24 Ultra", "SM-S926" to "Galaxy S24+", "SM-S921" to "Galaxy S24",
        "SM-S711" to "Galaxy S23 FE", "SM-S918" to "Galaxy S23 Ultra", "SM-S916" to "Galaxy S23+",
        "SM-S911" to "Galaxy S23", "SM-S908" to "Galaxy S22 Ultra", "SM-S906" to "Galaxy S22+",
        "SM-S901" to "Galaxy S22", "SM-G990" to "Galaxy S21 FE", "SM-F966" to "Galaxy Z Fold7",
        "SM-F766" to "Galaxy Z Flip7", "SM-F761" to "Galaxy Z Flip7 FE", "SM-F956" to "Galaxy Z Fold6",
        "SM-F741" to "Galaxy Z Flip6", "SM-F946" to "Galaxy Z Fold5", "SM-F731" to "Galaxy Z Flip5",
        "SM-A576" to "Galaxy A57", "SM-A566" to "Galaxy A56", "SM-A556" to "Galaxy A55", "SM-A546" to "Galaxy A54",
        "SM-A376" to "Galaxy A37", "SM-A366" to "Galaxy A36", "SM-A356" to "Galaxy A35", "SM-A346" to "Galaxy A34",
        "SM-A266" to "Galaxy A26", "SM-A256" to "Galaxy A25", "SM-A176" to "Galaxy A17", "SM-A166" to "Galaxy A16",
        "SM-A165" to "Galaxy A16", "SM-M366" to "Galaxy M36", "SM-M566" to "Galaxy M56", "SM-X" to "Galaxy Tab",
    )

    fun marketName(model: String): String? =
        names.entries.firstOrNull { model.uppercase().startsWith(it.key) }?.value
}
