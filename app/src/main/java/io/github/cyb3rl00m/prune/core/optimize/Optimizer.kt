package io.github.cyb3rl00m.prune.core.optimize

import android.Manifest
import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.os.UserHandle
import android.os.storage.StorageManager
import android.provider.Settings
import androidx.annotation.StringRes
import io.github.cyb3rl00m.prune.R
import io.github.cyb3rl00m.prune.core.i18n.str
import io.github.cyb3rl00m.prune.core.model.AppPackage
import io.github.cyb3rl00m.prune.core.model.ChangeType
import io.github.cyb3rl00m.prune.core.model.Source
import io.github.cyb3rl00m.prune.core.plan.PlannedChange
import io.github.cyb3rl00m.prune.core.scan.AppUsage
import io.github.cyb3rl00m.prune.core.shell.Commands
import io.github.cyb3rl00m.prune.core.shell.OutputParsers
import io.github.cyb3rl00m.prune.core.shell.PrivilegedShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 最適化の各セクションと、それぞれが効く技術的な理由。 */
enum class OptimizationSection(
    @StringRes private val titleRes: Int,
    @StringRes private val whyRes: Int,
    @StringRes private val requirementRes: Int,
) {
    ANIMATIONS(R.string.opt_animations, R.string.opt_animations_why, R.string.opt_animations_req),
    BACKGROUND(R.string.opt_background, R.string.opt_background_why, R.string.opt_background_req),
    BATTERY_EXEMPTIONS(R.string.opt_exemptions, R.string.opt_exemptions_why, R.string.opt_settings_req),
    PERMISSIONS(R.string.opt_permissions, R.string.opt_permissions_why, R.string.opt_settings_req),
    NOTIFICATIONS(R.string.opt_notifications, R.string.opt_notifications_why, R.string.opt_notifications_req),
    STORAGE(R.string.opt_storage, R.string.opt_storage_why, R.string.opt_storage_req),
    RESOURCES(R.string.opt_resources, R.string.opt_resources_why, R.string.opt_resources_req),
    COMPILATION(R.string.opt_compilation, R.string.opt_compilation_why, R.string.req_shell);

    val title: String get() = str(titleRes)
    val why: String get() = str(whyRes)
    val requirement: String get() = str(requirementRes)
}

data class AnimationState(val window: Float, val transition: Float, val animator: Float) {
    val uniform: Float? get() = if (window == transition && transition == animator) window else null
}

data class PermissionInfoItem(val permission: String, val label: String, val group: String)

data class AppPermissions(val app: AppPackage, val granted: List<PermissionInfoItem>)

data class AppStorage(val app: AppPackage, val appBytes: Long, val dataBytes: Long, val cacheBytes: Long) {
    val total get() = appBytes + dataBytes + cacheBytes
}

data class StorageSummary(val totalBytes: Long, val freeBytes: Long, val apps: List<AppStorage>) {
    val cacheBytes get() = apps.sumOf { it.cacheBytes }
}

data class ResourceItem(val app: AppPackage, val foregroundMs7d: Long, val batteryMah: Double?)

data class Recommendation(val section: OptimizationSection, val title: String, val detail: String)

class Optimizer(private val context: Context) {

    private val pm = context.packageManager
    private val user = Process.myUid() / 100_000

    fun animationState(): AnimationState {
        val cr = context.contentResolver
        fun f(key: String) = runCatching { Settings.Global.getFloat(cr, key, 1f) }.getOrDefault(1f)
        return AnimationState(f(KEY_WINDOW), f(KEY_TRANSITION), f(KEY_ANIMATOR))
    }

    fun animationChanges(scale: Float): List<PlannedChange> {
        val value = if (scale == 0f) "0" else scale.toString()
        return listOf(KEY_WINDOW to str(R.string.opt_window_scale), KEY_TRANSITION to str(R.string.opt_transition_scale),
            KEY_ANIMATOR to str(R.string.opt_animator_scale)).map { (key, label) ->
            PlannedChange(
                type = ChangeType.SETTING_PUT, label = "$label → ${if (scale == 0f) str(R.string.opt_off) else "${value}×"}",
                params = mapOf("key" to key, "value" to value),
                explanation = OptimizationSection.ANIMATIONS.why + if (scale == 0f)
                    "\n\n" + str(R.string.opt_animations_off_note) else "",
            )
        }
    }

