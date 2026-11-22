package io.github.cyb3rl00m.prune.ui.screens

import io.github.cyb3rl00m.prune.R
import io.github.cyb3rl00m.prune.core.i18n.str
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.cyb3rl00m.prune.core.history.Batch
import io.github.cyb3rl00m.prune.core.model.PackageStatus
import io.github.cyb3rl00m.prune.ui.AppViewModel
import io.github.cyb3rl00m.prune.ui.Nav
import io.github.cyb3rl00m.prune.ui.Routes
import io.github.cyb3rl00m.prune.ui.components.Banner
import io.github.cyb3rl00m.prune.ui.components.BannerKind
import io.github.cyb3rl00m.prune.ui.components.EmptyState
import io.github.cyb3rl00m.prune.ui.components.PackageRow
import io.github.cyb3rl00m.prune.ui.components.Pill
import io.github.cyb3rl00m.prune.ui.components.PruneCard
import io.github.cyb3rl00m.prune.ui.components.SectionHeader
import io.github.cyb3rl00m.prune.ui.components.rememberTextExporter
import io.github.cyb3rl00m.prune.ui.theme.MonoStyle
import java.text.DateFormat
import java.util.Date

private fun formatTime(t: Long): String = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(t))

@Composable
fun RestoreScreen(vm: AppViewModel, nav: Nav) {
    val history by vm.history.collectAsStateWithLifecycle()
    val scan by vm.scan.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val exporter = rememberTextExporter { ok -> vm.say(str(if (ok) R.string.restore_saved else R.string.restore_not_saved)) }
    val off = scan.apps.filter { it.facts.isSystem && it.status != PackageStatus.ENABLED }

    ScreenScaffold(str(R.string.tab_restore), null, "restore", subtitle = str(R.string.restore_subtitle)) {
        item {
            TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.surface) {
                listOf(str(R.string.restore_tab_history), str(R.string.restore_tab_device, off.size), str(R.string.restore_tab_tools)).forEachIndexed { i, t ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t, maxLines = 1) }, modifier = Modifier.testTag("restore_tab_$i"))
                }
            }
        }
        when (tab) {
            0 -> {
                if (history.batches.isEmpty()) item {
                    EmptyState(str(R.string.restore_no_changes), str(R.string.restore_no_changes_desc),
                        Icons.Outlined.History)
                }
                items(history.batches, key = { it.id }) { b -> BatchCard(b) { nav.go(Routes.batch(b.id)) } }
            }
            1 -> {
                item {
                    Text(str(R.string.restore_device_desc),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (off.isEmpty()) item { EmptyState(str(R.string.restore_all_on), str(R.string.restore_nothing), Icons.Outlined.CheckCircle) }
                else item {
                    Button(onClick = { if (vm.restorePackages(off)) nav.go(Routes.REVIEW) }, modifier = Modifier.fillMaxWidth().testTag("restore_all_off")) {
                        Text(str(R.string.restore_all_n, off.size))
                    }
                }
                items(off, key = { it.packageName }) { a ->
                    PackageRow(a, onClick = { nav.go(Routes.app(a.packageName)) }, trailing = {
                        TextButton(onClick = { if (vm.restorePackages(listOf(a))) nav.go(Routes.REVIEW) }) { Text(str(R.string.restore_btn)) }
                    })
                }
            }
            else -> {
                item {
                    PruneCard {
                        Text(str(R.string.vm_restore_original_title), style = MaterialTheme.typography.titleSmall)
                        Text(str(R.string.restore_originals_desc, history.originals.size),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = { if (vm.restoreAllOriginals()) nav.go(Routes.REVIEW) },
                            modifier = Modifier.fillMaxWidth().testTag("restore_originals")) { Text(str(R.string.restore_review)) }
                    }
                }
                item {
                    PruneCard {
                        Text(str(R.string.restore_emergency), style = MaterialTheme.typography.titleSmall)
                        Text(str(R.string.restore_emergency_desc),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { exporter.save("prune-restore.sh", vm.emergencyRestoreScript()) }, modifier = Modifier.weight(1f)) { Text(str(R.string.restore_save)) }
                            OutlinedButton(onClick = { exporter.share("prune-restore.sh", vm.emergencyRestoreScript()) }, modifier = Modifier.weight(1f)) { Text(str(R.string.restore_share)) }
                        }
                    }
                }
                item {
                    PruneCard {
                        Text(str(R.string.restore_export_history), style = MaterialTheme.typography.titleSmall)
                        Text(str(R.string.restore_export_history_desc), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(onClick = { exporter.save("prune-history.json", vm.repo.history.exportJson()) }, modifier = Modifier.fillMaxWidth()) { Text(str(R.string.restore_save_history)) }
                    }
                }
                item {
                    Banner(str(R.string.restore_help_banner),
                        BannerKind.INFO)
                }
            }
        }
    }
}

