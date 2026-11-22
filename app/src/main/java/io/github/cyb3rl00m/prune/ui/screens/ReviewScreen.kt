package io.github.cyb3rl00m.prune.ui.screens

import io.github.cyb3rl00m.prune.R
import io.github.cyb3rl00m.prune.core.i18n.str
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.cyb3rl00m.prune.core.model.Risk
import io.github.cyb3rl00m.prune.core.plan.PlannedChange
import io.github.cyb3rl00m.prune.ui.AppViewModel
import io.github.cyb3rl00m.prune.ui.ExecState
import io.github.cyb3rl00m.prune.ui.Nav
import io.github.cyb3rl00m.prune.ui.Routes
import io.github.cyb3rl00m.prune.ui.components.AppIcon
import io.github.cyb3rl00m.prune.ui.components.Banner
import io.github.cyb3rl00m.prune.ui.components.BannerKind
import io.github.cyb3rl00m.prune.ui.components.Pill
import io.github.cyb3rl00m.prune.ui.components.PruneCard
import io.github.cyb3rl00m.prune.ui.components.RiskBadge
import io.github.cyb3rl00m.prune.ui.components.SectionHeader
import io.github.cyb3rl00m.prune.ui.components.rememberTextExporter
import io.github.cyb3rl00m.prune.ui.theme.LocalRiskColors
import io.github.cyb3rl00m.prune.ui.theme.MonoStyle

