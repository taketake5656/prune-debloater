package io.github.cyb3rl00m.prune.core.plan

import android.content.Context
import io.github.cyb3rl00m.prune.R
import io.github.cyb3rl00m.prune.core.i18n.str
import android.content.pm.PackageManager
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import android.provider.Settings
import io.github.cyb3rl00m.prune.core.history.Batch
import io.github.cyb3rl00m.prune.core.history.HistoryEntry
import io.github.cyb3rl00m.prune.core.history.HistoryStore
import io.github.cyb3rl00m.prune.core.history.OriginalState
import io.github.cyb3rl00m.prune.core.model.ChangeType
import io.github.cyb3rl00m.prune.core.model.PackageStatus
import io.github.cyb3rl00m.prune.core.scan.PackageScanner
import io.github.cyb3rl00m.prune.core.shell.Commands
import io.github.cyb3rl00m.prune.core.shell.OutputParsers
import io.github.cyb3rl00m.prune.core.shell.PrivilegedShell
import io.github.cyb3rl00m.prune.core.shell.ShellResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

data class ExecProgress(val done: Int, val total: Int, val current: String, val log: List<String>)

/**
 * プランを適用する。変更ごとに変更前の状態を記録し、コマンドを実行してから、Android から実際の状態を
 * 読み戻して成否を判定する。コマンド自身の出力だけを信用することはない。
 */