    /** [days] 日間開かれていないのに、バックグラウンドで動きうるアプリ。使用状況統計とシェルが必要。 */
    suspend fun backgroundCandidates(apps: List<AppPackage>, usage: Map<String, AppUsage>, shell: PrivilegedShell?,
                                     days: Int = 30): List<AppPackage> = withContext(Dispatchers.IO) {
        if (usage.isEmpty()) return@withContext emptyList()
        val restricted = shell?.let {
            runCatching { OutputParsers.appOpQueryPackages(it.run(Commands.appOpQuery("RUN_ANY_IN_BACKGROUND", "ignore", user)).stdout) }
                .getOrNull()
        } ?: emptySet()
        val cutoff = System.currentTimeMillis() - days * DAY
        apps.filter { a ->
            !a.facts.isSystem && !a.isLocked && a.facts.installedForUser && a.facts.enabled &&
                a.classification.warnings.none { it.groupKey == "IN_USE" } &&
                a.packageName !in restricted &&
                (usage[a.packageName]?.lastUsed ?: 0L) < cutoff &&
                installedBefore(a.packageName, cutoff)
        }.sortedBy { it.label.lowercase() }
    }

    fun backgroundChange(app: AppPackage, restrict: Boolean) = PlannedChange(
        type = ChangeType.APPOP_SET, packageName = app.packageName,
        label = str(if (restrict) R.string.opt_restrict_background else R.string.opt_allow_background, app.label),
        params = mapOf("op" to "RUN_ANY_IN_BACKGROUND", "mode" to if (restrict) "ignore" else "default"),
        explanation = OptimizationSection.BACKGROUND.why, isSystemPackage = app.facts.isSystem,
    )

    data class Exemptions(val removable: List<AppPackage>, val systemManaged: List<AppPackage>)

    suspend fun batteryExemptions(apps: List<AppPackage>, shell: PrivilegedShell?): Exemptions = withContext(Dispatchers.IO) {
        val power = context.getSystemService(PowerManager::class.java)
        val exempt = apps.filter { it.facts.installedForUser && runCatching { power.isIgnoringBatteryOptimizations(it.packageName) }.getOrDefault(false) }
        val list = shell?.let { runCatching { OutputParsers.deviceIdleWhitelist(it.run(Commands.deviceIdleList()).stdout) }.getOrNull() }
        if (list == null) {
            Exemptions(exempt.filter { !it.facts.isSystem }, exempt.filter { it.facts.isSystem })
        } else {
            Exemptions(exempt.filter { it.packageName in list.user && it.packageName != context.packageName },
                exempt.filter { it.packageName !in list.user })
        }
    }

    fun exemptionChange(app: AppPackage) = PlannedChange(
        type = ChangeType.DEVICEIDLE_REMOVE, packageName = app.packageName, label = str(R.string.opt_remove_exemption, app.label),
        explanation = OptimizationSection.BATTERY_EXEMPTIONS.why, isSystemPackage = app.facts.isSystem,
    )

    @Suppress("DEPRECATION")
    suspend fun permissions(apps: List<AppPackage>, includeSystem: Boolean): List<AppPermissions> = withContext(Dispatchers.IO) {
        val sensitive = sensitive()
        apps.filter { (includeSystem || !it.facts.isSystem) && it.facts.installedForUser && it.packageName != context.packageName }
            .mapNotNull { app ->
                val info: PackageInfo = runCatching {
                    if (Build.VERSION.SDK_INT >= 33) pm.getPackageInfo(app.packageName,
                        PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
                    else pm.getPackageInfo(app.packageName, PackageManager.GET_PERMISSIONS)
                }.getOrNull() ?: return@mapNotNull null
                val req = info.requestedPermissions ?: return@mapNotNull null
                val flags = info.requestedPermissionsFlags ?: return@mapNotNull null
                val granted = req.indices.filter { (flags[it] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0 }
                    .mapNotNull { sensitive[req[it]] }
                    .filter { it.permission != Manifest.permission.POST_NOTIFICATIONS }
                if (granted.isEmpty()) null else AppPermissions(app, granted)
            }.sortedByDescending { it.granted.size }
    }

    @Suppress("DEPRECATION")
    suspend fun notificationApps(apps: List<AppPackage>, includeSystem: Boolean): List<AppPackage> = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < 33) return@withContext emptyList()
        apps.filter { (includeSystem || !it.facts.isSystem) && it.facts.installedForUser && it.facts.enabled &&
            it.packageName != context.packageName &&
            pm.checkPermission(Manifest.permission.POST_NOTIFICATIONS, it.packageName) == PackageManager.PERMISSION_GRANTED
        }.sortedBy { it.label.lowercase() }
    }

