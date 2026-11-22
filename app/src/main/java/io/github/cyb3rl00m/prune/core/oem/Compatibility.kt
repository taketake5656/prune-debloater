package io.github.cyb3rl00m.prune.core.oem

import androidx.annotation.StringRes
import io.github.cyb3rl00m.prune.R
import io.github.cyb3rl00m.prune.core.history.HistoryFile
import io.github.cyb3rl00m.prune.core.i18n.str
import io.github.cyb3rl00m.prune.core.model.ChangeType

/** 操作に必要なもの。 */
enum class Requirement(@StringRes private val labelRes: Int) {
    STANDARD(R.string.req_standard),
    USAGE_ACCESS(R.string.req_usage_access),
    SHELL(R.string.req_shell),
    SHELL_OR_SECURE_SETTINGS(R.string.req_shell_or_secure_settings);

    val label: String get() = str(labelRes)
}

/** Prune ができることの一覧 (互換性レポート用)。 */
enum class Operation(
    @StringRes private val labelRes: Int,
    val requirement: Requirement,
    val minSdk: Int = 28,
    val changeTypes: Set<ChangeType> = emptySet(),
) {
    SCAN(R.string.op_scan, Requirement.STANDARD),
    UNINSTALL_OWN(R.string.op_uninstall_own, Requirement.STANDARD),
    SCRIPT(R.string.op_script, Requirement.STANDARD),
    USAGE(R.string.op_usage, Requirement.USAGE_ACCESS),
    DISABLE(R.string.op_disable, Requirement.SHELL, changeTypes = setOf(ChangeType.DISABLE)),
    UNINSTALL_USER(R.string.op_uninstall_user, Requirement.SHELL, changeTypes = setOf(ChangeType.UNINSTALL_USER)),
    RESTORE(R.string.op_restore, Requirement.SHELL, changeTypes = setOf(ChangeType.ENABLE, ChangeType.REINSTALL_USER)),
    BACKGROUND(R.string.op_background, Requirement.SHELL, changeTypes = setOf(ChangeType.APPOP_SET)),
    PERMISSIONS(R.string.op_permissions, Requirement.SHELL, changeTypes = setOf(ChangeType.PERMISSION_REVOKE, ChangeType.PERMISSION_GRANT)),
    NOTIFICATIONS(R.string.op_notifications, Requirement.SHELL, minSdk = 33),
    BATTERY_EXEMPTIONS(R.string.op_battery_exemptions, Requirement.SHELL, changeTypes = setOf(ChangeType.DEVICEIDLE_REMOVE, ChangeType.DEVICEIDLE_ADD)),
    ANIMATIONS(R.string.op_animations, Requirement.SHELL_OR_SECURE_SETTINGS, changeTypes = setOf(ChangeType.SETTING_PUT)),
    CACHES(R.string.op_caches, Requirement.SHELL, changeTypes = setOf(ChangeType.TRIM_CACHES)),
    DEXOPT(R.string.op_dexopt, Requirement.SHELL, changeTypes = setOf(ChangeType.DEXOPT)),
    BATTERY_STATS(R.string.op_battery_stats, Requirement.SHELL);

    val label: String get() = str(labelRes)
}

enum class OpStatus(@StringRes private val labelRes: Int) {
    AVAILABLE(R.string.opstatus_available),
    NEEDS_ACCESS(R.string.opstatus_needs_access),
    NEEDS_USAGE(R.string.opstatus_needs_usage),
    UNSUPPORTED(R.string.opstatus_unsupported);

    val label: String get() = str(labelRes)
}

data class OperationReport(
    val operation: Operation,
    val status: OpStatus,
    /** ファームウェア固有の注意点 (あれば)。 */
    val caveat: String?,
    /** この端末でのこの操作の結果 (Prune の履歴より)。 */
    val succeeded: Int,
    val failed: Int,
    val lastError: String?,
)

data class AccessSnapshot(
    val tier: String,
    val privileged: Boolean,
    val shizukuInstalled: Boolean,
    val shizukuRunning: Boolean,
    val shizukuAuthorized: Boolean,
    val root: Boolean,
    val usageAccess: Boolean,
    val writeSecureSettings: Boolean,
)

