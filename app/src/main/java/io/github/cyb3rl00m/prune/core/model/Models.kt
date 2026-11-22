package io.github.cyb3rl00m.prune.core.model

import androidx.annotation.StringRes
import io.github.cyb3rl00m.prune.R
import io.github.cyb3rl00m.prune.core.i18n.str
import kotlinx.serialization.Serializable

/** パッケージを無効化・削除する際の危険度。LOCKED のパッケージは Prune が決して変更しない。 */
enum class Risk(@StringRes private val labelRes: Int, @StringRes private val meaningRes: Int) {
    SAFE(R.string.risk_safe, R.string.risk_safe_meaning),
    LOW(R.string.risk_low, R.string.risk_low_meaning),
    MODERATE(R.string.risk_moderate, R.string.risk_moderate_meaning),
    UNKNOWN(R.string.risk_unknown, R.string.risk_unknown_meaning),
    HIGH(R.string.risk_high, R.string.risk_high_meaning),
    LOCKED(R.string.risk_locked, R.string.risk_locked_meaning);

    val label: String get() = str(labelRes)
    val meaning: String get() = str(meaningRes)
}

/** パッケージの出どころ。 */
enum class Source(@StringRes private val labelRes: Int) {
    /** 端末メーカー製。どのメーカーかは [Classification.manufacturer] に入る。 */
    OEM(R.string.source_oem),
    GOOGLE(R.string.source_google),
    CARRIER(R.string.source_carrier),
    PRELOAD(R.string.source_preload),
    ANDROID(R.string.source_android),
    OTHER_SYSTEM(R.string.source_other_system),
    USER(R.string.source_user);

    val label: String get() = str(labelRes)
}

/** 現在の Android ユーザーにおけるパッケージの状態。 */
enum class PackageStatus(@StringRes private val labelRes: Int) {
    ENABLED(R.string.status_enabled),
    DISABLED(R.string.status_disabled),
    REMOVED(R.string.status_removed);

    val label: String get() = str(labelRes)
}

/** パッケージに対して Prune が推奨する操作。 */
enum class RecommendedAction(@StringRes private val labelRes: Int) {
    KEEP(R.string.action_keep),
    DISABLE(R.string.action_disable),
    REVIEW(R.string.action_review),
    UNINSTALL_APP(R.string.action_uninstall_app);

    val label: String get() = str(labelRes)
}

/** システムパッケージを使えなくする方法。どちらも元に戻せる。 */
enum class RemovalMethod(@StringRes private val labelRes: Int, @StringRes private val explanationRes: Int) {
    DISABLE(R.string.method_disable, R.string.method_disable_explanation),
    UNINSTALL_USER(R.string.method_uninstall_user, R.string.method_uninstall_user_explanation);

    val label: String get() = str(labelRes)
    val explanation: String get() = str(explanationRes)
}

/** PackageManager から読み取った、インストール済みパッケージの事実情報。ユニットテストできるよう純粋なデータにしている。 */
data class PackageFacts(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
    val isUpdatedSystem: Boolean,
    val isPersistent: Boolean,
    val sharedUserId: String?,
    val installedForUser: Boolean,
    val enabled: Boolean,
    val versionName: String?,
    val uid: Int,
    val hasLauncherActivity: Boolean = false,
    /** このアプリをインストールしたパッケージ (Play ストア、メーカーストア、プリロード用インストーラ)。Android が報告する場合のみ。 */
    val installer: String? = null,
) {
    val status: PackageStatus
        get() = when {
            !installedForUser -> PackageStatus.REMOVED
            !enabled -> PackageStatus.DISABLED
            else -> PackageStatus.ENABLED
        }
}

/** パッケージに付く警告。例:「Samsung Wallet が動かなくなる」。 */
data class Warning(val groupKey: String, val title: String, val text: String)

/** 1 つのパッケージに対する Prune の最終判定。 */
data class Classification(
    val source: Source,
    val risk: Risk,
    /** このパッケージを選択しうる最も低い debloat レベル (1..4)。どのプロファイルでも選択しない場合は null。 */
    val level: Int?,
    val description: String,
    val notes: List<String>,
    val groups: Set<String>,
    val warnings: List<Warning>,
    val lockedReason: String?,
    val recommendedAction: RecommendedAction,
    val inDatabase: Boolean,
    val dependencies: List<String> = emptyList(),
    val neededBy: List<String> = emptyList(),
    val communityTier: String? = null,
    /** [Source.OEM] パッケージのメーカー名 (例: "Xiaomi")。 */
    val manufacturer: String? = null,
    /** 警告以上の情報が Prune のデータベースにある場合の、動かなくなるもの。 */
    val consequences: String? = null,
    /** パッケージを元に戻す方法。 */
    val recovery: String = "",
    /** 地域別のバリアント (例: アプリの .global 版や .india 版)。 */
    val regional: Boolean = false,
) {
    /** パッケージの出どころを示すラベル。メーカー製パッケージではメーカー名。 */
    val sourceLabel: String get() = if (source == Source.OEM) manufacturer ?: source.label else source.label
    /** HIGH と UNKNOWN は事前選択されず、追加の確認が必要。 */
    val needsCare: Boolean get() = risk == Risk.HIGH || risk == Risk.UNKNOWN
}

data class AppPackage(
    val facts: PackageFacts,
    val classification: Classification,
    /** ユーザーが最後に開いた時刻 (使用状況統計より)。不明なら null。 */
    val lastUsed: Long? = null,
    /** 同じ役割の別アプリがインストールされている場合に設定される (ブラウザ 2 つ、ギャラリー 2 つなど)。 */
    val duplicateGroup: String? = null,
) {
    val packageName get() = facts.packageName
    val label get() = facts.label
    val status get() = facts.status
    val risk get() = classification.risk
    val isLocked get() = classification.risk == Risk.LOCKED
}

/** Prune が適用・取り消しできる端末レベルの変更 1 件。 */
@Serializable
enum class ChangeType(@StringRes private val labelRes: Int, val reversible: Boolean = true) {
    DISABLE(R.string.change_disable),
    ENABLE(R.string.change_enable),
    UNINSTALL_USER(R.string.change_uninstall_user),
    REINSTALL_USER(R.string.change_reinstall_user),
    UNINSTALL_APP(R.string.change_uninstall_app, reversible = false),
    APPOP_SET(R.string.change_appop_set),
    PERMISSION_REVOKE(R.string.change_permission_revoke),
    PERMISSION_GRANT(R.string.change_permission_grant),
    DEVICEIDLE_REMOVE(R.string.change_deviceidle_remove),
    DEVICEIDLE_ADD(R.string.change_deviceidle_add),
    SETTING_PUT(R.string.change_setting_put),
    TRIM_CACHES(R.string.change_trim_caches, reversible = false),
    DEXOPT(R.string.change_dexopt, reversible = false);

    val label: String get() = str(labelRes)
}