class Executor(
    private val context: Context,
    private val scanner: PackageScanner,
    private val history: HistoryStore,
) {
    private val user = android.os.Process.myUid() / 100_000
    private val pm = context.packageManager

    suspend fun apply(
        plan: Plan,
        shell: PrivilegedShell,
        undoOf: Map<String, String> = emptyMap(),
        onProgress: (ExecProgress) -> Unit = {},
    ): Batch = withContext(Dispatchers.IO) {
        val log = mutableListOf<String>()
        val entries = mutableListOf<HistoryEntry>()
        val originals = mutableMapOf<String, OriginalState>()
        plan.changes.forEachIndexed { i, change ->
            onProgress(ExecProgress(i, plan.changes.size, change.label, log.toList()))
            val before = readState(change, shell)
            if (change.packageName != null && before in PACKAGE_STATES) {
                originals[change.packageName] = OriginalState(change.packageName, change.label, before!!, System.currentTimeMillis())
            }
            val commands = mutableListOf<String>()
            suspend fun run(argv: List<String>, timeout: Long = 60_000): ShellResult {
                commands += Commands.toShell(argv)
                log += "$ " + Commands.toShell(argv)
                val r = runCatching { shell.run(argv, timeout) }
                    .getOrElse { ShellResult(-4, "", it.message ?: it.toString()) }
                r.output.lineSequence().take(6).forEach { if (it.isNotBlank()) log += "  $it" }
                return r
            }
            var message = ""
            val result: ShellResult? = when (change.type) {
                ChangeType.DISABLE -> {
                    var r = run(Commands.disable(change.packageName!!, user))
                    if (!OutputParsers.disableSucceeded(r) && change.fallbackUninstall) {
                        message = str(R.string.exec_disable_refused, firstLine(r))
                        r = run(Commands.uninstallForUser(change.packageName, user))
                    }
                    r
                }
                ChangeType.SETTING_PUT -> putSetting(change) { argv, timeout -> run(argv, timeout) }
                ChangeType.TRIM_CACHES -> {
                    val free = freeBytes()
                    val r = run(Commands.trimCaches(), 300_000)
                    val gained = (freeBytes() - free).coerceAtLeast(0)
                    message = str(R.string.exec_free_space_grew, formatBytes(gained))
                    r
                }
                ChangeType.DEXOPT -> run(Commands.backgroundDexopt(), 30 * 60_000L)
                ChangeType.UNINSTALL_APP -> null
                else -> {
                    var last: ShellResult? = null
                    for (argv in ChangeCommands.forChange(change, user)) last = run(argv)
                    last
                }
            }
            val after = readState(change, shell)
            val success = when (change.type) {
                ChangeType.DISABLE -> after == "disabled" || (change.fallbackUninstall && after == "removed")
                ChangeType.ENABLE -> after == "enabled"
                ChangeType.UNINSTALL_USER -> after == "removed"
                ChangeType.REINSTALL_USER -> after == "enabled" || after == "disabled"
                ChangeType.APPOP_SET -> after == change.params["mode"] || (change.params["mode"] == "default" && after in setOf("default", "allow"))
                ChangeType.PERMISSION_REVOKE -> after == "revoked"
                ChangeType.PERMISSION_GRANT -> after == "granted"
                ChangeType.DEVICEIDLE_REMOVE -> after == "not-exempt"
                ChangeType.DEVICEIDLE_ADD -> after == "exempt"
                ChangeType.SETTING_PUT -> after == (change.params["value"] ?: "null")
                ChangeType.TRIM_CACHES, ChangeType.DEXOPT -> result?.exitCode == 0
                ChangeType.UNINSTALL_APP -> false
            }
            if (message.isEmpty()) {
                message = if (success) str(R.string.exec_done) else str(R.string.exec_not_done,
                    result?.let(::firstLine)?.ifBlank { null } ?: str(R.string.exec_state_still, after.toString()))
            }
            log += if (success) "  ✓ ${change.label}" else "  ✗ ${change.label}: $message"
            entries += HistoryEntry(
                id = UUID.randomUUID().toString(), change = change, before = before, after = after,
                success = success, message = message, commands = commands, undoOf = undoOf[change.id],
            )
        }
        onProgress(ExecProgress(plan.changes.size, plan.changes.size, "", log.toList()))
        val batch = Batch(UUID.randomUUID().toString(), plan.title, plan.origin, System.currentTimeMillis(),
            shell.kind.label, entries)
        history.addBatch(batch, originals)
        val undone = entries.filter { it.success == true }.mapNotNull { it.undoOf }.toSet()
        if (undone.isNotEmpty()) history.markUndone(undone)
        batch
    }

    /** ADB スクリプトとして書き出したプランを記録する。[verifyScriptBatch] を呼ぶまで結果は未検証のまま。 */
    suspend fun recordScript(plan: Plan): Batch {
        val entries = plan.changes.map { c ->
            HistoryEntry(UUID.randomUUID().toString(), c, before = readState(c, null), success = null,
                message = str(R.string.exec_waiting_script),
                commands = ChangeCommands.forChange(c, user).map(Commands::toShell))
        }
        val originals = entries.filter { it.change.packageName != null && it.before in PACKAGE_STATES }
            .associate { it.change.packageName!! to OriginalState(it.change.packageName, it.change.label, it.before!!, System.currentTimeMillis()) }
        val batch = Batch(UUID.randomUUID().toString(), plan.title, plan.origin, System.currentTimeMillis(),
            str(R.string.exec_adb_script), entries, script = true)
        history.addBatch(batch, originals)
        return batch
    }

    suspend fun verifyScriptBatch(batch: Batch): Batch {
        val checked = batch.copy(entries = batch.entries.map { e ->
            val after = readState(e.change, null)
            val ok = when (e.change.type) {
                ChangeType.DISABLE -> after == "disabled"
                ChangeType.ENABLE -> after == "enabled"
                ChangeType.UNINSTALL_USER -> after == "removed"
                ChangeType.REINSTALL_USER -> after != "removed"
                ChangeType.SETTING_PUT -> after == (e.change.params["value"] ?: "null")
                ChangeType.PERMISSION_REVOKE -> after == "revoked"
                ChangeType.PERMISSION_GRANT -> after == "granted"
                ChangeType.DEVICEIDLE_REMOVE -> after == "not-exempt"
                ChangeType.DEVICEIDLE_ADD -> after == "exempt"
                else -> null
            }
            e.copy(after = after, success = ok, message = when (ok) {
                true -> str(R.string.exec_verified)
                false -> str(R.string.exec_not_applied, after.toString())
                null -> str(R.string.exec_cannot_verify)
            })
        })
        history.replaceBatch(checked)
        return checked
    }

    /** 変更に関係する現在の状態を読む。[shell] が必要なのは app-ops の場合だけ。 */
    suspend fun readState(change: PlannedChange, shell: PrivilegedShell?): String? = withContext(Dispatchers.IO) {
        val p = change.packageName
        when (change.type) {
            ChangeType.DISABLE, ChangeType.ENABLE, ChangeType.UNINSTALL_USER, ChangeType.REINSTALL_USER,
            ChangeType.UNINSTALL_APP -> p?.let { packageState(it) }
            ChangeType.APPOP_SET -> shell?.let {
                val r = runCatching { it.run(Commands.appOpGet(p!!, change.params.getValue("op"), user)) }.getOrNull()
                r?.let { res -> OutputParsers.appOpMode(res.stdout, change.params.getValue("op")) }
            }
            ChangeType.PERMISSION_REVOKE, ChangeType.PERMISSION_GRANT ->
                if (pm.checkPermission(change.params.getValue("permission"), p!!) == PackageManager.PERMISSION_GRANTED) "granted" else "revoked"
            ChangeType.DEVICEIDLE_REMOVE, ChangeType.DEVICEIDLE_ADD ->
                if (context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(p!!)) "exempt" else "not-exempt"
            ChangeType.SETTING_PUT -> Settings.Global.getString(context.contentResolver, change.params.getValue("key")) ?: "null"
            ChangeType.TRIM_CACHES -> freeBytes().toString()
            ChangeType.DEXOPT -> null
        }
    }

    fun packageState(pkg: String): String = when (scanner.read(pkg)?.status) {
        PackageStatus.ENABLED -> "enabled"
        PackageStatus.DISABLED -> "disabled"
        PackageStatus.REMOVED -> "removed"
        null -> "missing"
    }

    private suspend fun putSetting(change: PlannedChange, run: suspend (List<String>, Long) -> ShellResult): ShellResult {
        val key = change.params.getValue("key")
        val value = change.params["value"]
        val hasPermission = context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            val ok = runCatching {
                if (value == null) Settings.Global.putString(context.contentResolver, key, null)
                else Settings.Global.putString(context.contentResolver, key, value)
            }.getOrDefault(false)
            if (ok) return ShellResult(0, "", "")
        }
        return run(if (value == null) Commands.settingsDelete(key) else Commands.settingsPut(key, value), 60_000)
    }

    private fun firstLine(r: ShellResult) = r.output.lineSequence().firstOrNull { it.isNotBlank() }?.take(200) ?: ""

    private fun freeBytes(): Long = runCatching { StatFs(Environment.getDataDirectory().path).availableBytes }.getOrDefault(0)

    companion object {
        val PACKAGE_STATES = setOf("enabled", "disabled", "removed")

        fun formatBytes(b: Long): String {
            if (b < 1000) return "$b B"
            val units = listOf("kB", "MB", "GB", "TB")
            var v = b.toDouble()
            var i = -1
            while (v >= 1000 && i < units.lastIndex) { v /= 1000; i++ }
            return String.format(java.util.Locale.US, if (v >= 100) "%.0f %s" else "%.1f %s", v, units[i])
        }
    }
}