    fun revokeChange(app: AppPackage, perm: PermissionInfoItem) = PlannedChange(
        type = ChangeType.PERMISSION_REVOKE, packageName = app.packageName,
        label = str(R.string.opt_revoke, perm.label, app.label), params = mapOf("permission" to perm.permission),
        explanation = OptimizationSection.PERMISSIONS.why, isSystemPackage = app.facts.isSystem,
    )

    fun notificationChange(app: AppPackage) = revokeChange(app, PermissionInfoItem(Manifest.permission.POST_NOTIFICATIONS, str(R.string.perm_notifications), str(R.string.permgroup_notifications)))
        .copy(explanation = OptimizationSection.NOTIFICATIONS.why)

    suspend fun storage(apps: List<AppPackage>): StorageSummary? = withContext(Dispatchers.IO) {
        val ssm = context.getSystemService(StorageStatsManager::class.java) ?: return@withContext null
        val uuid = StorageManager.UUID_DEFAULT
        val total = runCatching { ssm.getTotalBytes(uuid) }.getOrDefault(0L)
        val free = runCatching { ssm.getFreeBytes(uuid) }.getOrDefault(0L)
        val handle: UserHandle = Process.myUserHandle()
        val list = apps.filter { it.facts.installedForUser }.mapNotNull { app ->
            runCatching {
                val s = ssm.queryStatsForPackage(uuid, app.packageName, handle)
                AppStorage(app, s.appBytes, s.dataBytes - s.cacheBytes, s.cacheBytes)
            }.getOrNull()
        }
        StorageSummary(total, free, list.sortedByDescending { it.total })
    }

    fun trimCachesChange() = PlannedChange(type = ChangeType.TRIM_CACHES, label = str(R.string.opt_trim_caches_label),
        explanation = OptimizationSection.STORAGE.why)

    fun dexoptChange() = PlannedChange(type = ChangeType.DEXOPT, label = str(R.string.opt_dexopt_label),
        explanation = OptimizationSection.COMPILATION.why)

    suspend fun resources(apps: List<AppPackage>, usage: Map<String, AppUsage>, shell: PrivilegedShell?): List<ResourceItem> =
        withContext(Dispatchers.IO) {
            val battery = shell?.let {
                runCatching { OutputParsers.batteryUsePerUid(it.run(Commands.batteryStats(), 120_000).stdout) }.getOrNull()
            }.orEmpty()
            val byUid = apps.groupBy { it.facts.uid }
            val items = apps.filter { it.facts.installedForUser }.map { app ->
                val shared = byUid[app.facts.uid]?.size ?: 1
                // uid を共有するアプリは電池使用量も共有になるため、その uid のパッケージが 1 つだけの場合に限り表示する。
                ResourceItem(app, usage[app.packageName]?.foregroundMs7d ?: 0L, battery[app.facts.uid]?.takeIf { shared == 1 })
            }
            items.filter { it.foregroundMs7d > 0 || (it.batteryMah ?: 0.0) > 0.0 }
                .sortedWith(compareByDescending<ResourceItem> { it.batteryMah ?: 0.0 }.thenByDescending { it.foregroundMs7d })
        }

    fun recommendations(apps: List<AppPackage>, usage: Map<String, AppUsage>, privileged: Boolean): List<Recommendation> {
        val out = mutableListOf<Recommendation>()
        val anim = animationState()
        if (anim.window >= 1f && anim.transition >= 1f && anim.animator >= 1f) {
            out += Recommendation(OptimizationSection.ANIMATIONS, str(R.string.rec_animations),
                str(R.string.rec_animations_detail, str(if (privileged) R.string.rec_one_tap else R.string.rec_needs_shizuku_adb)))
        }
        if (usage.isEmpty()) {
            out += Recommendation(OptimizationSection.RESOURCES, str(R.string.rec_usage_access),
                str(R.string.rec_usage_access_detail))
        } else {
            val cutoff = System.currentTimeMillis() - 30 * DAY
            val unused = apps.count { !it.facts.isSystem && it.facts.enabled && (usage[it.packageName]?.lastUsed ?: 0L) < cutoff &&
                installedBefore(it.packageName, cutoff) }
            if (unused > 0) out += Recommendation(OptimizationSection.BACKGROUND, str(R.string.rec_unused, unused),
                str(R.string.rec_unused_detail))
        }
        val power = context.getSystemService(PowerManager::class.java)
        val exempt = apps.count { !it.facts.isSystem && it.packageName != context.packageName &&
            runCatching { power.isIgnoringBatteryOptimizations(it.packageName) }.getOrDefault(false) }
        if (exempt > 0) out += Recommendation(OptimizationSection.BATTERY_EXEMPTIONS, str(R.string.rec_exempt, exempt),
            str(R.string.rec_exempt_detail))
        return out
    }