@Composable
private fun BatchCard(b: Batch, onClick: () -> Unit) {
    val ok = b.entries.count { it.success == true }
    val failed = b.entries.count { it.success == false }
    val pending = b.entries.count { it.success == null }
    val undone = b.entries.count { it.undone }
    PruneCard(onClick = onClick, modifier = Modifier.testTag("batch_${b.id}")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(b.title, style = MaterialTheme.typography.titleSmall)
                Text("${formatTime(b.timestamp)} · ${b.access}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (ok > 0) Pill(str(R.string.restore_n_applied, ok), MaterialTheme.colorScheme.primary)
                    if (failed > 0) Pill(str(R.string.restore_n_failed, failed), MaterialTheme.colorScheme.error)
                    if (pending > 0) Pill(str(R.string.restore_n_pending, pending), MaterialTheme.colorScheme.tertiary)
                    if (undone > 0) Pill(str(R.string.restore_n_undone, undone), MaterialTheme.colorScheme.secondary)
                }
            }
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun BatchScreen(vm: AppViewModel, nav: Nav, id: String) {
    val history by vm.history.collectAsStateWithLifecycle()
    val batch = history.batches.firstOrNull { it.id == id }
    if (batch == null) {
        ScreenScaffold(str(R.string.restore_batch), nav, "batch") { item { Banner(str(R.string.restore_not_found), BannerKind.INFO) } }
        return
    }
    val undoable = batch.entries.any { it.success == true && !it.undone && it.change.type.reversible }
    ScreenScaffold(batch.title, nav, "batch", subtitle = formatTime(batch.timestamp),
        bottom = {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (batch.script) OutlinedButton(onClick = { vm.verifyScript(batch) }, modifier = Modifier.weight(1f)) { Text(str(R.string.restore_check_results)) }
                Button(onClick = { if (vm.undoBatch(batch)) nav.go(Routes.REVIEW) }, enabled = undoable, modifier = Modifier.weight(1f).testTag("undo_batch")) {
                    Icon(Icons.AutoMirrored.Outlined.Undo, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(str(R.string.restore_undo))
                }
            }
        }) {
        item {
            Text(str(R.string.restore_origin, batch.origin, batch.access), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(batch.entries, key = { it.id }) { e ->
            PruneCard(padding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        when (e.success) { true -> Icons.Outlined.CheckCircle; false -> Icons.Outlined.ErrorOutline; null -> Icons.Outlined.HourglassEmpty },
                        null,
                        tint = when (e.success) { true -> MaterialTheme.colorScheme.primary; false -> MaterialTheme.colorScheme.error; null -> MaterialTheme.colorScheme.tertiary },
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(e.change.label, style = MaterialTheme.typography.titleSmall)
                        Text(listOfNotNull(e.change.type.label, e.change.packageName).joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (e.undone) Pill(str(R.string.restore_undone), MaterialTheme.colorScheme.secondary)
                    else if (e.success == true && e.change.type.reversible) TextButton(onClick = {
                        if (vm.undoEntries(listOf(e), str(R.string.vm_undo_title, e.change.label))) nav.go(Routes.REVIEW)
                    }) { Text(str(R.string.restore_undo)) }
                }
                Spacer(Modifier.height(6.dp))
                Text(str(R.string.restore_before_after, e.before ?: "—", e.after ?: "—"), style = MaterialTheme.typography.bodySmall)
                Text(e.message, style = MaterialTheme.typography.bodySmall, color = if (e.success == false) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                if (e.commands.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    SelectionContainer { Text(e.commands.joinToString("\n"), style = MonoStyle, color = MaterialTheme.colorScheme.primary) }
                }
            }
        }
        item { SectionHeader("") }
    }
}
