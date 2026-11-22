package io.github.cyb3rl00m.prune.ui.screens

import io.github.cyb3rl00m.prune.R
import io.github.cyb3rl00m.prune.core.i18n.str
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.cyb3rl00m.prune.BuildConfig
import io.github.cyb3rl00m.prune.core.model.RemovalMethod
import io.github.cyb3rl00m.prune.core.settings.ThemeMode
import io.github.cyb3rl00m.prune.ui.AppViewModel
import io.github.cyb3rl00m.prune.ui.Nav
import io.github.cyb3rl00m.prune.ui.Routes
import io.github.cyb3rl00m.prune.ui.components.Banner
import io.github.cyb3rl00m.prune.ui.components.BannerKind
import io.github.cyb3rl00m.prune.ui.components.KeyValue
import io.github.cyb3rl00m.prune.ui.components.PruneCard
import io.github.cyb3rl00m.prune.ui.components.SectionHeader
import io.github.cyb3rl00m.prune.ui.theme.MonoStyle

@Composable
private fun StatusLine(done: Boolean, title: String, detail: String) {
    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
        Icon(if (done) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked, null,
            tint = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SetupScreen(vm: AppViewModel, nav: Nav) {
    val access by vm.access.collectAsStateWithLifecycle()
    val device by vm.device.collectAsStateWithLifecycle()
    val context = LocalContext.current
    fun open(intent: Intent) = try { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: ActivityNotFoundException) {
        vm.say(str(R.string.setup_cannot_open))
    }

    ScreenScaffold(str(R.string.setup_title), nav, "setup", subtitle = str(R.string.setup_subtitle)) {
        item {
            PruneCard {
                Text(str(R.string.setup_three_levels), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                StatusLine(true, str(R.string.setup_level1), str(R.string.setup_level1_desc))
                StatusLine(access.active.name == "SHIZUKU" || access.rootGranted,
                    str(R.string.setup_level2),
                    str(R.string.setup_level2_desc))
                StatusLine(access.rootGranted, str(R.string.setup_level3),
                    str(R.string.setup_level3_desc))
            }
        }
        item { SectionHeader(str(R.string.setup_shizuku_header)) }
        item {
            PruneCard {
                StatusLine(access.shizukuInstalled, str(R.string.setup_shizuku_installed), str(R.string.setup_shizuku_installed_desc))
                StatusLine(access.shizukuRunning, str(R.string.setup_shizuku_running), if (access.shizukuRunning) str(R.string.setup_running_as, if (access.shizukuUid == 0) "root" else "ADB (shell)") else str(R.string.setup_start_it))
                StatusLine(access.shizukuPermission, str(R.string.setup_prune_authorized), str(R.string.setup_prune_authorized_desc))
                if (access.shizukuTooOld) Banner(str(R.string.setup_shizuku_too_old), BannerKind.ERROR)
                Spacer(Modifier.height(8.dp))
                when {
                    !access.shizukuInstalled -> Button(onClick = {
                        open(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=moe.shizuku.privileged.api")).let {
                            if (it.resolveActivity(context.packageManager) != null) it
                            else Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/RikkaApps/Shizuku/releases"))
                        })
                    }, modifier = Modifier.fillMaxWidth()) { Text(str(R.string.setup_get_shizuku)) }
                    !access.shizukuRunning -> Button(onClick = {
                        context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")?.let { open(it) }
                    }, modifier = Modifier.fillMaxWidth()) { Text(str(R.string.setup_open_shizuku)) }
                    !access.shizukuPermission -> Button(onClick = { vm.requestShizuku() }, modifier = Modifier.fillMaxWidth().testTag("authorize_shizuku")) {
                        Text(str(R.string.setup_authorize))
                    }
                    else -> OutlinedButton(onClick = { vm.refreshAccess() }, modifier = Modifier.fillMaxWidth()) { Text(str(R.string.setup_ready_refresh)) }
                }
            }
        }
        item {
            PruneCard {
                Text(str(R.string.setup_start_on, device?.brandName ?: str(R.string.setup_this_phone)), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                val integration = vm.repo.integration
                val identity = vm.repo.identity
                (integration.developerOptionsSteps + listOf(
                    str(R.string.setup_step_wifi),
                    str(R.string.setup_step_pairing),
                    str(R.string.setup_step_code),
                    str(R.string.setup_step_start),
                    str(R.string.setup_step_reboot),
                ) + runCatching { integration.extraSetupSteps(identity) }.getOrDefault(emptyList())).forEachIndexed { i, s -> Text("${i + 1}. $s", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 3.dp)) }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { open(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) }, modifier = Modifier.fillMaxWidth()) {
                    Text(str(R.string.setup_open_dev_options))
                }
                Spacer(Modifier.height(8.dp))
                Text(str(R.string.setup_with_computer), style = MaterialTheme.typography.bodySmall)
                SelectionContainer { Text("adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh", style = MonoStyle, color = MaterialTheme.colorScheme.primary) }
            }
        }
        item { SectionHeader(str(R.string.setup_extra_perms)) }
        item {
            PruneCard {
                StatusLine(access.usageAccess, str(R.string.perm_usage_access), str(R.string.setup_usage_desc))
                StatusLine(access.writeSecureSettings, str(R.string.perm_write_secure_settings), str(R.string.setup_wss_desc))
                Spacer(Modifier.height(8.dp))
                if (access.privileged && (!access.usageAccess || !access.writeSecureSettings)) {
                    Button(onClick = { vm.grantSelfPermissions() }, modifier = Modifier.fillMaxWidth().testTag("grant_self")) { Text(str(R.string.setup_grant_both, access.active.label)) }
                } else if (!access.usageAccess) {
                    Button(onClick = {
                        open(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:${context.packageName}")).let {
                            if (it.resolveActivity(context.packageManager) != null) it else Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                        })
                    }, modifier = Modifier.fillMaxWidth()) { Text(str(R.string.setup_open_usage_settings)) }
                }
                if (!access.writeSecureSettings && !access.privileged) {
                    Spacer(Modifier.height(8.dp))
                    Text(str(R.string.setup_or_computer), style = MaterialTheme.typography.bodySmall)
                    SelectionContainer { Text("adb shell pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS", style = MonoStyle, color = MaterialTheme.colorScheme.primary) }
                }
            }
        }
        item { SectionHeader(str(R.string.setup_root_header)) }
        item {
            PruneCard {
                StatusLine(access.rootGranted, str(R.string.setup_root_access), str(R.string.setup_root_desc))
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { vm.requestRoot() }, modifier = Modifier.fillMaxWidth()) { Text(str(R.string.setup_request_root)) }
            }
        }
        item { SectionHeader(str(R.string.setup_without_header)) }
        item {
            Banner(str(R.string.setup_without_desc), BannerKind.INFO)
        }
    }
}

