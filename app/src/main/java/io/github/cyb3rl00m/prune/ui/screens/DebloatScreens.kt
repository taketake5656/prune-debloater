package io.github.cyb3rl00m.prune.ui.screens

import io.github.cyb3rl00m.prune.R
import io.github.cyb3rl00m.prune.core.i18n.str
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.cyb3rl00m.prune.core.model.RemovalMethod
import io.github.cyb3rl00m.prune.core.plan.DebloatLevel
import io.github.cyb3rl00m.prune.core.plan.PlanBuilder
import io.github.cyb3rl00m.prune.ui.AppViewModel
import io.github.cyb3rl00m.prune.ui.Nav
import io.github.cyb3rl00m.prune.ui.Routes
import io.github.cyb3rl00m.prune.ui.components.Banner
import io.github.cyb3rl00m.prune.ui.components.BannerKind
import io.github.cyb3rl00m.prune.ui.components.PackageRow
import io.github.cyb3rl00m.prune.ui.components.PruneCard
import io.github.cyb3rl00m.prune.ui.components.SectionHeader

@Composable
fun DebloatScreen(vm: AppViewModel, nav: Nav) {
    val scan by vm.scan.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val used = io.github.cyb3rl00m.prune.core.settings.SettingsStore.effectiveUsed(settings, vm.allEcosystemKeys)
    val counts = remember(scan.apps, used) {
        DebloatLevel.entries.associateWith { l ->
            PlanBuilder.candidates(scan.apps, io.github.cyb3rl00m.prune.core.plan.ProfileOptions.defaults(l, used))
                .count { it.keptBecause == null && it.selected }
        }
    }
    ScreenScaffold(str(R.string.tab_debloat), null, "debloat", subtitle = str(R.string.debloat_subtitle)) {
        item {
            Banner(str(R.string.debloat_banner),
                BannerKind.INFO)
        }
        DebloatLevel.entries.forEach { level ->
            item {
                LevelCard(level, counts[level] ?: 0) {
                    vm.startProfile(level)
                    nav.go(Routes.customize(level))
                }
            }
        }
    }
}

