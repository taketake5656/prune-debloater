package io.github.cyb3rl00m.prune.ui.screens

import io.github.cyb3rl00m.prune.R
import io.github.cyb3rl00m.prune.core.i18n.str
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.TipsAndUpdates
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.cyb3rl00m.prune.core.model.PackageStatus
import io.github.cyb3rl00m.prune.core.plan.DebloatLevel
import io.github.cyb3rl00m.prune.core.plan.PlanBuilder
import io.github.cyb3rl00m.prune.core.shell.AccessKind
import io.github.cyb3rl00m.prune.ui.AppViewModel
import io.github.cyb3rl00m.prune.ui.Nav
import io.github.cyb3rl00m.prune.ui.Routes
import io.github.cyb3rl00m.prune.ui.components.Banner
import io.github.cyb3rl00m.prune.ui.components.BannerKind
import io.github.cyb3rl00m.prune.ui.components.Pill
import io.github.cyb3rl00m.prune.ui.components.PruneCard
import io.github.cyb3rl00m.prune.ui.components.SectionHeader
import io.github.cyb3rl00m.prune.ui.components.StatTile

@Composable
fun HomeScreen(vm: AppViewModel, nav: Nav) {
    val device by vm.device.collectAsStateWithLifecycle()
    val scan by vm.scan.collectAsStateWithLifecycle()
    val access by vm.access.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val apps = scan.apps
    val installed = apps.count { it.facts.installedForUser }
    val system = apps.count { it.facts.isSystem }
    val removable = apps.count { !it.isLocked && it.status == PackageStatus.ENABLED && (it.classification.level ?: 9) <= 3 && it.facts.isSystem }
    val changed = apps.count { it.facts.isSystem && it.status != PackageStatus.ENABLED }
    val (done, total) = remember(apps, settings.progressLevel) { PlanBuilder.progress(apps, settings.progressLevel) }
    val recommendations by produceState(emptyList<io.github.cyb3rl00m.prune.core.optimize.Recommendation>(), apps, scan.usage, access.privileged) {
        value = if (apps.isEmpty()) emptyList() else kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            vm.repo.optimizer.recommendations(apps, scan.usage, access.privileged)
        }
    }

    ScreenScaffold(
        title = "Prune", nav = null, tag = "home",
        subtitle = str(R.string.home_subtitle),
        actions = {
            IconButton(onClick = { vm.rescan() }, modifier = Modifier.testTag("rescan")) { Icon(Icons.Outlined.Refresh, str(R.string.cd_scan_again)) }
            IconButton(onClick = { nav.go(Routes.SETTINGS) }, modifier = Modifier.testTag("open_settings")) { Icon(Icons.Outlined.Settings, str(R.string.cd_settings)) }
        },
    ) {
        item {
            val brush = Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                MaterialTheme.colorScheme.secondary.copy(alpha = 0.20f)))
            PruneCard(onClick = { nav.go(Routes.COMPAT) }, padding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                modifier = Modifier.testTag("device_card")) {
                Box(Modifier.fillMaxWidth().background(brush).padding(20.dp)) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Smartphone, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(device?.brandName ?: str(R.string.home_detecting_device),
                                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f))
                            device?.let { Pill(it.compat.label, compatColor(it.compat)) }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(device?.fullName ?: str(R.string.home_reading_device), style = MaterialTheme.typography.headlineMedium)
                        Text(device?.model ?: "", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            device?.let { d ->
                                Pill("Android ${d.androidVersion}", MaterialTheme.colorScheme.onSurface)
                                d.firmware?.takeIf { it.name != "Android" }?.let { Pill(it.toString(), MaterialTheme.colorScheme.onSurface) }
                                d.cscCode?.let { Pill("CSC $it", MaterialTheme.colorScheme.onSurface) }
                                if (!d.services.gms) Pill(str(R.string.lim_no_gms_t), MaterialTheme.colorScheme.tertiary)
                            }
                        }
                    }
                }
            }
        }

        device?.takeIf { it.compat == io.github.cyb3rl00m.prune.core.oem.CompatLevel.EXPERIMENTAL ||
            it.compat == io.github.cyb3rl00m.prune.core.oem.CompatLevel.GENERIC }?.let { d ->
            item {
                Banner(
                    if (d.compat == io.github.cyb3rl00m.prune.core.oem.CompatLevel.EXPERIMENTAL)
                        str(R.string.home_banner_experimental, d.integration.title)
                    else str(R.string.home_banner_generic, d.brandName),
                    BannerKind.WARNING,
                    action = { androidx.compose.material3.TextButton(onClick = { nav.go(Routes.COMPAT) }) { Text(str(R.string.home_compat_report)) } },
                )
            }
        }

        item {
            AccessCard(access.active, access.privileged) { nav.go(Routes.SETUP) }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(if (scan.scanning && apps.isEmpty()) "…" else installed.toString(), str(R.string.home_stat_installed), Icons.Outlined.Apps, Modifier.weight(1f))
                StatTile(system.toString(), str(R.string.home_stat_system), Icons.Outlined.Inventory2, Modifier.weight(1f), MaterialTheme.colorScheme.secondary)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(removable.toString(), str(R.string.home_stat_removable), Icons.Outlined.ContentCut, Modifier.weight(1f), MaterialTheme.colorScheme.tertiary)
                StatTile(changed.toString(), str(R.string.home_stat_changed), Icons.Outlined.CheckCircle, Modifier.weight(1f))
            }
        }

        item {
            PruneCard {
                Text(str(R.string.home_progress_title), style = MaterialTheme.typography.titleMedium)
                Text(str(R.string.home_progress_desc), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(DebloatLevel.MINIMAL, DebloatLevel.BALANCED, DebloatLevel.AGGRESSIVE).forEach { l ->
                        FilterChip(selected = settings.progressLevel == l.number, onClick = { vm.update { setProgressLevel(l.number) } },
                            label = { Text(l.title) })
                    }
                }
                Spacer(Modifier.height(10.dp))
                val fraction = if (total == 0) 0f else done.toFloat() / total
                LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant)
                Spacer(Modifier.height(8.dp))
                Text(str(R.string.home_progress_value, done, total, (fraction * 100).toInt()), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { nav.tab(Routes.DEBLOAT) }, modifier = Modifier.fillMaxWidth().testTag("home_debloat")) {
                    Icon(Icons.Outlined.ContentCut, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(str(R.string.home_choose_level))
                }
            }
        }

        if (scan.duplicates.isNotEmpty()) item {
            PruneCard(onClick = { nav.go(Routes.DUPLICATES) }, modifier = Modifier.testTag("duplicates_card")) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Apps, null, tint = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(str(R.string.home_duplicates, scan.duplicates.size), style = MaterialTheme.typography.titleSmall)
                        Text(scan.duplicates.take(3).joinToString { it.name } + if (scan.duplicates.size > 3) "…" else "",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item { SectionHeader(str(R.string.home_recommendations)) }
        if (recommendations.isEmpty()) item {
            PruneCard { Text(if (scan.scanning) str(R.string.home_scanning) else str(R.string.home_no_recommendations), style = MaterialTheme.typography.bodyMedium) }
        }
        recommendations.forEach { r ->
            item {
                PruneCard(onClick = { nav.go(Routes.section(r.section)) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.TipsAndUpdates, null, tint = MaterialTheme.colorScheme.tertiary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(r.title, style = MaterialTheme.typography.titleSmall)
                            Text(r.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        scan.error?.let { e -> item { Banner(str(R.string.home_scan_failed, e), BannerKind.ERROR) } }
        item {
            Text(
                if (scan.lastScan == 0L) str(R.string.home_scanning_packages) else str(R.string.home_last_scan, java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(scan.lastScan)), vm.repo.database.updated),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, start = 4.dp),
            )
        }
    }
}

@Composable
fun AccessCard(active: AccessKind, privileged: Boolean, onSetup: () -> Unit) {
    PruneCard(onClick = onSetup, color = if (privileged) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.secondaryContainer) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Shield, null, tint = if (privileged) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(if (privileged) str(R.string.home_access, active.label) else str(R.string.home_limited_access), style = MaterialTheme.typography.titleSmall)
                Text(
                    if (privileged) str(R.string.home_access_full)
                    else str(R.string.home_access_limited_desc),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (!privileged) OutlinedButton(onClick = onSetup, modifier = Modifier.testTag("setup_access")) { Text(str(R.string.home_set_up)) }
        }
    }
}