@Composable
fun SettingsScreen(vm: AppViewModel, nav: Nav) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    ScreenScaffold(str(R.string.cd_settings), nav, "settings") {
        item { SectionHeader(str(R.string.settings_appearance)) }
        item {
            PruneCard {
                ThemeMode.entries.forEach { m ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(s.theme == m, { vm.update { setTheme(m) } })
                        Text(m.label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
        item { SectionHeader(str(R.string.settings_debloating)) }
        item {
            PruneCard {
                Text(str(R.string.settings_default_method), style = MaterialTheme.typography.titleSmall)
                RemovalMethod.entries.forEach { m ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                        RadioButton(s.method == m, { vm.update { setMethod(m) } })
                        Column {
                            Text(m.label, style = MaterialTheme.typography.bodyLarge)
                            Text(m.explanation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                OptionSwitch(str(R.string.settings_fallback), str(R.string.settings_fallback_desc),
                    s.fallbackUninstall) { vm.update { setFallback(it) } }
                OptionSwitch(str(R.string.settings_skip_recent), str(R.string.settings_skip_recent_desc, s.skipUsedWithinDays),
                    s.skipUsedWithinDays > 0) { vm.update { setSkipDays(if (it) 14 else 0) } }
                OptionSwitch(str(R.string.settings_prefer_root), str(R.string.settings_prefer_root_desc), s.preferRoot) { vm.update { setPreferRoot(it) } }
            }
        }
        item { SectionHeader(str(R.string.settings_about)) }
        item {
            PruneCard {
                KeyValue(str(R.string.apps_kv_version), BuildConfig.VERSION_NAME)
                KeyValue(str(R.string.settings_package_db), str(R.string.settings_package_db_value, vm.repo.database.updated, vm.repo.database.uad.size, vm.repo.database.curatedCount, vm.repo.database.files.size))
                KeyValue(str(R.string.settings_license), "GPL-3.0-or-later")
                Spacer(Modifier.height(8.dp))
                Text(str(R.string.settings_credits),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/cyb3rl00m/prune-debloater"))) }
                }, modifier = Modifier.fillMaxWidth()) { Text(str(R.string.settings_source)) }
                Spacer(Modifier.height(6.dp))
                OutlinedButton(onClick = { nav.go(Routes.SETUP) }, modifier = Modifier.fillMaxWidth()) { Text(str(R.string.setup_title)) }
                Spacer(Modifier.height(6.dp))
                OutlinedButton(onClick = { nav.go(Routes.DEVICE) }, modifier = Modifier.fillMaxWidth()) { Text(str(R.string.device_title)) }
                Spacer(Modifier.height(6.dp))
                OutlinedButton(onClick = { nav.go(Routes.COMPAT) }, modifier = Modifier.fillMaxWidth().testTag("open_compat")) { Text(str(R.string.home_compat_report)) }
            }
        }
        item {
            Text(str(R.string.settings_disclaimer),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(4.dp))
        }
    }
}

@Composable
fun DeviceScreen(vm: AppViewModel, nav: Nav) {
    val d by vm.device.collectAsStateWithLifecycle()
    val access by vm.access.collectAsStateWithLifecycle()
    val scan by vm.scan.collectAsStateWithLifecycle()
    ScreenScaffold(str(R.string.device_title), nav, "device") {
        val dev = d
        if (dev == null) {
            item { Text(str(R.string.device_reading)) }
            return@ScreenScaffold
        }
        item {
            PruneCard {
                KeyValue(str(R.string.device_model), dev.fullName)
                KeyValue(str(R.string.device_model_number), dev.model)
                KeyValue(str(R.string.device_code), dev.device)
                KeyValue(str(R.string.compat_kv_manufacturer), dev.manufacturer)
                KeyValue(str(R.string.device_brand), dev.brand)
                KeyValue("Android", "${dev.androidVersion} (API ${dev.sdkInt})")
                KeyValue(str(R.string.compat_kv_firmware), dev.firmware?.toString() ?: str(R.string.compat_not_detected))
                KeyValue(str(R.string.compat_kv_integration), "${dev.integration.title} · ${dev.compat.label}")
                KeyValue(str(R.string.compat_kv_gms), if (dev.services.gms) str(R.string.compat_installed) else str(R.string.compat_not_available))
                KeyValue(str(R.string.device_build), dev.buildNumber)
                KeyValue(str(R.string.device_security_patch), dev.securityPatch)
                KeyValue(str(R.string.device_csc), listOfNotNull(dev.cscCode, dev.cscCountry).joinToString(" · ").ifEmpty { str(R.string.risk_unknown) })
                KeyValue(str(R.string.source_carrier), dev.carrier ?: str(R.string.device_no_sim))
                KeyValue("RAM", io.github.cyb3rl00m.prune.core.plan.Executor.formatBytes(dev.totalRamBytes))
                KeyValue(str(R.string.device_storage), str(R.string.device_storage_value, io.github.cyb3rl00m.prune.core.plan.Executor.formatBytes(dev.storageFreeBytes), io.github.cyb3rl00m.prune.core.plan.Executor.formatBytes(dev.storageTotalBytes)))
                KeyValue(str(R.string.device_android_user), dev.userId.toString())
            }
        }
        item {
            PruneCard {
                KeyValue(str(R.string.tab_packages), scan.apps.size.toString())
                KeyValue(str(R.string.compat_access), "${access.active.label} (${access.active.tier})")
                KeyValue("Shizuku", if (access.shizukuRunning) str(R.string.device_running) else if (access.shizukuInstalled) str(R.string.compat_shizuku_installed) else str(R.string.compat_shizuku_not_installed))
            }
        }
    }
}
