package io.github.cyb3rl00m.prune.core.scan

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.view.inputmethod.InputMethodManager
import io.github.cyb3rl00m.prune.R
import io.github.cyb3rl00m.prune.core.i18n.str

/**
 * 同じ種類のアプリが複数インストールされているものを見つける (ブラウザ 2 つ、ギャラリー 2 つ、キーボード 3 つなど)。
 * メーカー製の不要アプリのよくある原因。Android 標準のアプリカテゴリを使うので、どのブランドでも動く。
 * 報告するだけで、どれを残すかはユーザーが選ぶ。
 */
class DuplicateFinder(private val context: Context) {

    data class DuplicateGroup(val name: String, val packages: List<String>, val defaultPackage: String?)

    private val pm = context.packageManager

    fun find(): List<DuplicateGroup> {
        val groups = mutableListOf<DuplicateGroup>()
        fun add(name: String, pkgs: Collection<String>, default: String? = null) {
            val list = pkgs.filter { it != context.packageName }.distinct()
            if (list.size >= 2) groups += DuplicateGroup(name, list.sorted(), default?.takeIf { it in list })
        }
        val categories = listOf(
            Intent.CATEGORY_APP_BROWSER to str(R.string.dup_browsers),
            Intent.CATEGORY_APP_GALLERY to str(R.string.dup_galleries),
            Intent.CATEGORY_APP_MUSIC to str(R.string.dup_music),
            Intent.CATEGORY_APP_CALCULATOR to str(R.string.dup_calculators),
            Intent.CATEGORY_APP_CALENDAR to str(R.string.dup_calendars),
            Intent.CATEGORY_APP_EMAIL to str(R.string.dup_email),
            Intent.CATEGORY_APP_MESSAGING to str(R.string.dup_messaging),
            Intent.CATEGORY_APP_CONTACTS to str(R.string.dup_contacts),
            Intent.CATEGORY_APP_MAPS to str(R.string.dup_maps),
        ) + (if (Build.VERSION.SDK_INT >= 29) listOf(Intent.CATEGORY_APP_FILES to str(R.string.dup_files)) else emptyList())
        for ((category, name) in categories) {
            val intent = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, category)
            add(name, query(intent), defaultFor(intent))
        }
        add(str(R.string.dup_stores), query(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.example"))))
        add(str(R.string.dup_video), query(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("content://media/video"), "video/*")))
        add(str(R.string.dup_cameras), query(Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)))
        val imm = context.getSystemService(InputMethodManager::class.java)
        add(str(R.string.dup_keyboards), runCatching { imm?.inputMethodList?.map { it.packageName } }.getOrNull().orEmpty())
        return groups
    }

    @Suppress("DEPRECATION")
    private fun query(intent: Intent): List<String> = runCatching {
        val list = if (Build.VERSION.SDK_INT >= 33) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong()))
        } else pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        list.mapNotNull { it.activityInfo?.packageName }.filter { it != "android" }
    }.getOrDefault(emptyList())

    @Suppress("DEPRECATION")
    private fun defaultFor(intent: Intent): String? = runCatching {
        val ri = if (Build.VERSION.SDK_INT >= 33) {
            pm.resolveActivity(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
        } else pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        ri?.activityInfo?.packageName?.takeIf { it != "android" }
    }.getOrNull()
}
