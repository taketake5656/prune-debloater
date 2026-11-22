package io.github.cyb3rl00m.prune.core.plan

import androidx.annotation.StringRes
import io.github.cyb3rl00m.prune.R
import io.github.cyb3rl00m.prune.core.i18n.str
import io.github.cyb3rl00m.prune.core.model.AppPackage
import io.github.cyb3rl00m.prune.core.model.ChangeType
import io.github.cyb3rl00m.prune.core.model.PackageStatus
import io.github.cyb3rl00m.prune.core.model.RemovalMethod
import io.github.cyb3rl00m.prune.core.model.Risk
import io.github.cyb3rl00m.prune.core.model.Source
import io.github.cyb3rl00m.prune.core.shell.Commands
import kotlinx.serialization.Serializable
import java.util.UUID

/** Prune が行おうとしている変更 1 件。履歴にも保存されるのでシリアライズ可能にしている。 */
@Serializable
data class PlannedChange(
    val id: String = UUID.randomUUID().toString(),
    val type: ChangeType,
    val packageName: String? = null,
    val label: String,
    val params: Map<String, String> = emptyMap(),
    val explanation: String,
    val warnings: List<String> = emptyList(),
    val risk: Risk? = null,
    /** ファームウェアに無効化を拒否された場合、代わりにユーザーからアンインストールする (これも元に戻せる)。 */
    val fallbackUninstall: Boolean = false,
    val isSystemPackage: Boolean = true,
) {
    val needsConfirmation: Boolean get() = risk == Risk.HIGH || risk == Risk.UNKNOWN
}

@Serializable
data class Plan(
    val title: String,
    val origin: String,
    val changes: List<PlannedChange>,
    val createdAt: Long = System.currentTimeMillis(),
)

/** 4 段階の debloat レベル。 */
enum class DebloatLevel(
    val number: Int,
    @StringRes private val titleRes: Int,
    @StringRes private val summaryRes: Int,
    private val detailRes: List<Int>,
) {
    MINIMAL(1, R.string.level1_title, R.string.level1_summary, listOf(
        R.string.level1_d1, R.string.level1_d2, R.string.level1_d3,
    )),
    BALANCED(2, R.string.level2_title, R.string.level2_summary, listOf(
        R.string.level2_d1, R.string.level2_d2, R.string.level2_d3, R.string.level2_d4, R.string.level2_d5,
    )),
    AGGRESSIVE(3, R.string.level3_title, R.string.level3_summary, listOf(
        R.string.level3_d1, R.string.level3_d2, R.string.level3_d3, R.string.level3_d4,
    )),
    CUSTOM(4, R.string.level4_title, R.string.level4_summary, listOf(
        R.string.level4_d1, R.string.level4_d2, R.string.level4_d3, R.string.level4_d4,
    ));

    val title: String get() = str(titleRes)
    val summary: String get() = str(summaryRes)
    val details: List<String> get() = detailRes.map { str(it) }

    companion object {
        fun of(n: Int) = entries.first { it.number == n }
    }
}

data class ProfileOptions(
    val level: DebloatLevel,
    val includeGoogle: Boolean,
    /** ユーザーがまだ使っているエコシステムのキー。それらのパッケージは決して選択しない。 */
    val usedEcosystems: Set<String>,
    val method: RemovalMethod = RemovalMethod.DISABLE,
    val skipUsedWithinDays: Int = 14,
    val restrictUnusedBackground: Boolean,
    val fallbackUninstall: Boolean = true,
) {
    companion object {
        fun defaults(level: DebloatLevel, allEcosystems: Set<String>) = ProfileOptions(
            level = level,
            includeGoogle = level.number >= 3,
            usedEcosystems = allEcosystems,
            restrictUnusedBackground = level.number >= 2,
        )
    }
}

data class Candidate(
    val app: AppPackage,
    val selected: Boolean,
    /** 事前選択しない理由 (ユーザーに表示する)。 */
    val note: String? = null,
    /** 完全に除外する (残す) 理由。「残すもの」の欄に表示する。 */
    val keptBecause: String? = null,
)

/**
 * debloat レベルとユーザーの回答を候補リストに変換する。純粋なロジックで、ユニットテスト対象。
 * ここで守る安全規則:
 *  - LOCKED のパッケージは決して候補にしない。
 *  - ユーザーがまだ使っているエコシステムのパッケージは決して候補にしない。
 *  - HIGH リスクのパッケージは Custom モードでのみ表示し、決して事前選択しない。
 *  - 最近使ったアプリは決して事前選択しない。
 */
object PlanBuilder {

