package io.github.cyb3rl00m.prune.ui.screens

import io.github.cyb3rl00m.prune.R
import io.github.cyb3rl00m.prune.core.i18n.str
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DoNotDisturbOn
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.cyb3rl00m.prune.BuildConfig
import io.github.cyb3rl00m.prune.core.oem.CompatLevel
import io.github.cyb3rl00m.prune.core.oem.OpStatus
import io.github.cyb3rl00m.prune.ui.AppViewModel
import io.github.cyb3rl00m.prune.ui.Nav
import io.github.cyb3rl00m.prune.ui.Routes
import io.github.cyb3rl00m.prune.ui.components.Banner
import io.github.cyb3rl00m.prune.ui.components.BannerKind
import io.github.cyb3rl00m.prune.ui.components.EmptyState
import io.github.cyb3rl00m.prune.ui.components.KeyValue
import io.github.cyb3rl00m.prune.ui.components.PackageRow
import io.github.cyb3rl00m.prune.ui.components.Pill
import io.github.cyb3rl00m.prune.ui.components.PruneCard
import io.github.cyb3rl00m.prune.ui.components.SectionHeader
import io.github.cyb3rl00m.prune.ui.components.rememberTextExporter

@Composable
fun compatColor(level: CompatLevel): Color = when (level) {
    CompatLevel.TESTED -> MaterialTheme.colorScheme.primary
    CompatLevel.PARTIAL -> Color(0xFF5CC8FF)
    CompatLevel.EXPERIMENTAL -> MaterialTheme.colorScheme.tertiary
    CompatLevel.GENERIC -> MaterialTheme.colorScheme.secondary
    CompatLevel.UNSUPPORTED -> MaterialTheme.colorScheme.error
}

@Composable
fun CompatibilityScreen(vm: AppViewModel, nav: Nav) {
    val device by vm.device.collectAsStateWithLifecycle()
    val access by vm.access.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val exporter = rememberTextExporter()
    val report = remember(device, access, history) { vm.compatibilityReport() }
    ScreenScaffold(str(R.string.compat_title), nav, "compat", subtitle = str(R.string.compat_subtitle),
        actions = {
            if (report != null) IconButton(onClick = { exporter.share("prune-compatibility.txt", report.asText(BuildConfig.VERSION_NAME)) },
                modifier = Modifier.testTag("share_report")) { Icon(Icons.Outlined.ContentCopy, str(R.string.cd_share_report)) }
        }) {
        if (report == null) {
            item { Text(str(R.string.home_reading_device)) }
            return@ScreenScaffold
        }
        val level = report.integration.compat
        item {
            PruneCard {
                Text(report.device, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill(level.label, compatColor(level))
                }
                Spacer(Modifier.height(8.dp))
                Text(report.integration.compatNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                KeyValue(str(R.string.compat_kv_manufacturer), report.manufacturer)
                KeyValue(str(R.string.compat_kv_integration), report.integration.title)
                KeyValue("Android", "${report.androidVersion} (API ${report.sdkInt})")
                KeyValue(str(R.string.compat_kv_firmware), report.firmware?.toString() ?: str(R.string.compat_not_detected))
                KeyValue(str(R.string.compat_kv_gms), if (report.services.gms) str(R.string.compat_installed) else str(R.string.compat_not_available))
                if (report.services.hms) KeyValue("Huawei Mobile Services", str(R.string.compat_installed))
                if (report.services.honorServices) KeyValue("Honor Mobile Services", str(R.string.compat_installed))
            }
        }
        item { SectionHeader(str(R.string.compat_access)) }
        item {
            PruneCard {
                KeyValue(str(R.string.compat_kv_tier), report.access.tier)
                KeyValue("Shizuku", when {
                    report.access.shizukuAuthorized -> str(R.string.compat_shizuku_authorized)
                    report.access.shizukuRunning -> str(R.string.compat_shizuku_running)
                    report.access.shizukuInstalled -> str(R.string.compat_shizuku_installed)
                    else -> str(R.string.compat_shizuku_not_installed)
                })
                KeyValue(str(R.string.access_root), if (report.access.root) str(R.string.compat_granted) else str(R.string.compat_not_granted))
                KeyValue(str(R.string.perm_usage_access), if (report.access.usageAccess) str(R.string.compat_granted) else str(R.string.compat_not_granted))
                KeyValue(str(R.string.perm_write_secure_settings), if (report.access.writeSecureSettings) str(R.string.compat_granted) else str(R.string.compat_not_granted))
                if (!report.access.privileged) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { nav.go(Routes.SETUP) }, modifier = Modifier.fillMaxWidth()) { Text(str(R.string.compat_setup_access)) }
                }
            }
        }
        item { SectionHeader(str(R.string.compat_operations)) }
        items(report.operations, key = { it.operation.name }) { o ->
            val (icon, tint) = when (o.status) {
                OpStatus.AVAILABLE -> Icons.Outlined.CheckCircle to MaterialTheme.colorScheme.primary
                OpStatus.NEEDS_ACCESS, OpStatus.NEEDS_USAGE -> Icons.Outlined.Lock to MaterialTheme.colorScheme.onSurfaceVariant
                OpStatus.UNSUPPORTED -> Icons.Outlined.DoNotDisturbOn to MaterialTheme.colorScheme.error
            }
            PruneCard(padding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(o.operation.label, style = MaterialTheme.typography.titleSmall)
                        Text(str(R.string.compat_requires, o.status.label, o.operation.requirement.label.lowercase()),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        o.caveat?.let {
                            Spacer(Modifier.height(4.dp))
                            Row {
                                Icon(Icons.Outlined.WarningAmber, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(14.dp).padding(top = 2.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(it, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        if (o.succeeded + o.failed > 0) {
                            Spacer(Modifier.height(4.dp))
                            Text(str(R.string.compat_on_device, o.succeeded, o.failed), style = MaterialTheme.typography.labelSmall,
                                color = if (o.failed > 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary)
                            o.lastError?.let { Text(str(R.string.compat_last_error, it), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
        }
        if (report.limitations.isNotEmpty()) {
            item { SectionHeader(str(R.string.compat_known_limitations)) }
            items(report.limitations, key = { "lim" + it.title }) { l ->
                PruneCard {
                    Text(l.title, style = MaterialTheme.typography.titleSmall)
                    Text(l.text, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            Banner(str(R.string.compat_levels_banner),
                BannerKind.INFO)
        }
    }
}

@Composable
fun DuplicatesScreen(vm: AppViewModel, nav: Nav) {
    val scan by vm.scan.collectAsStateWithLifecycle()
    val byPkg = remember(scan.apps) { scan.apps.associateBy { it.packageName } }
    ScreenScaffold(str(R.string.dup_title), nav, "duplicates", subtitle = str(R.string.dup_subtitle)) {
        item {
            Text(str(R.string.dup_desc),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (scan.duplicates.isEmpty()) item {
            EmptyState(str(R.string.dup_none), str(R.string.dup_none_desc), Icons.Outlined.CheckCircle)
        }
        scan.duplicates.forEach { g ->
            item(key = "h" + g.name) { SectionHeader("${g.name} (${g.packages.size})") }
            items(g.packages.mapNotNull { byPkg[it] }, key = { g.name + it.packageName }) { a ->
                PackageRow(a, subtitle = if (a.packageName == g.defaultPackage) str(R.string.dup_default_app, a.classification.sourceLabel) else a.classification.sourceLabel,
                    onClick = { nav.go(Routes.app(a.packageName)) })
            }
        }
    }
}
