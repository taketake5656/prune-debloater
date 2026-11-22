package io.github.cyb3rl00m.prune.core.oem

import android.os.Build
import java.util.concurrent.TimeUnit

/**
 * Prune が知る端末の識別情報すべて: Build のフィールドとシステムプロパティ。
 * 純粋なデータなので、メーカー検出をモック端末でユニットテストできる。
 */
data class DeviceIdentity(
    val manufacturer: String,
    val brand: String,
    val model: String,
    val device: String,
    val product: String,
    val fingerprint: String,
    val sdkInt: Int,
    val release: String,
    val display: String,
    /** `getprop` が出力するとおりのシステムプロパティ。 */
    val props: Map<String, String> = emptyMap(),
) {
    val manufacturerKey: String get() = manufacturer.trim().lowercase()
    val brandKey: String get() = brand.trim().lowercase()

    fun prop(name: String): String? = props[name]?.trim()?.takeIf { it.isNotEmpty() }

    /** Android エミュレータなら true (OEM ファームウェアなし。Prune の自動テストで使う)。 */
    val isEmulator: Boolean
        get() = prop("ro.kernel.qemu") == "1" || prop("ro.boot.qemu") == "1" ||
            fingerprint.startsWith("generic") || fingerprint.contains("/sdk_g") ||
            model.startsWith("sdk_g") || product.startsWith("sdk_g") || model.contains("Emulator")

    companion object {
        /** Build と全システムプロパティを 1 回の `getprop` 呼び出しで読む (非公開 API は使わない)。 */
        fun read(): DeviceIdentity = DeviceIdentity(
            manufacturer = Build.MANUFACTURER.orEmpty(),
            brand = Build.BRAND.orEmpty(),
            model = Build.MODEL.orEmpty(),
            device = Build.DEVICE.orEmpty(),
            product = Build.PRODUCT.orEmpty(),
            fingerprint = Build.FINGERPRINT.orEmpty(),
            sdkInt = Build.VERSION.SDK_INT,
            release = Build.VERSION.RELEASE.orEmpty(),
            display = Build.DISPLAY.orEmpty(),
            props = readProps() + synthetic(),
        )

        /** メーカー独自クラス経由でしか取れない値。検出を純粋に保つため、プロパティとして公開する。 */
        private fun synthetic(): Map<String, String> = buildMap {
            runCatching { Build.VERSION::class.java.getField("SEM_PLATFORM_INT").getInt(null) }
                .getOrNull()?.let { put(SamsungIntegration.SEM_PLATFORM_PROP, it.toString()) }
            runCatching {
                Class.forName("com.huawei.system.BuildEx").getMethod("getOsBrand").invoke(null) as? String
            }.getOrNull()?.let { put(HuaweiIntegration.HARMONY_BRAND_PROP, it) }
        }

        private val PROP_LINE = Regex("""^\[([^\]]+)]: \[(.*)]$""")

        fun parseGetprop(output: String): Map<String, String> = output.lineSequence()
            .mapNotNull { PROP_LINE.find(it.trim()) }
            .associate { it.groupValues[1] to it.groupValues[2] }

        private fun readProps(): Map<String, String> = runCatching {
            val p = ProcessBuilder("/system/bin/getprop").redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().readText()
            if (!p.waitFor(3, TimeUnit.SECONDS)) p.destroy()
            parseGetprop(out)
        }.getOrDefault(emptyMap())
    }
}