    fun candidates(apps: List<AppPackage>, options: ProfileOptions, now: Long = System.currentTimeMillis()): List<Candidate> {
        val maxLevel = if (options.level == DebloatLevel.CUSTOM) 4 else options.level.number
        val defaultLevel = if (options.level == DebloatLevel.CUSTOM) 3 else maxLevel
        val out = mutableListOf<Candidate>()
        for (app in apps) {
            val c = app.classification
            if (app.isLocked) continue
            if (app.status != PackageStatus.ENABLED) continue
            val level = c.level ?: continue
            if (level > maxLevel) continue
            val usedEco = c.groups.firstOrNull { it in options.usedEcosystems }
            if (usedEco != null) {
                out += Candidate(app, selected = false, keptBecause = str(R.string.plan_kept_you_use, ecosystemLabel(c, usedEco)))
                continue
            }
            if (c.source == Source.GOOGLE && !options.includeGoogle) {
                out += Candidate(app, selected = false, keptBecause = str(R.string.plan_kept_google_excluded))
                continue
            }
            val recentlyUsed = app.lastUsed?.let { now - it < options.skipUsedWithinDays * DAY } ?: false
            val inUse = c.warnings.any { it.groupKey == "IN_USE" }
            val note = when {
                recentlyUsed -> str(R.string.plan_note_recently_used, options.skipUsedWithinDays)
                inUse -> c.warnings.first { it.groupKey == "IN_USE" }.text
                c.risk == Risk.HIGH -> str(R.string.plan_note_high)
                c.risk == Risk.UNKNOWN -> str(R.string.plan_note_unknown)
                level > defaultLevel -> str(R.string.plan_note_not_default)
                else -> null
            }
            out += Candidate(app, selected = note == null, note = note)
        }
        return out.sortedWith(compareBy({ !it.selected }, { it.keptBecause != null }, { it.app.risk.ordinal },
            { it.app.label.lowercase() }))
    }

    fun changeFor(app: AppPackage, method: RemovalMethod, fallbackUninstall: Boolean): PlannedChange {
        val system = app.facts.isSystem
        // ユーザーがインストールしたものは常に無効化する。唯一のユーザーからアンインストールすると完全に消えてしまうため。
        val type = if (method == RemovalMethod.UNINSTALL_USER && system) ChangeType.UNINSTALL_USER else ChangeType.DISABLE
        val c = app.classification
        val explanation = buildString {
            append(if (type == ChangeType.DISABLE) RemovalMethod.DISABLE.explanation else RemovalMethod.UNINSTALL_USER.explanation)
            c.notes.firstOrNull()?.let { append("\n\n").append(it) }
        }
        return PlannedChange(
            type = type, packageName = app.packageName, label = app.label, explanation = explanation,
            warnings = c.warnings.map { "${it.title}: ${it.text}" }, risk = c.risk,
            fallbackUninstall = fallbackUninstall && system && type == ChangeType.DISABLE,
            isSystemPackage = system,
        )
    }

    fun plan(title: String, origin: String, selected: List<AppPackage>, options: ProfileOptions,
             extra: List<PlannedChange> = emptyList()): Plan =
        Plan(title, origin, selected.map { changeFor(it, options.method, options.fallbackUninstall) } + extra)

    /** 選択したレベルのパッケージのうち、すでに無効化・削除済みのもの (済み数, 全体数)。 */
    fun progress(apps: List<AppPackage>, level: Int): Pair<Int, Int> {
        val relevant = apps.filter { !it.isLocked && (it.classification.level ?: 99) <= level && it.facts.isSystem }
        val done = relevant.count { it.status != PackageStatus.ENABLED }
        return done to relevant.size
    }

    private fun ecosystemLabel(c: io.github.cyb3rl00m.prune.core.model.Classification, key: String) =
        c.warnings.firstOrNull { it.groupKey == key }?.title ?: key

    private const val DAY = 24L * 60 * 60 * 1000
}

/** 各変更の実際のコマンド。実行・プレビュー・ADB スクリプトで共通に使う。 */
object ChangeCommands {

