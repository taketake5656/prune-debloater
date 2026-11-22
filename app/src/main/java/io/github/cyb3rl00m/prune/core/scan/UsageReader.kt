package io.github.cyb3rl00m.prune.core.scan

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process

data class AppUsage(val packageName: String, val lastUsed: Long, val foregroundMs7d: Long)

/** UsageStatsManager から得るアプリの使用状況。ユーザーが許可する「使用状況へのアクセス」(PACKAGE_USAGE_STATS) が必要。 */
class UsageReader(private val context: Context) {

    @Suppress("DEPRECATION")
    fun hasAccess(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return if (mode == AppOpsManager.MODE_DEFAULT) {
            context.checkSelfPermission(android.Manifest.permission.PACKAGE_USAGE_STATS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        } else mode == AppOpsManager.MODE_ALLOWED
    }

    /** 過去 [days] 日間の使用状況 (パッケージごと)。使用状況へのアクセスがなければ空。 */
    fun read(days: Int = 90): Map<String, AppUsage> {
        if (!hasAccess()) return emptyMap()
        val usm = context.getSystemService(UsageStatsManager::class.java) ?: return emptyMap()
        val now = System.currentTimeMillis()
        val long = runCatching { usm.queryAndAggregateUsageStats(now - days * DAY, now) }.getOrNull().orEmpty()
        val week = runCatching { usm.queryAndAggregateUsageStats(now - 7 * DAY, now) }.getOrNull().orEmpty()
        return long.mapValues { (pkg, s) ->
            AppUsage(pkg, s.lastTimeUsed, week[pkg]?.totalTimeInForeground ?: 0L)
        }.filterValues { it.lastUsed > 0 }
    }

    companion object {
        const val DAY = 24L * 60 * 60 * 1000
    }
}
