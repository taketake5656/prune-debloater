package io.github.cyb3rl00m.prune.core.oem

import androidx.annotation.StringRes
import io.github.cyb3rl00m.prune.R
import io.github.cyb3rl00m.prune.core.i18n.str

/** Prune がその端末をどの程度サポートしているか。互換性レポートに表示する。決して誇張しない。 */
enum class CompatLevel(@StringRes private val labelRes: Int, @StringRes private val meaningRes: Int) {
    TESTED(R.string.compat_tested, R.string.compat_tested_meaning),
    PARTIAL(R.string.compat_partial, R.string.compat_partial_meaning),
    EXPERIMENTAL(R.string.compat_experimental, R.string.compat_experimental_meaning),
    GENERIC(R.string.compat_generic, R.string.compat_generic_meaning),
    UNSUPPORTED(R.string.compat_unsupported, R.string.compat_unsupported_meaning);

    val label: String get() = str(labelRes)
    val meaning: String get() = str(meaningRes)
}

data class Firmware(val name: String, val version: String?) {
    override fun toString() = if (version.isNullOrBlank()) name else "$name $version"
}

data class Limitation(val title: String, val text: String)

/** Google / Huawei のサービスフレームワークのうち、インストール済みで有効なもの。ブランドとは独立に検出する。 */
data class ServicesInfo(val gms: Boolean, val playStore: Boolean, val hms: Boolean, val honorServices: Boolean = false)

/**
 * メーカーごとに異なるものすべて。統合は知識を追加するだけで、debloat エンジン自体はメーカーに依存しない。
 * [GenericAndroidIntegration] はすべての Android 端末で動く。
 */
abstract class OemIntegration {
    abstract val id: String
    /** ユーザーに表示する名前。例: "Xiaomi (HyperOS / MIUI)"。 */
    abstract val title: String
    abstract val compat: CompatLevel
    open val compatNote: String get() = compat.meaning
    /** このメーカーに属するパッケージのプレフィックス (どの端末でもパッケージのラベル付けに使う)。 */
    open val packagePrefixes: List<String> = emptyList()

    abstract fun matches(d: DeviceIdentity): Boolean
    open fun firmware(d: DeviceIdentity): Firmware? = null
    /** ホーム画面に表示するブランド (例: Xiaomi 製の端末なら Redmi)。 */
    open fun brandName(d: DeviceIdentity): String = d.brand.replaceFirstChar { it.uppercase() }

    open val developerOptionsSteps: List<String> get() = listOf(str(R.string.default_dev_steps))
    /** ADB レベルのコマンドを使う前に、一部のファームウェアで一度だけ必要な追加の設定。 */
    open fun extraSetupSteps(d: DeviceIdentity): List<String> = emptyList()
    open fun limitations(d: DeviceIdentity, services: ServicesInfo): List<Limitation> = emptyList()
    /** インストール済みのサービスフレームワークを踏まえて、このファームウェアが必要とするパッケージ。 */
    open fun dynamicProtections(services: ServicesInfo): Map<String, String> = emptyMap()
    /** 多くのシステムアプリで `pm disable-user` を拒否することが知られているファームウェアなら true。 */
    open val disableOftenRefused: Boolean = false
}

/** 端末に合う統合を選ぶ。例外を投げない。不明な端末には汎用の統合を返す。 */
object OemRegistry {
    val integrations: List<OemIntegration> = listOf(
        SamsungIntegration, GooglePixelIntegration, XiaomiIntegration, HuaweiIntegration, HonorIntegration,
        OplusIntegration, MotorolaIntegration, SonyIntegration, NothingIntegration, AsusIntegration, VivoIntegration,
        TranssionIntegration, HmdIntegration, LenovoIntegration, ZteIntegration, LgIntegration,
    )

    fun detect(d: DeviceIdentity): OemIntegration = runCatching {
        if (d.isEmulator) EmulatorIntegration
        else integrations.firstOrNull { runCatching { it.matches(d) }.getOrDefault(false) } ?: GenericAndroidIntegration
    }.getOrDefault(GenericAndroidIntegration)

    private val prefixIndex: List<Pair<String, OemIntegration>> by lazy {
        integrations.flatMap { i -> i.packagePrefixes.map { it to i } }.sortedByDescending { it.first.length }
    }

    /** パッケージ名から判断した所属メーカー。既知のメーカーのパッケージでなければ null。 */
    fun manufacturerOf(pkg: String): OemIntegration? = prefixIndex.firstOrNull { pkg.startsWith(it.first) }?.second

    fun firmwareSafe(i: OemIntegration, d: DeviceIdentity): Firmware? = runCatching { i.firmware(d) }.getOrNull()
    fun limitationsSafe(i: OemIntegration, d: DeviceIdentity, s: ServicesInfo): List<Limitation> =
        runCatching { i.limitations(d, s) }.getOrDefault(emptyList())
}
