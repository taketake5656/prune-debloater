package io.github.cyb3rl00m.prune.ui

import io.github.cyb3rl00m.prune.R
import io.github.cyb3rl00m.prune.core.i18n.str
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.cyb3rl00m.prune.core.history.Batch
import io.github.cyb3rl00m.prune.core.history.HistoryEntry
import io.github.cyb3rl00m.prune.core.history.HistoryFile
import io.github.cyb3rl00m.prune.core.model.AppPackage
import io.github.cyb3rl00m.prune.core.model.ChangeType
import io.github.cyb3rl00m.prune.core.model.PackageStatus
import io.github.cyb3rl00m.prune.core.plan.Candidate
import io.github.cyb3rl00m.prune.core.plan.ChangeCommands
import io.github.cyb3rl00m.prune.core.plan.DebloatLevel
import io.github.cyb3rl00m.prune.core.plan.ExecProgress
import io.github.cyb3rl00m.prune.core.plan.Inverse
import io.github.cyb3rl00m.prune.core.plan.Plan
import io.github.cyb3rl00m.prune.core.plan.PlanBuilder
import io.github.cyb3rl00m.prune.core.plan.PlannedChange
import io.github.cyb3rl00m.prune.core.plan.ProfileOptions
import io.github.cyb3rl00m.prune.core.settings.AppSettings
import io.github.cyb3rl00m.prune.core.shell.AccessState
import io.github.cyb3rl00m.prune.core.shell.Commands
import io.github.cyb3rl00m.prune.repository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ExecState {
    data object Idle : ExecState
    data class Running(val progress: ExecProgress) : ExecState
    data class Done(val batch: Batch) : ExecState
}

/** カスタマイズ中の debloat プロファイルの候補と、ユーザーが手動で付けたチェック。 */
data class ProfileDraft(
    val options: ProfileOptions,
    val candidates: List<Candidate>,
    val overrides: Map<String, Boolean> = emptyMap(),
    val backgroundApps: List<AppPackage> = emptyList(),
) {
    fun isSelected(c: Candidate) = overrides[c.app.packageName] ?: c.selected
    val selectedApps get() = candidates.filter { it.keptBecause == null && isSelected(it) }.map { it.app }
}

class AppViewModel(application: Application) : AndroidViewModel(application) {
    val repo = application.repository
    val user = android.os.Process.myUid() / 100_000

    val device = repo.device
    val scan = repo.scan
    val access: StateFlow<AccessState> = repo.access.state
    val history: StateFlow<HistoryFile> = repo.history.state
    val settings: StateFlow<AppSettings> = repo.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val ecosystems get() = repo.database.ecosystems
    val allEcosystemKeys get() = ecosystems.map { it.key }.toSet()

    private val _draft = MutableStateFlow<Plan?>(null)
    /** 確認画面に表示するプラン。 */
    val draft: StateFlow<Plan?> = _draft.asStateFlow()

    private val _profile = MutableStateFlow<ProfileDraft?>(null)
    val profile: StateFlow<ProfileDraft?> = _profile.asStateFlow()

    private val _exec = MutableStateFlow<ExecState>(ExecState.Idle)
    val exec: StateFlow<ExecState> = _exec.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    /** 次のプランが取り消す履歴エントリの id (成功したら取り消し済みにするため)。 */
    private var pendingUndo: Map<String, String> = emptyMap()

    init {
        viewModelScope.launch { settings.collect { s -> repo.access.setPreferRoot(s.preferRoot) } }
    }

    fun rescan() = viewModelScope.launch { repo.rescan() }

    fun refreshAccess() = repo.access.refresh()

    fun say(msg: String) { _messages.tryEmit(msg) }

    fun usedEcosystems(): Set<String> = io.github.cyb3rl00m.prune.core.settings.SettingsStore.effectiveUsed(settings.value, allEcosystemKeys)

    // ---------- debloat プロファイル ----------

    fun startProfile(level: DebloatLevel) {
        val s = settings.value
        val options = ProfileOptions.defaults(level, usedEcosystems()).copy(
            method = s.method, fallbackUninstall = s.fallbackUninstall, skipUsedWithinDays = s.skipUsedWithinDays,
        )
        rebuildProfile(options, emptyMap())
        viewModelScope.launch { repo.settings.setProgressLevel(level.number.coerceAtMost(3)) }
    }

    fun updateProfileOptions(options: ProfileOptions) {
        rebuildProfile(options, _profile.value?.overrides.orEmpty())
        if (options.usedEcosystems != usedEcosystems()) {
            viewModelScope.launch { repo.settings.setUsedEcosystems(options.usedEcosystems, allEcosystemKeys) }
        }
    }

