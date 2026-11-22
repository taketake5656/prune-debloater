package io.github.cyb3rl00m.prune.core.db

import io.github.cyb3rl00m.prune.R
import io.github.cyb3rl00m.prune.core.i18n.str
import io.github.cyb3rl00m.prune.core.model.Classification
import io.github.cyb3rl00m.prune.core.model.PackageFacts
import io.github.cyb3rl00m.prune.core.model.RecommendedAction
import io.github.cyb3rl00m.prune.core.model.Risk
import io.github.cyb3rl00m.prune.core.model.Source
import io.github.cyb3rl00m.prune.core.model.Warning
import io.github.cyb3rl00m.prune.core.oem.OemRegistry

/**
 * この端末の現在の設定によって保護されるパッケージ (既定のアプリ、使用中のキーボード、デバイス管理アプリ、
 * Google サービスの有無など)。スキャンのたびに読み直す。
 */
data class DynamicProtections(
    /** パッケージ -> 変更してはならない理由 */
    val locked: Map<String, String> = emptyMap(),
    /** パッケージ -> 使用中である旨の警告 (変更は可能) */
    val inUse: Map<String, String> = emptyMap(),
)

/**
 * 1 つのパッケージの危険度・分類・レベル・推奨操作を決める。メーカーに依存しない。メーカー固有の知識は
 * ルールファイルと [OemRegistry] から得る。
 *
 * 優先順位 (ロックについては最初に一致したものが勝ち、後の段階は判定を細かくするだけ):
 *  1. Prune のロックリスト (core と全メーカーのファイル)。
 *  2. 動的保護 (この端末のランチャー、電話アプリ、SMS アプリ、キーボード、デバイス管理アプリ、ユーザー補助サービス、
 *     Google サービスのない端末が依存するサービスフレームワーク)。
 *  3. 常駐 (persistent) システムアプリ (Android が常に動かし続けるもの)。
 *  4. Prune のキュレーション済みエントリ (この Android バージョンとファームウェアに当てはまる場合)、次にプレフィックスエントリ。
 *  5. コミュニティデータ (UAD-ng の段階)。保守的に対応付ける。
 *  6. 不明なシステムパッケージ: 危険度 UNKNOWN。Custom モードでのみ表示し、事前選択しない。
 */
class Classifier(private val db: PackageDatabase, private val device: DeviceContext = DeviceContext()) {

    /** 例外を投げない。Prune が解析できないパッケージは不明として扱う。 */
    fun classify(facts: PackageFacts, dynamic: DynamicProtections = DynamicProtections()): Classification =
        runCatching { classifyUnsafe(facts, dynamic) }.getOrElse {
            Classification(
                source = if (facts.isSystem) Source.OTHER_SYSTEM else Source.USER, risk = Risk.UNKNOWN,
                level = if (facts.isSystem) 4 else null, description = str(R.string.cls_could_not_analyse),
                notes = emptyList(), groups = emptySet(), warnings = emptyList(), lockedReason = null,
                recommendedAction = RecommendedAction.REVIEW, inDatabase = false, recovery = recoveryFor(facts),
            )
        }