data class CompatibilityReport(
    val device: String,
    val manufacturer: String,
    val androidVersion: String,
    val sdkInt: Int,
    val firmware: Firmware?,
    val integration: OemIntegration,
    val services: ServicesInfo,
    val access: AccessSnapshot,
    val operations: List<OperationReport>,
    val limitations: List<Limitation>,
    val setupSteps: List<String>,
) {
    val supported get() = operations.filter { it.status == OpStatus.AVAILABLE }
    val unsupported get() = operations.filter { it.status != OpStatus.AVAILABLE }

    /** 不具合報告用のプレーンテキスト。表示言語で書き出す。 */
    fun asText(appVersion: String): String = buildString {
        fun yn(b: Boolean) = str(if (b) R.string.report_yes else R.string.report_no)
        val shizuku = str(when {
            access.shizukuRunning -> R.string.report_running
            access.shizukuInstalled -> R.string.report_installed
            else -> R.string.report_not_installed
        })
        appendLine(str(R.string.report_title, appVersion))
        appendLine(str(R.string.report_device, device))
        appendLine(str(R.string.report_manufacturer, manufacturer))
        appendLine(str(R.string.report_android, androidVersion, sdkInt))
        appendLine(str(R.string.report_firmware, firmware?.toString() ?: str(R.string.report_unknown)))
        appendLine(str(R.string.report_integration, integration.title, integration.compat.label))
        appendLine(str(R.string.report_services, yn(services.gms), yn(services.playStore), yn(services.hms)))
        appendLine(str(R.string.report_access, access.tier, shizuku, yn(access.root), yn(access.usageAccess)))
        appendLine()
        appendLine(str(R.string.report_operations))
        for (o in operations) {
            append("- ${o.operation.label}: ${o.status.label}")
            if (o.succeeded + o.failed > 0) append(" " + str(R.string.report_on_this_device, o.succeeded, o.failed))
            appendLine()
            o.caveat?.let { appendLine("    " + str(R.string.report_note, it)) }
            o.lastError?.let { appendLine("    " + str(R.string.report_last_error, it)) }
        }
        if (limitations.isNotEmpty()) {
            appendLine()
            appendLine(str(R.string.report_known_limitations))
            limitations.forEach { appendLine("- ${it.title}: ${it.text}") }
        }
    }

    companion object {
        fun build(
            device: String,
            identity: DeviceIdentity,
            integration: OemIntegration,
            firmware: Firmware?,
            services: ServicesInfo,
            access: AccessSnapshot,
            history: HistoryFile,
        ): CompatibilityReport {
            val entries = history.batches.flatMap { it.entries }
            val ops = Operation.entries.map { op ->
                val status = when {
                    identity.sdkInt < op.minSdk -> OpStatus.UNSUPPORTED
                    op.requirement == Requirement.STANDARD -> OpStatus.AVAILABLE
                    op.requirement == Requirement.USAGE_ACCESS -> if (access.usageAccess) OpStatus.AVAILABLE else OpStatus.NEEDS_USAGE
                    op.requirement == Requirement.SHELL_OR_SECURE_SETTINGS ->
                        if (access.privileged || access.writeSecureSettings) OpStatus.AVAILABLE else OpStatus.NEEDS_ACCESS
                    else -> if (access.privileged) OpStatus.AVAILABLE else OpStatus.NEEDS_ACCESS
                }
                val mine = entries.filter { it.change.type in op.changeTypes && it.success != null }
                OperationReport(
                    operation = op, status = status, caveat = runCatching { caveat(integration, op) }.getOrNull(),
                    succeeded = mine.count { it.success == true }, failed = mine.count { it.success == false },
                    lastError = mine.lastOrNull { it.success == false }?.message?.take(200),
                )
            }
            return CompatibilityReport(
                device = device, manufacturer = identity.manufacturer, androidVersion = identity.release,
                sdkInt = identity.sdkInt, firmware = firmware, integration = integration, services = services,
                access = access, operations = ops,
                limitations = OemRegistry.limitationsSafe(integration, identity, services),
                setupSteps = runCatching { integration.developerOptionsSteps + integration.extraSetupSteps(identity) }.getOrDefault(emptyList()),
            )
        }

        /** 操作ごとのファームウェア固有の注意点。そのファームウェアについて文書化されている挙動に限る。 */
        fun caveat(i: OemIntegration, op: Operation): String? = when {
            i == XiaomiIntegration && op in setOf(Operation.PERMISSIONS, Operation.BACKGROUND, Operation.NOTIFICATIONS, Operation.ANIMATIONS) ->
                str(R.string.caveat_xiaomi)
            i == OplusIntegration && op in setOf(Operation.PERMISSIONS, Operation.BACKGROUND, Operation.NOTIFICATIONS) ->
                str(R.string.caveat_oplus)
            i.disableOftenRefused && op == Operation.DISABLE ->
                str(R.string.caveat_disable_often_refused)
            i == SamsungIntegration && op == Operation.DISABLE ->
                str(R.string.caveat_samsung_disable)
            else -> null
        }
    }
}