    fun forChange(change: PlannedChange, user: Int): List<List<String>> {
        val p = change.packageName
        return when (change.type) {
            ChangeType.DISABLE -> listOf(Commands.disable(p!!, user))
            ChangeType.ENABLE -> listOf(Commands.installExisting(p!!, user), Commands.enable(p, user))
            ChangeType.UNINSTALL_USER -> listOf(Commands.uninstallForUser(p!!, user))
            ChangeType.REINSTALL_USER -> listOf(Commands.installExisting(p!!, user))
            ChangeType.APPOP_SET -> listOf(Commands.appOpSet(p!!, change.params.getValue("op"), change.params.getValue("mode"), user))
            ChangeType.PERMISSION_REVOKE -> listOf(Commands.revoke(p!!, change.params.getValue("permission"), user))
            ChangeType.PERMISSION_GRANT -> listOf(Commands.grant(p!!, change.params.getValue("permission"), user))
            ChangeType.DEVICEIDLE_REMOVE -> listOf(Commands.deviceIdleRemove(p!!))
            ChangeType.DEVICEIDLE_ADD -> listOf(Commands.deviceIdleAdd(p!!))
            ChangeType.SETTING_PUT -> {
                val key = change.params.getValue("key")
                val value = change.params["value"]
                listOf(if (value == null) Commands.settingsDelete(key) else Commands.settingsPut(key, value))
            }
            ChangeType.TRIM_CACHES -> listOf(Commands.trimCaches())
            ChangeType.DEXOPT -> listOf(Commands.backgroundDexopt())
            ChangeType.UNINSTALL_APP -> emptyList()
        }
    }

    fun preview(change: PlannedChange, user: Int): List<String> =
        forChange(change, user).map { "adb shell " + Commands.toShell(it) }

    /** Shizuku も root も使えないときに、ユーザーがパソコンから `adb` で実行できるスクリプト。 */
    fun adbScript(plan: Plan, user: Int, header: String): String = buildString {
        appendLine("# $header")
        appendLine("# " + str(R.string.script_title, plan.title, plan.changes.size))
        appendLine("# " + str(R.string.script_line_run))
        appendLine("# " + str(R.string.script_line_os))
        appendLine("# " + str(R.string.script_line_independent))
        appendLine()
        for (c in plan.changes) {
            appendLine("# ${c.type.label}: ${c.label}${c.packageName?.let { " ($it)" } ?: ""}")
            preview(c, user).forEach { appendLine(it) }
        }
    }
}

/** 記録された変更を取り消す変更を、変更前に記録した状態から計算する。 */
object Inverse {
    fun of(change: PlannedChange, before: String?): PlannedChange? {
        val base = change.copy(id = UUID.randomUUID().toString(), warnings = emptyList(), fallbackUninstall = false, risk = null)
        return when (change.type) {
            ChangeType.DISABLE -> when (before) {
                "enabled" -> base.copy(type = ChangeType.ENABLE, explanation = str(R.string.inv_enable))
                else -> null
            }
            ChangeType.UNINSTALL_USER -> when (before) {
                "enabled" -> base.copy(type = ChangeType.ENABLE, explanation = str(R.string.inv_reinstall_enable))
                "disabled" -> base.copy(type = ChangeType.REINSTALL_USER, explanation = str(R.string.inv_reinstall_disabled))
                else -> null
            }
            ChangeType.ENABLE -> when (before) {
                "disabled" -> base.copy(type = ChangeType.DISABLE, explanation = str(R.string.inv_disable_again))
                "removed" -> base.copy(type = ChangeType.UNINSTALL_USER, explanation = str(R.string.inv_uninstall_again))
                else -> null
            }
            ChangeType.REINSTALL_USER -> if (before == "removed") base.copy(type = ChangeType.UNINSTALL_USER, explanation = str(R.string.inv_uninstall_again)) else null
            ChangeType.APPOP_SET -> before?.let { base.copy(params = change.params + ("mode" to it), explanation = str(R.string.inv_appop, it)) }
            ChangeType.PERMISSION_REVOKE -> if (before == "granted") base.copy(type = ChangeType.PERMISSION_GRANT, explanation = str(R.string.inv_grant)) else null
            ChangeType.PERMISSION_GRANT -> if (before == "revoked") base.copy(type = ChangeType.PERMISSION_REVOKE, explanation = str(R.string.inv_revoke)) else null
            ChangeType.DEVICEIDLE_REMOVE -> if (before == "exempt") base.copy(type = ChangeType.DEVICEIDLE_ADD, explanation = str(R.string.inv_deviceidle_add)) else null
            ChangeType.DEVICEIDLE_ADD -> if (before == "not-exempt") base.copy(type = ChangeType.DEVICEIDLE_REMOVE, explanation = str(R.string.inv_deviceidle_remove)) else null
            ChangeType.SETTING_PUT -> base.copy(
                params = if (before == null || before == "null") change.params - "value" else change.params + ("value" to before),
                explanation = str(R.string.inv_setting, before ?: str(R.string.inv_system_default)))
            ChangeType.TRIM_CACHES, ChangeType.DEXOPT, ChangeType.UNINSTALL_APP -> null
        }
    }
}