    private fun classifyUnsafe(facts: PackageFacts, dynamic: DynamicProtections): Classification {
        val pkg = facts.packageName
        val uad = db.uad[pkg]
        val sourced = db.curated(pkg, device)
        val curated = sourced?.entry
        val prefix = if (curated == null) db.prefixEntry(pkg) else null
        val oem = OemRegistry.manufacturerOf(pkg)
        val preloadInstaller = facts.installer?.let { inst -> PRELOAD_INSTALLERS.any { inst == it || inst.startsWith("$it.") } } == true
        val source = sourceOf(facts, uad, curated, oem != null, preloadInstaller)
        val manufacturer = when (source) {
            Source.OEM -> oem?.let { manufacturerName(it.id) } ?: sourced?.manufacturer?.takeIf { it != "Generic Android" }
            else -> null
        }
        val ecosystems = db.ecosystemsOf(pkg)
        val features = db.featureTagsOf(pkg)
        val groups = (ecosystems + features).map { it.key }.toSet()
        val warnings = (ecosystems + features).map { Warning(it.key, it.name, it.warning) }.toMutableList()
        val notes = mutableListOf<String>()
        val description = uad?.description?.takeIf { it.isNotBlank() } ?: curated?.note ?: defaultDescription(facts, source, manufacturer)
        if (curated?.note != null && uad?.description?.isNotBlank() == true) notes += curated.note
        prefix?.note?.let { if (uad == null) notes += it }
        if (preloadInstaller) notes += str(R.string.cls_note_preload_installer, facts.installer!!)
        val regional = REGIONAL.containsMatchIn(pkg)
        if (regional) notes += str(R.string.cls_note_regional)

        dynamic.inUse[pkg]?.let { warnings += Warning("IN_USE", str(R.string.cls_in_use_title), it) }
        val recovery = curated?.recovery ?: recoveryFor(facts)

        fun locked(reason: String) = Classification(
            source = source, risk = Risk.LOCKED, level = null, description = description, notes = notes,
            groups = groups, warnings = warnings, lockedReason = reason, recommendedAction = RecommendedAction.KEEP,
            inDatabase = uad != null || curated != null, dependencies = uad?.dependencies.orEmpty(),
            neededBy = uad?.neededBy.orEmpty(), communityTier = uad?.removal?.let(::tierName),
            manufacturer = manufacturer, recovery = recovery, regional = regional,
        )

        db.lockedReason(pkg)?.let { return locked(it) }
        dynamic.locked[pkg]?.let { return locked(it) }
        if (facts.isSystem && facts.isPersistent) {
            return locked(str(R.string.cls_locked_persistent))
        }

        var risk: Risk
        var level: Int?
        when {
            curated != null -> {
                risk = Risk.valueOf(curated.risk)
                level = curated.level
            }
            prefix != null -> {
                risk = Risk.valueOf(prefix.risk)
                level = prefix.level
            }
            uad != null -> {
                val mapped = mapCommunityTier(uad) ?: return locked(str(R.string.cls_locked_community_unsafe))
                risk = mapped.first
                level = mapped.second
            }
            !facts.isSystem -> {
                risk = Risk.SAFE
                level = if (preloadInstaller) 1 else null
            }
            else -> {
                risk = Risk.UNKNOWN
                level = 4
                notes += str(R.string.cls_note_unknown)
            }
        }

        // system/phone の ID を共有するパッケージは、Prune がキュレーションしていない限り最低でも HIGH にする。
        if (curated == null && prefix == null && facts.sharedUserId in PRIVILEGED_SHARED_UIDS) {
            if (risk.ordinal < Risk.HIGH.ordinal) risk = Risk.HIGH
            level = 4
            notes += str(R.string.cls_note_system_privileges, facts.sharedUserId!!)
        }
        // キュレーション済みエントリがない場合、エコシステムに属していれば危険度の下限を MODERATE に引き上げる。
        if (curated == null && ecosystems.isNotEmpty() && risk.ordinal < Risk.MODERATE.ordinal) risk = Risk.MODERATE
        if (risk == Risk.HIGH || risk == Risk.UNKNOWN) level = 4

        val recommended = when {
            !facts.isSystem -> RecommendedAction.UNINSTALL_APP.takeIf { source == Source.PRELOAD } ?: RecommendedAction.KEEP
            risk == Risk.HIGH || risk == Risk.UNKNOWN -> RecommendedAction.REVIEW
            level != null && level <= 3 -> RecommendedAction.DISABLE
            else -> RecommendedAction.REVIEW
        }
        if (!facts.isSystem) level = level?.takeIf { source == Source.PRELOAD }

        return Classification(
            source = source, risk = risk, level = level, description = description, notes = notes, groups = groups,
            warnings = warnings, lockedReason = null, recommendedAction = recommended,
            inDatabase = uad != null || curated != null || prefix != null,
            dependencies = uad?.dependencies.orEmpty(), neededBy = uad?.neededBy.orEmpty(),
            communityTier = uad?.removal?.let(::tierName), manufacturer = manufacturer,
            consequences = curated?.consequences, recovery = recovery, regional = regional,
        )
    }

    /** コミュニティの段階を (危険度, レベル) に対応付ける。"unsafe" の場合は null。意図的に保守的にしている。 */
    private fun mapCommunityTier(e: UadEntry): Pair<Risk, Int>? {
        val thirdParty = e.list == "c" || e.list == "m"
        return when (e.removal) {
            "r" -> if (thirdParty) Risk.LOW to 1 else Risk.MODERATE to 3
            "a" -> if (thirdParty) Risk.MODERATE to 3 else Risk.MODERATE to 4
            "e" -> Risk.HIGH to 4
            else -> null
        }
    }

