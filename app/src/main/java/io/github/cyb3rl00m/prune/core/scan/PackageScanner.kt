package io.github.cyb3rl00m.prune.core.scan

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.provider.Telephony
import android.telecom.TelecomManager
import io.github.cyb3rl00m.prune.R
import io.github.cyb3rl00m.prune.core.i18n.str
import io.github.cyb3rl00m.prune.core.db.DynamicProtections
import io.github.cyb3rl00m.prune.core.model.PackageFacts

/** 公開された PackageManager API を通して、現在のユーザーのインストール済みパッケージとその状態を読む。 */
class PackageScanner(private val context: Context) {

    private val pm: PackageManager = context.packageManager

    /** ラベルの取得はスキャンで最も遅い部分なので、パッケージのバージョンごとにキャッシュする。 */
    private val labelCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, String>>()

    @Suppress("DEPRECATION")
    fun scan(): List<PackageFacts> {
        val flags = PackageManager.MATCH_UNINSTALLED_PACKAGES or PackageManager.MATCH_DISABLED_COMPONENTS
        val infos: List<PackageInfo> = if (Build.VERSION.SDK_INT >= 33) {
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            pm.getInstalledPackages(flags)
        }
        val launchable = launchablePackages()
        return infos.mapNotNull { info -> toFacts(info, launchable) }.sortedBy { it.label.lowercase() }
    }

    @Suppress("DEPRECATION")
    fun read(packageName: String): PackageFacts? = runCatching {
        val flags = PackageManager.MATCH_UNINSTALLED_PACKAGES or PackageManager.MATCH_DISABLED_COMPONENTS
        val info = if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else pm.getPackageInfo(packageName, flags)
        toFacts(info, null)
    }.getOrNull()

    @Suppress("DEPRECATION")
    private fun toFacts(info: PackageInfo, launchable: Set<String>?): PackageFacts? {
        val ai: ApplicationInfo = info.applicationInfo ?: return null
        val installed = (ai.flags and ApplicationInfo.FLAG_INSTALLED) != 0
        val enabledSetting = runCatching { pm.getApplicationEnabledSetting(info.packageName) }
            .getOrDefault(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)
        val enabled = when (enabledSetting) {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED -> false
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            else -> ai.enabled
        }
        val label = labelCache[info.packageName]?.takeIf { it.first == info.lastUpdateTime }?.second
            ?: (runCatching { ai.loadLabel(pm).toString() }.getOrNull()?.takeIf { it.isNotBlank() } ?: info.packageName)
                .also { labelCache[info.packageName] = info.lastUpdateTime to it }
        return PackageFacts(
            packageName = info.packageName,
            label = label,
            isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
            isUpdatedSystem = (ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0,
            isPersistent = (ai.flags and ApplicationInfo.FLAG_PERSISTENT) != 0,
            sharedUserId = info.sharedUserId,
            installedForUser = installed,
            enabled = enabled,
            versionName = info.versionName,
            uid = ai.uid,
            hasLauncherActivity = launchable?.contains(info.packageName) ?: false,
            installer = installerOf(info.packageName),
        )
    }

    @Suppress("DEPRECATION")
    private fun installerOf(pkg: String): String? = runCatching {
        if (Build.VERSION.SDK_INT >= 30) pm.getInstallSourceInfo(pkg).installingPackageName
        else pm.getInstallerPackageName(pkg)
    }.getOrNull()

    @Suppress("DEPRECATION")
    private fun launchablePackages(): Set<String> = runCatching {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val list = if (Build.VERSION.SDK_INT >= 33) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong()))
        } else pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        list.map { it.activityInfo.packageName }.toSet()
    }.getOrDefault(emptySet())

    /** この端末が現在依存しているパッケージ: 既定のアプリ、キーボード、デバイス管理アプリなど。 */
    @Suppress("DEPRECATION")
    fun dynamicProtections(extraLocked: Map<String, String> = emptyMap()): DynamicProtections {
        val locked = linkedMapOf<String, String>()
        val inUse = linkedMapOf<String, String>()
        fun resolve(intent: Intent): String? = runCatching {
            val ri = if (Build.VERSION.SDK_INT >= 33) {
                pm.resolveActivity(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
            } else pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            ri?.activityInfo?.packageName?.takeIf { it != "android" }
        }.getOrNull()

        resolve(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
            ?.let { locked[it] = str(R.string.dyn_launcher) }
        runCatching { context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage }.getOrNull()
            ?.let { locked[it] = str(R.string.dyn_dialer) }
        runCatching { Telephony.Sms.getDefaultSmsPackage(context) }.getOrNull()
            ?.let { locked[it] = str(R.string.dyn_sms) }
        val cr = context.contentResolver
        runCatching { Settings.Secure.getString(cr, Settings.Secure.DEFAULT_INPUT_METHOD) }.getOrNull()
            ?.substringBefore('/')?.takeIf { it.isNotBlank() }
            ?.let { locked[it] = str(R.string.dyn_keyboard) }
        runCatching { Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) }.getOrNull()
            ?.split(':')?.map { it.substringBefore('/') }?.filter { it.isNotBlank() }
            ?.forEach { locked[it] = str(R.string.dyn_accessibility) }
        runCatching { context.getSystemService(DevicePolicyManager::class.java)?.activeAdmins }.getOrNull()
            ?.forEach { locked[it.packageName] = str(R.string.dyn_device_admin) }

        resolve(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")).addCategory(Intent.CATEGORY_BROWSABLE))
            ?.let { inUse[it] = str(R.string.dyn_browser) }
        runCatching { Settings.Secure.getString(cr, Settings.Secure.ENABLED_INPUT_METHODS) }.getOrNull()
            ?.split(':')?.map { it.substringBefore('/') }?.filter { it.isNotBlank() && it !in locked }
            ?.forEach { inUse[it] = str(R.string.dyn_enabled_keyboard) }
        runCatching { Settings.Secure.getString(cr, "enabled_notification_listeners") }.getOrNull()
            ?.split(':')?.map { it.substringBefore('/') }?.filter { it.isNotBlank() }
            ?.forEach { inUse.putIfAbsent(it, str(R.string.dyn_notification_access)) }
        runCatching { android.app.WallpaperManager.getInstance(context).wallpaperInfo?.packageName }.getOrNull()
            ?.let { inUse.putIfAbsent(it, str(R.string.dyn_live_wallpaper)) }

        extraLocked.forEach { (pkg, reason) -> locked.putIfAbsent(pkg, reason) }
        locked[context.packageName] = str(R.string.dyn_prune_itself)
        return DynamicProtections(locked = locked, inUse = inUse)
    }
}