@Composable
private fun LevelCard(level: DebloatLevel, count: Int, onClick: () -> Unit) {
    val accent = when (level) {
        DebloatLevel.MINIMAL -> MaterialTheme.colorScheme.primary
        DebloatLevel.BALANCED -> androidx.compose.ui.graphics.Color(0xFF5CC8FF)
        DebloatLevel.AGGRESSIVE -> MaterialTheme.colorScheme.tertiary
        DebloatLevel.CUSTOM -> MaterialTheme.colorScheme.error
    }
    PruneCard(onClick = onClick, modifier = Modifier.testTag("level_${level.number}")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(accent.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                Text(level.number.toString(), style = MaterialTheme.typography.titleLarge, color = accent, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(level.title, style = MaterialTheme.typography.titleMedium)
                Text(level.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(count.toString(), style = MaterialTheme.typography.titleLarge, color = accent)
                Text(if (level == DebloatLevel.CUSTOM) str(R.string.debloat_preselected) else str(R.string.debloat_packages), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(10.dp))
        level.details.forEach { d ->
            Row(Modifier.padding(vertical = 2.dp)) {
                Icon(Icons.Outlined.Check, null, Modifier.size(16.dp).padding(top = 2.dp), tint = accent)
                Spacer(Modifier.width(8.dp))
                Text(d, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun CustomizeScreen(vm: AppViewModel, nav: Nav, level: DebloatLevel) {
    val profile by vm.profile.collectAsStateWithLifecycle()
    val scan by vm.scan.collectAsStateWithLifecycle()
    LaunchedEffect(level) { if (vm.profile.value?.options?.level != level) vm.startProfile(level) }
    LaunchedEffect(scan.lastScan) { vm.profile.value?.let { vm.updateProfileOptions(it.options) } }
    val p = profile ?: return
    val o = p.options
    var showKept by rememberSaveable { mutableStateOf(false) }
    var showUnselected by rememberSaveable { mutableStateOf(level == DebloatLevel.CUSTOM) }
    val active = p.candidates.filter { it.keptBecause == null }
    val selected = active.filter { p.isSelected(it) }
    val unselected = active.filterNot { p.isSelected(it) }
    val kept = p.candidates.filter { it.keptBecause != null }
    val bgCount = if (o.restrictUnusedBackground) p.backgroundApps.size else 0

    ScreenScaffold(
        title = str(R.string.vm_profile_title, level.title), nav = nav, tag = "customize",
        subtitle = str(R.string.debloat_n_selected, selected.size),
        bottom = {
            Button(
                onClick = { vm.reviewProfile(); nav.go(Routes.REVIEW) },
                enabled = selected.isNotEmpty() || bgCount > 0,
                modifier = Modifier.fillMaxWidth().testTag("review_changes"),
            ) { Text(str(R.string.debloat_review_n, selected.size + bgCount)) }
        },
    ) {
        item { SectionHeader(str(R.string.debloat_features)) }
        item {
            PruneCard {
                Text(str(R.string.debloat_features_desc),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    // この端末に存在する機能だけを表示する。
                    val present = remember(scan.apps) {
                        scan.apps.filter { it.facts.installedForUser }.flatMap { it.classification.groups }.toSet()
                    }
                    vm.ecosystems.filter { it.key in present }.forEach { eco ->
                        val on = eco.key in o.usedEcosystems
                        FilterChip(
                            selected = on,
                            onClick = {
                                vm.updateProfileOptions(o.copy(usedEcosystems = if (on) o.usedEcosystems - eco.key else o.usedEcosystems + eco.key))
                            },
                            label = { Text(eco.name) },
                            leadingIcon = if (on) ({ Icon(Icons.Outlined.Check, null, Modifier.size(16.dp)) }) else null,
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer),
                            modifier = Modifier.testTag("eco_${eco.key}"),
                        )
                    }
                }
            }
        }
        item { SectionHeader(str(R.string.debloat_options)) }
        item {
            PruneCard {
                OptionSwitch(str(R.string.debloat_include_google), str(R.string.debloat_include_google_desc),
                    o.includeGoogle) { vm.updateProfileOptions(o.copy(includeGoogle = it)) }
                OptionSwitch(str(R.string.debloat_restrict_bg), if (scan.usage.isEmpty())
                    str(R.string.debloat_restrict_bg_needs) else str(R.string.debloat_restrict_bg_count, p.backgroundApps.size),
                    o.restrictUnusedBackground) { vm.updateProfileOptions(o.copy(restrictUnusedBackground = it)) }
                Spacer(Modifier.height(8.dp))
                Text(str(R.string.debloat_method), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    RemovalMethod.entries.forEachIndexed { i, m ->
                        SegmentedButton(selected = o.method == m, onClick = { vm.updateProfileOptions(o.copy(method = m)) },
                            shape = SegmentedButtonDefaults.itemShape(i, RemovalMethod.entries.size)) { Text(m.label, maxLines = 1) }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(o.method.explanation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (level == DebloatLevel.CUSTOM) item {
            Banner(str(R.string.debloat_custom_banner),
                BannerKind.WARNING)
        }
        item {
            SectionHeader(str(R.string.debloat_selected_n, selected.size)) {
                TextButton(onClick = { vm.setAllCandidates(false) }) { Text(str(R.string.debloat_none)) }
            }
        }
        if (selected.isEmpty()) item {
            PruneCard { Text(str(R.string.debloat_nothing_selected), style = MaterialTheme.typography.bodyMedium) }
        }
        items(selected, key = { "s_" + it.app.packageName }) { c ->
            CandidateRow(c.app, true, c.note) { vm.toggleCandidate(c.app.packageName, it) }
        }
        if (unselected.isNotEmpty()) {
            item {
                ExpandHeader(str(R.string.debloat_not_preselected_n, unselected.size), showUnselected) { showUnselected = !showUnselected }
            }
            if (showUnselected) items(unselected, key = { "u_" + it.app.packageName }) { c ->
                CandidateRow(c.app, false, c.note) { vm.toggleCandidate(c.app.packageName, it) }
            }
        }
        if (kept.isNotEmpty()) {
            item { ExpandHeader(str(R.string.debloat_kept_n, kept.size), showKept) { showKept = !showKept } }
            if (showKept) items(kept, key = { "k_" + it.app.packageName }) { c ->
                PackageRow(c.app, subtitle = c.keptBecause, onClick = { nav.go(Routes.app(c.app.packageName)) })
            }
        }
    }
}

@Composable
private fun CandidateRow(app: io.github.cyb3rl00m.prune.core.model.AppPackage, checked: Boolean, note: String?, onChange: (Boolean) -> Unit) {
    PackageRow(
        app, subtitle = note ?: app.packageName,
        modifier = Modifier.testTag("cand_${app.packageName}"),
        onClick = { onChange(!checked) },
        trailing = { Checkbox(checked = checked, onCheckedChange = onChange) },
    )
}

@Composable
fun ExpandHeader(title: String, expanded: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable(onClick = onClick).padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(title.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun OptionSwitch(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