    fun toggleCandidate(pkg: String, selected: Boolean) {
        _profile.value = _profile.value?.let { it.copy(overrides = it.overrides + (pkg to selected)) }
    }

    fun setAllCandidates(selected: Boolean) {
        _profile.value = _profile.value?.let { p ->
            p.copy(overrides = p.candidates.filter { it.keptBecause == null }.associate { it.app.packageName to selected })
        }
    }

    private fun rebuildProfile(options: ProfileOptions, overrides: Map<String, Boolean>) {
        val apps = scan.value.apps
        val candidates = PlanBuilder.candidates(apps, options)
        _profile.value = ProfileDraft(options, candidates, overrides, _profile.value?.backgroundApps.orEmpty())
        if (options.restrictUnusedBackground) {
            viewModelScope.launch {
                val bg = repo.optimizer.backgroundCandidates(apps, scan.value.usage, repo.access.shell())
                _profile.value = _profile.value?.copy(backgroundApps = bg)
            }
        }
    }

    fun reviewProfile() {
        val p = _profile.value ?: return
        val extra = if (p.options.restrictUnusedBackground) p.backgroundApps.map { repo.optimizer.backgroundChange(it, true) } else emptyList()
        val plan = PlanBuilder.plan(
            title = str(R.string.vm_profile_title, p.options.level.title), origin = str(R.string.vm_profile_origin, p.options.level.number),
            selected = p.selectedApps, options = p.options, extra = extra,
        )
        review(plan)
    }

    // ---------- 確認と適用 ----------

    fun review(plan: Plan, undo: Map<String, String> = emptyMap()) {
        pendingUndo = undo
        _draft.value = plan
        _exec.value = ExecState.Idle
    }

    fun removeFromDraft(changeId: String) {
        _draft.value = _draft.value?.let { it.copy(changes = it.changes.filterNot { c -> c.id == changeId }) }
    }

    fun commandsPreview(change: PlannedChange): List<String> = ChangeCommands.preview(change, user)

    fun apply() {
        val plan = _draft.value ?: return
        val shell = repo.access.shell()
        if (shell == null) {
            say(str(R.string.vm_need_shell))
            return
        }
        val undo = pendingUndo
        viewModelScope.launch {
            _exec.value = ExecState.Running(ExecProgress(0, plan.changes.size, "", emptyList()))
            val batch = repo.executor.apply(plan, shell, undo) { p -> _exec.value = ExecState.Running(p) }
            _exec.value = ExecState.Done(batch)
            pendingUndo = emptyMap()
            repo.rescan()
            _profile.value = _profile.value?.let { pd ->
                pd.copy(candidates = PlanBuilder.candidates(scan.value.apps, pd.options), overrides = emptyMap())
            }
        }
    }

    fun scriptForDraft(): String? {
        val plan = _draft.value ?: return null
        return ChangeCommands.adbScript(plan, user, str(R.string.vm_script_header, java.util.Date().toString()))
    }

    fun recordDraftAsScript() {
        val plan = _draft.value ?: return
        viewModelScope.launch {
            repo.executor.recordScript(plan)
            say(str(R.string.vm_script_saved))
        }
    }

    fun verifyScript(batch: Batch) = viewModelScope.launch {
        repo.rescan()
        val checked = repo.executor.verifyScriptBatch(batch)
        val ok = checked.entries.count { it.success == true }
        say(str(R.string.vm_script_checked, ok, checked.entries.size))
    }

    fun resetExec() { _exec.value = ExecState.Idle }

    // ---------- 個別パッケージの操作 ----------

    fun planFor(app: AppPackage, type: ChangeType): Plan {
        val base = PlanBuilder.changeFor(app, settings.value.method, settings.value.fallbackUninstall)
        val change = when (type) {
            ChangeType.DISABLE -> base.copy(type = ChangeType.DISABLE, fallbackUninstall = false)
            ChangeType.UNINSTALL_USER -> base.copy(type = ChangeType.UNINSTALL_USER, fallbackUninstall = false,
                explanation = io.github.cyb3rl00m.prune.core.model.RemovalMethod.UNINSTALL_USER.explanation)
            ChangeType.ENABLE -> base.copy(type = ChangeType.ENABLE, warnings = emptyList(), risk = null, fallbackUninstall = false,
                explanation = if (app.status == PackageStatus.REMOVED) str(R.string.vm_reinstall_from_system) else str(R.string.inv_enable))
            else -> base.copy(type = type)
        }
        return Plan(change.label, str(R.string.vm_origin_package_manager), listOf(change))
    }

    // ---------- 復元 ----------