@Composable
fun ReviewScreen(vm: AppViewModel, nav: Nav) {
    val plan by vm.draft.collectAsStateWithLifecycle()
    val exec by vm.exec.collectAsStateWithLifecycle()
    val access by vm.access.collectAsStateWithLifecycle()
    val exporter = rememberTextExporter { ok -> vm.say(str(if (ok) R.string.review_script_saved else R.string.review_script_not_saved)) }
    val p = plan
    var confirmedHigh by rememberSaveable(p?.createdAt) { mutableStateOf(false) }

    when (val e = exec) {
        is ExecState.Running -> {
            ScreenScaffold(str(R.string.review_applying), null, "applying") {
                item {
                    PruneCard {
                        Text("${e.progress.done} of ${e.progress.total}", style = MaterialTheme.typography.headlineMedium)
                        Text(e.progress.current, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(progress = { if (e.progress.total == 0) 0f else e.progress.done.toFloat() / e.progress.total },
                            modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        Text(str(R.string.review_dont_close), style = MaterialTheme.typography.bodySmall)
                    }
                }
                item { LogCard(e.progress.log.takeLast(60)) }
            }
            return
        }
        is ExecState.Done -> {
            val ok = e.batch.entries.count { it.success == true }
            val failed = e.batch.entries.filter { it.success != true }
            ScreenScaffold(str(R.string.review_finished), nav, "result",
                bottom = {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = { vm.undoBatch(e.batch) }, modifier = Modifier.weight(1f).testTag("undo_all")) { Text(str(R.string.review_undo_all)) }
                        Button(onClick = { vm.resetExec(); nav.back() }, modifier = Modifier.weight(1f).testTag("done")) { Text(str(R.string.review_done)) }
                    }
                }) {
                item {
                    PruneCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (failed.isEmpty()) Icons.Outlined.CheckCircle else Icons.Outlined.WarningAmber, null,
                                tint = if (failed.isEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(32.dp))
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(str(R.string.review_n_of_m_applied, ok, e.batch.entries.size), style = MaterialTheme.typography.titleMedium)
                                Text(str(R.string.review_verified),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                if (failed.isNotEmpty()) {
                    item { SectionHeader(str(R.string.review_not_applied_n, failed.size)) }
                    items(failed, key = { it.id }) { f ->
                        PruneCard {
                            Text(f.change.label, style = MaterialTheme.typography.titleSmall)
                            f.change.packageName?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            Text(f.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                item { SectionHeader(str(R.string.review_applied)) }
                items(e.batch.entries.filter { it.success == true }, key = { "ok" + it.id }) { s ->
                    Row(Modifier.padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(s.change.label + (if (s.message != str(R.string.exec_done)) " — ${s.message}" else ""), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            return
        }
        ExecState.Idle -> Unit
    }

    if (p == null || p.changes.isEmpty()) {
        ScreenScaffold(str(R.string.review_title_short), nav, "review") {
            item { Banner(str(R.string.review_nothing), BannerKind.INFO) }
        }
        return
    }
    val high = p.changes.count { it.needsConfirmation }
    val warned = p.changes.count { it.warnings.isNotEmpty() }
    val canApply = access.privileged && (high == 0 || confirmedHigh)

    ScreenScaffold(
        title = str(R.string.review_title), nav = nav, tag = "review", subtitle = p.title,
        bottom = {
            Column {
                if (access.privileged) {
                    Button(onClick = { vm.apply() }, enabled = canApply, modifier = Modifier.fillMaxWidth().testTag("apply")) {
                        Text(str(if (p.changes.size == 1) R.string.review_apply_one else R.string.review_apply_many, p.changes.size, access.active.label))
                    }
                } else {
                    Button(onClick = { nav.go(Routes.SETUP) }, modifier = Modifier.fillMaxWidth().testTag("review_setup")) {
                        Text(str(R.string.review_setup_shizuku))
                    }
                }
                Spacer(Modifier.height(6.dp))
                OutlinedButton(
                    onClick = {
                        vm.scriptForDraft()?.let { exporter.save("prune-plan.sh", it) }
                        vm.recordDraftAsScript()
                    },
                    modifier = Modifier.fillMaxWidth().testTag("export_script"),
                ) {
                    Icon(Icons.Outlined.Terminal, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(str(R.string.review_export_script))
                }
            }
        },
    ) {
        item {
            PruneCard {
                Text(str(if (p.changes.size == 1) R.string.review_n_change else R.string.review_n_changes, p.changes.size), style = MaterialTheme.typography.headlineMedium)
                Text(str(R.string.review_nothing_changed_yet),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Risk.entries.filter { it != Risk.LOCKED }.forEach { r ->
                        val n = p.changes.count { it.risk == r }
                        if (n > 0) Pill(str(R.string.review_n_risk, n, r.label.lowercase()), LocalRiskColors.current.of(r))
                    }
                    if (warned > 0) Pill(str(R.string.review_n_warned, warned), MaterialTheme.colorScheme.secondary)
                }
            }
        }
        if (!access.privileged) item {
            Banner(str(R.string.review_no_access),
                BannerKind.WARNING)
        }
        if (high > 0) item {
            PruneCard(color = MaterialTheme.colorScheme.errorContainer, onClick = { confirmedHigh = !confirmedHigh }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(confirmedHigh, { confirmedHigh = it }, modifier = Modifier.testTag("confirm_high"))
                    Spacer(Modifier.width(8.dp))
                    Text(str(if (high == 1) R.string.review_confirm_one else R.string.review_confirm_many, high),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }
        items(p.changes, key = { it.id }) { c -> ChangeCard(vm, c) { vm.removeFromDraft(c.id) } }
    }
}

@Composable
private fun ChangeCard(vm: AppViewModel, c: PlannedChange, onRemove: () -> Unit) {
    var expanded by rememberSaveable(c.id) { mutableStateOf(false) }
    PruneCard(padding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { expanded = !expanded }) {
            if (c.packageName != null) AppIcon(c.packageName, 36.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(c.label, style = MaterialTheme.typography.titleSmall)
                Text(listOfNotNull(c.type.label, c.packageName).joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    c.risk?.let { RiskBadge(it) }
                    if (!c.type.reversible) Pill(str(R.string.review_not_reversible), MaterialTheme.colorScheme.error)
                    if (c.fallbackUninstall) Pill(str(R.string.review_fallback), MaterialTheme.colorScheme.secondary)
                }
            }
            IconButton(onClick = onRemove) { Icon(Icons.Outlined.Close, str(R.string.cd_remove_from_plan)) }
        }
        c.warnings.forEach { w ->
            Spacer(Modifier.height(8.dp))
            Row {
                Icon(Icons.Outlined.WarningAmber, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(w, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (expanded) {
            Spacer(Modifier.height(10.dp))
            Text(c.explanation, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            SelectionContainer {
                Text(vm.commandsPreview(c).joinToString("\n"), style = MonoStyle, color = MaterialTheme.colorScheme.primary)
            }
        } else {
            TextButton(onClick = { expanded = true }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                Text(str(R.string.review_what_this_does), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
fun LogCard(lines: List<String>) {
    PruneCard(color = MaterialTheme.colorScheme.surfaceContainerLowest) {
        SelectionContainer {
            Text(lines.joinToString("\n").ifEmpty { "…" }, style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