    @Suppress("DEPRECATION")
    private fun installedBefore(pkg: String, time: Long): Boolean = runCatching {
        pm.getPackageInfo(pkg, 0).firstInstallTime < time
    }.getOrDefault(true)

    fun sourceIsUser(app: AppPackage) = app.classification.source == Source.USER

    companion object {
        const val KEY_WINDOW = "window_animation_scale"
        const val KEY_TRANSITION = "transition_animation_scale"
        const val KEY_ANIMATOR = "animator_duration_scale"
        private const val DAY = 24L * 60 * 60 * 1000

        /** 確認対象の機密性の高い権限: (権限名, 表示名, グループ名)。 */
        private val SENSITIVE_DEFS: List<Triple<String, Int, Int>> = listOf(
            Triple(Manifest.permission.ACCESS_FINE_LOCATION, R.string.perm_fine_location, R.string.permgroup_location),
            Triple(Manifest.permission.ACCESS_COARSE_LOCATION, R.string.perm_coarse_location, R.string.permgroup_location),
            Triple(Manifest.permission.ACCESS_BACKGROUND_LOCATION, R.string.perm_background_location, R.string.permgroup_location),
            Triple(Manifest.permission.CAMERA, R.string.perm_camera, R.string.permgroup_camera),
            Triple(Manifest.permission.RECORD_AUDIO, R.string.perm_microphone, R.string.permgroup_microphone),
            Triple(Manifest.permission.READ_CONTACTS, R.string.perm_contacts, R.string.permgroup_contacts),
            Triple(Manifest.permission.WRITE_CONTACTS, R.string.perm_edit_contacts, R.string.permgroup_contacts),
            Triple(Manifest.permission.READ_CALENDAR, R.string.perm_calendar, R.string.permgroup_calendar),
            Triple(Manifest.permission.READ_SMS, R.string.perm_read_sms, R.string.permgroup_sms),
            Triple(Manifest.permission.SEND_SMS, R.string.perm_send_sms, R.string.permgroup_sms),
            Triple(Manifest.permission.RECEIVE_SMS, R.string.perm_receive_sms, R.string.permgroup_sms),
            Triple(Manifest.permission.READ_CALL_LOG, R.string.perm_call_log, R.string.permgroup_call_log),
            Triple(Manifest.permission.CALL_PHONE, R.string.perm_make_calls, R.string.permgroup_phone),
            Triple(Manifest.permission.READ_PHONE_STATE, R.string.perm_phone_state, R.string.permgroup_phone),
            Triple(Manifest.permission.BODY_SENSORS, R.string.perm_body_sensors, R.string.permgroup_body_sensors),
            Triple(Manifest.permission.ACTIVITY_RECOGNITION, R.string.perm_physical_activity, R.string.permgroup_physical_activity),
            Triple("android.permission.READ_MEDIA_IMAGES", R.string.perm_photos, R.string.permgroup_photos_videos),
            Triple("android.permission.READ_MEDIA_VIDEO", R.string.perm_videos, R.string.permgroup_photos_videos),
            Triple("android.permission.READ_MEDIA_AUDIO", R.string.perm_music_audio, R.string.permgroup_music_audio),
            Triple(Manifest.permission.READ_EXTERNAL_STORAGE, R.string.perm_files_media, R.string.permgroup_files),
            Triple("android.permission.BLUETOOTH_SCAN", R.string.perm_nearby_devices, R.string.permgroup_nearby_devices),
            Triple("android.permission.NEARBY_WIFI_DEVICES", R.string.perm_nearby_wifi, R.string.permgroup_nearby_devices),
            Triple(Manifest.permission.POST_NOTIFICATIONS, R.string.perm_notifications, R.string.permgroup_notifications),
        )

        /** 権限名 -> 表示用の情報。表示名は現在の表示言語で解決する。 */
        fun sensitive(): Map<String, PermissionInfoItem> =
            SENSITIVE_DEFS.associate { (p, label, group) -> p to PermissionInfoItem(p, str(label), str(group)) }
    }
}