    /** 取り消しプランを確認画面に出す。取り消すものがなければ false を返す (理由も表示する)。 */
    fun undoEntries(entries: List<HistoryEntry>, title: String): Boolean {
        val inverses = entries.filter { it.success == true && !it.undone }
            .mapNotNull { e -> Inverse.of(e.change, e.before)?.let { inv -> inv to e.id } }
        if (inverses.isEmpty()) {
            say(str(R.string.vm_nothing_to_undo))
            return false
        }
        review(Plan(title, str(R.string.vm_origin_restore), inverses.map { it.first }), inverses.associate { it.first.id to it.second })
        return true
    }

    fun undoBatch(batch: Batch): Boolean = undoEntries(batch.entries, str(R.string.vm_undo_title, batch.title))

    /** Prune が変更したすべてのパッケージを、Prune が最初に触れたときの状態に戻す。 */
    fun restoreAllOriginals(): Boolean {
        val apps = scan.value.apps.associateBy { it.packageName }
        val changes = history.value.originals.values.mapNotNull { o ->
            val now = repo.executor.packageState(o.packageName)
            val app = apps[o.packageName]
            when {
                now == o.state || now == "missing" -> null
                o.state == "enabled" -> PlannedChange(type = ChangeType.ENABLE, packageName = o.packageName, label = app?.label ?: o.label,
                    explanation = str(R.string.vm_restore_enabled), isSystemPackage = app?.facts?.isSystem ?: true)
                o.state == "disabled" && now == "removed" -> PlannedChange(type = ChangeType.REINSTALL_USER, packageName = o.packageName,
                    label = app?.label ?: o.label, explanation = str(R.string.vm_restore_disabled))
                else -> null
            }
        }
        if (changes.isEmpty()) {
            say(str(R.string.vm_already_original))
            return false
        }
        review(Plan(str(R.string.vm_restore_original_title), str(R.string.vm_origin_restore), changes))
        return true
    }

    fun restorePackages(apps: List<AppPackage>): Boolean {
        val changes = apps.filter { it.status != PackageStatus.ENABLED }.map { planFor(it, ChangeType.ENABLE).changes.first() }
        if (changes.isEmpty()) return false
        review(Plan(str(R.string.vm_restore_n_title, changes.size), str(R.string.vm_origin_restore), changes))
        return true
    }

    /** 現在無効化・削除されているすべてのシステムパッケージを再び有効にする ADB スクリプト。 */
    fun emergencyRestoreScript(): String {
        val apps = scan.value.apps.filter { it.facts.isSystem && it.status != PackageStatus.ENABLED }
        return buildString {
            appendLine("# " + str(R.string.vm_emergency_1, java.util.Date().toString()))
            appendLine("# " + str(R.string.vm_emergency_2, user))
            appendLine("# " + str(R.string.vm_emergency_3))
            appendLine()
            for (a in apps) {
                appendLine("# ${a.label}")
                appendLine("adb shell " + Commands.toShell(Commands.installExisting(a.packageName, user)))
                appendLine("adb shell " + Commands.toShell(Commands.enable(a.packageName, user)))
            }
        }
    }

    // ---------- 互換性 ----------

    fun compatibilityReport(): io.github.cyb3rl00m.prune.core.oem.CompatibilityReport? {
        val d = device.value ?: return null
        val a = access.value
        return io.github.cyb3rl00m.prune.core.oem.CompatibilityReport.build(
            device = d.fullName + if (d.marketName != d.model) " (${d.model})" else "",
            identity = d.identity, integration = d.integration, firmware = d.firmware, services = d.services,
            access = io.github.cyb3rl00m.prune.core.oem.AccessSnapshot(
                tier = "${a.active.label} (${a.active.tier})", privileged = a.privileged, shizukuInstalled = a.shizukuInstalled,
                shizukuRunning = a.shizukuRunning, shizukuAuthorized = a.shizukuPermission, root = a.rootGranted,
                usageAccess = a.usageAccess, writeSecureSettings = a.writeSecureSettings,
            ),
            history = history.value,
        )
    }

    // ---------- 設定 ----------

    fun update(block: suspend io.github.cyb3rl00m.prune.core.settings.SettingsStore.() -> Unit) =
        viewModelScope.launch { repo.settings.block() }

    fun requestShizuku() = repo.access.requestShizukuPermission()

    fun requestRoot() = viewModelScope.launch {
        val ok = repo.access.requestRoot()
        say(str(if (ok) R.string.vm_root_granted else R.string.vm_root_denied))
    }

    fun grantSelfPermissions() = viewModelScope.launch {
        val done = repo.access.grantSelfPermissions()
        if (done.isNotEmpty()) {
            say(str(R.string.vm_granted, done.joinToString()))
            repo.rescan()
        }
    }
}