    private fun sourceOf(facts: PackageFacts, uad: UadEntry?, curated: CuratedEntry?, oemPrefix: Boolean,
                         preloadInstaller: Boolean): Source {
        val pkg = facts.packageName
        if (!facts.isSystem) {
            // 既知のプリロードを更新したもの・ユーザーが入れ直したものもプリロードとして扱う。
            return if (preloadInstaller || uad?.list == "m" || uad?.list == "c" ||
                (curated != null && !curated.google && !oemPrefix)) Source.PRELOAD
            else Source.USER
        }
        if (curated?.google == true) return Source.GOOGLE
        when (uad?.list) {
            "o" -> return if (pkg.startsWith("com.google.")) Source.GOOGLE else Source.OEM
            "g" -> return Source.GOOGLE
            "a" -> return Source.ANDROID
            "c" -> return Source.CARRIER
            "m" -> return Source.PRELOAD
        }
        return when {
            oemPrefix && !pkg.startsWith("com.google.") -> Source.OEM
            pkg.startsWith("com.google.") || pkg == "com.android.vending" || pkg == "com.android.chrome" -> Source.GOOGLE
            pkg == "android" || pkg.startsWith("com.android.") -> Source.ANDROID
            CARRIER_PREFIXES.any { pkg.startsWith(it) } -> Source.CARRIER
            curated != null -> Source.PRELOAD
            else -> Source.OTHER_SYSTEM
        }
    }

    private fun defaultDescription(facts: PackageFacts, source: Source, manufacturer: String?): String = when {
        !facts.isSystem && source == Source.PRELOAD -> str(R.string.cls_desc_preload_user)
        !facts.isSystem -> str(R.string.cls_desc_user)
        source == Source.OEM -> str(R.string.cls_desc_oem, manufacturer ?: str(R.string.source_oem))
        source == Source.GOOGLE -> str(R.string.cls_desc_google)
        source == Source.CARRIER -> str(R.string.cls_desc_carrier)
        else -> str(R.string.cls_desc_system)
    }

    private fun recoveryFor(facts: PackageFacts): String = if (facts.isSystem) {
        str(R.string.cls_recovery_system, facts.packageName)
    } else {
        str(R.string.cls_recovery_user)
    }

    private fun tierName(code: String) = when (code) {
        "r" -> str(R.string.tier_recommended)
        "a" -> str(R.string.tier_advanced)
        "e" -> str(R.string.tier_expert)
        "u" -> str(R.string.tier_unsafe)
        else -> code
    }

    companion object {
        val PRIVILEGED_SHARED_UIDS = setOf(
            "android.uid.system", "android.uid.phone", "android.uid.systemui", "android.uid.nfc",
            "android.uid.bluetooth", "android.uid.shell", "android.uid.networkstack", "android.uid.se",
            "android.media", "android.uid.log",
        )

        /** 初期設定時にパートナーアプリを配置するインストーラ (ユーザーが選んだアプリストアではないもの)。 */
        val PRELOAD_INSTALLERS = listOf(
            "com.facebook.system", "com.aura.oobe.samsung", "com.aura.oobe.samsung.gl", "com.ironsource.appcloud.oobe",
            "com.digitalturbine.ignite", "com.dti", "com.miui.msa.global", "com.android.preinstall",
        )

        private val REGIONAL = Regex("""(\.|_)(global|india|in|cn|china|eu|eea|indonesia|jp|kr|br|latam|mx|ru|tr|vn|th)$""",
            RegexOption.IGNORE_CASE)

        val CARRIER_PREFIXES = listOf(
            "com.vzw", "com.verizon", "com.motricity.verizon", "com.tmobile", "com.tmo", "com.t-mobile", "com.att.",
            "com.sprint", "com.telekom", "de.telekom", "com.vodafone", "uk.co.ee", "com.orange", "com.telefonica",
            "es.movistar", "com.movistar", "com.bouyguestelecom", "fr.free", "com.sfr", "com.telstra", "com.optus",
            "com.rogers", "ca.bell", "com.telus", "com.uscc", "com.cricketwireless", "com.metropcs", "com.boost",
            "com.dish", "com.playphone", "com.dti.", "com.ironsource.appcloud", "com.aura.oobe", "com.lguplus",
            "com.sktelecom", "com.kt.", "com.plus.", "pl.plus", "pl.play", "com.orange.pl", "com.wind", "it.tim",
            "com.three", "com.h3g", "com.drei", "com.a1", "com.mtel", "bg.vivacom", "com.yettel",
        )

        /** [OemRegistry] のメーカーの表示名。 */
        fun manufacturerName(id: String): String = when (id) {
            "samsung" -> "Samsung"
            "pixel" -> "Google Pixel"
            "xiaomi" -> "Xiaomi"
            "huawei" -> "Huawei"
            "honor" -> "Honor"
            "oplus" -> "OPPO / OnePlus / realme"
            "motorola" -> "Motorola"
            "sony" -> "Sony"
            "nothing" -> "Nothing"
            "asus" -> "ASUS"
            "vivo" -> "vivo"
            "transsion" -> "Tecno / Infinix / itel"
            "hmd" -> "Nokia / HMD"
            "lenovo" -> "Lenovo"
            "zte" -> "ZTE / nubia"
            "lg" -> "LG"
            else -> id
        }
    }
}
