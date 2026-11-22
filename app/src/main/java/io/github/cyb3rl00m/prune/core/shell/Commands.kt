package io.github.cyb3rl00m.prune.core.shell

import androidx.annotation.StringRes
import io.github.cyb3rl00m.prune.R
import io.github.cyb3rl00m.prune.core.i18n.str

/** コマンド 1 回分の実行結果。 */
data class ShellResult(val exitCode: Int, val stdout: String, val stderr: String) {
    val output: String get() = listOf(stdout.trim(), stderr.trim()).filter { it.isNotEmpty() }.joinToString("\n")
    val ok: Boolean get() = exitCode == 0 && !OutputParsers.looksLikeFailure(output)
}

/** Prune が特権コマンドを実行する手段。 */
enum class AccessKind(@StringRes private val labelRes: Int, @StringRes private val tierRes: Int) {
    SHIZUKU(R.string.access_shizuku, R.string.tier_adb),
    ROOT(R.string.access_root, R.string.tier_root),
    ADB_SCRIPT(R.string.access_adb_script, R.string.tier_adb_manual),
    NONE(R.string.access_none, R.string.tier_standard);

    val label: String get() = str(labelRes)
    val tier: String get() = str(tierRes)
}

/** shell ユーザーまたは root ユーザーとしてコマンドを実行できるもの。 */
interface PrivilegedShell {
    val kind: AccessKind
    /** コマンドを実行する uid: 2000 = shell、0 = root。 */
    val uid: Int
    suspend fun run(argv: List<String>, timeoutMs: Long = 60_000): ShellResult
}

/**
 * Prune が実行するすべてのコマンドを組み立てる。入力はすべてここで検証し、コマンドは argv リストとして渡す
 * (シェル文字列は決して使わない)。そのため、パッケージ名から余分なコマンドを注入されることはない。
 */
object Commands {
    private val PKG = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)*$")
    private val PERMISSION = Regex("^[A-Za-z][A-Za-z0-9_.]*$")
    val APP_OPS = setOf("RUN_ANY_IN_BACKGROUND", "RUN_IN_BACKGROUND", "GET_USAGE_STATS")
    val APP_OP_MODES = setOf("allow", "ignore", "deny", "default", "foreground")
    val GLOBAL_SETTINGS = setOf("window_animation_scale", "transition_animation_scale", "animator_duration_scale")

    fun isValidPackage(pkg: String) = pkg.length in 1..255 && PKG.matches(pkg)

    private fun pkg(p: String): String = p.also { require(isValidPackage(it)) { "Invalid package name: $it" } }
    private fun user(u: Int): String = u.also { require(it in 0..9999) { "Invalid user: $it" } }.toString()

    fun disable(p: String, u: Int) = listOf("pm", "disable-user", "--user", user(u), pkg(p))
    fun enable(p: String, u: Int) = listOf("pm", "enable", "--user", user(u), pkg(p))
    fun uninstallForUser(p: String, u: Int) = listOf("pm", "uninstall", "-k", "--user", user(u), pkg(p))
    fun installExisting(p: String, u: Int) = listOf("pm", "install-existing", "--user", user(u), pkg(p))

    fun appOpSet(p: String, op: String, mode: String, u: Int): List<String> {
        require(op in APP_OPS && mode in APP_OP_MODES)
        return listOf("cmd", "appops", "set", "--user", user(u), pkg(p), op, mode)
    }

    fun appOpGet(p: String, op: String, u: Int): List<String> {
        require(op in APP_OPS)
        return listOf("cmd", "appops", "get", "--user", user(u), pkg(p), op)
    }

    fun appOpQuery(op: String, mode: String, u: Int): List<String> {
        require(op in APP_OPS && mode in APP_OP_MODES)
        return listOf("cmd", "appops", "query-op", "--user", user(u), op, mode)
    }

    fun revoke(p: String, perm: String, u: Int): List<String> {
        require(PERMISSION.matches(perm))
        return listOf("pm", "revoke", "--user", user(u), pkg(p), perm)
    }

    fun grant(p: String, perm: String, u: Int): List<String> {
        require(PERMISSION.matches(perm))
        return listOf("pm", "grant", "--user", user(u), pkg(p), perm)
    }

    fun deviceIdleRemove(p: String) = listOf("cmd", "deviceidle", "whitelist", "-" + pkg(p))
    fun deviceIdleAdd(p: String) = listOf("cmd", "deviceidle", "whitelist", "+" + pkg(p))
    fun deviceIdleList() = listOf("cmd", "deviceidle", "whitelist")

    fun settingsPut(key: String, value: String): List<String> {
        require(key in GLOBAL_SETTINGS && value.matches(Regex("^[0-9.]{1,6}$")))
        return listOf("settings", "put", "global", key, value)
    }

    fun settingsDelete(key: String): List<String> {
        require(key in GLOBAL_SETTINGS)
        return listOf("settings", "delete", "global", key)
    }

    /** 全アプリのキャッシュ領域の解放をシステムに要求する (ストレージ不足時に Android が使うのと同じ仕組み)。 */
    fun trimCaches() = listOf("pm", "trim-caches", "9999G")

    /** アイドル時メンテナンスのアプリコンパイルジョブを今すぐ実行する (アプリプロファイルを使った ART dexopt)。 */
    fun backgroundDexopt() = listOf("cmd", "package", "bg-dexopt-job")

    fun batteryStats() = listOf("dumpsys", "batterystats")

    fun id() = listOf("id")

    /** 表示・ADB スクリプト・`su -c` 用に argv をクォートする。 */
    fun toShell(argv: List<String>): String = argv.joinToString(" ") { quote(it) }

    fun quote(arg: String): String =
        if (arg.isNotEmpty() && arg.all { it.isLetterOrDigit() || it in "._-+/=:@%," }) arg
        else "'" + arg.replace("'", "'\\''") + "'"
}

/** コマンド出力を解析する。実際の出力でユニットテストできるよう純粋関数にしている。
 * 照合する文字列は Android が出力する英語なので、翻訳してはならない。 */
object OutputParsers {

    private val failureMarkers = listOf("Exception", "Failure", "Error:", "Unknown package", "not found",
        "Shell cannot", "Permission Denial", "Bad component", "IllegalArgument", "SecurityException")

    fun looksLikeFailure(output: String): Boolean = failureMarkers.any { output.contains(it, ignoreCase = false) }

    /** `pm disable-user` は "Package X new state: disabled-user" と出力する。 */
    fun disableSucceeded(r: ShellResult) = r.exitCode == 0 && r.stdout.contains("new state: disabled")

    /** `pm enable` は "Package X new state: enabled" と出力する。 */
    fun enableSucceeded(r: ShellResult) = r.exitCode == 0 &&
        (r.stdout.contains("new state: enabled") || r.stdout.contains("new state: default"))

    fun uninstallSucceeded(r: ShellResult) = r.exitCode == 0 && r.stdout.trim().startsWith("Success")

    /** `pm install-existing` は "Package X installed for user: 0" と出力する。 */
    fun installExistingSucceeded(r: ShellResult) = r.exitCode == 0 && r.stdout.contains("installed for user")

    /** `cmd appops get <pkg> <op>` の出力からモードを読む: "RUN_ANY_IN_BACKGROUND: ignore; time=..." */
    fun appOpMode(output: String, op: String): String {
        val m = Regex("""\b$op: ([a-z]+)""").find(output) ?: return "default"
        return m.groupValues[1]
    }

    /** `cmd appops query-op` は 1 行に 1 パッケージを出力する (または "No operations.")。 */
    fun appOpQueryPackages(output: String): Set<String> =
        output.lines().map { it.trim() }.filter { Commands.isValidPackage(it) }.toSet()

    data class IdleWhitelist(val user: Set<String>, val system: Set<String>)

    /** `cmd deviceidle whitelist` は "system-excidle,pkg,uid"、"system,pkg,uid"、"user,pkg,uid" の行を出力する。 */
    fun deviceIdleWhitelist(output: String): IdleWhitelist {
        val user = mutableSetOf<String>()
        val system = mutableSetOf<String>()
        for (line in output.lines()) {
            val parts = line.trim().split(',')
            if (parts.size < 2 || !Commands.isValidPackage(parts[1])) continue
            when (parts[0]) {
                "user" -> user += parts[1]
                "system", "system-excidle" -> system += parts[1]
            }
        }
        return IdleWhitelist(user, system - user)
    }

    /**
     * `dumpsys batterystats` の "Estimated power use (mAh)" セクションから、uid ごとの推定電池使用量を得る。
     * 行の形式は "UID u0a234: 12.3 ( cpu=... )" や "UID 1000: 45.6 fg: ..."。
     */
    fun batteryUsePerUid(output: String): Map<Int, Double> {
        val lines = output.lines()
        val headerIndex = lines.indexOfFirst { it.contains("Estimated power use") }
        if (headerIndex < 0) return emptyMap()
        fun indent(s: String) = s.length - s.trimStart().length
        val headerIndent = indent(lines[headerIndex])
        // セクションは、見出しより深くインデントされていない最初の行で終わる。
        val section = lines.drop(headerIndex + 1).takeWhile { it.isBlank() || indent(it) > headerIndent }
        val re = Regex("""^\s*UID (u(\d+)a(\d+)|u(\d+)s(\d+)|(\d+)): ([0-9]+(?:\.[0-9]+)?)""")
        val out = HashMap<Int, Double>()
        for (line in section) {
            val m = re.find(line) ?: continue
            val g = m.groupValues
            val uid = when {
                g[2].isNotEmpty() -> g[2].toInt() * 100_000 + 10_000 + g[3].toInt()
                g[4].isNotEmpty() -> g[4].toInt() * 100_000 + 99_000 + g[5].toInt()
                else -> g[6].toInt()
            }
            out[uid] = (out[uid] ?: 0.0) + g[7].toDouble()
        }
        return out
    }

    /** `id` の uid 部分: "uid=2000(shell) gid=..." */
    fun uidOf(idOutput: String): Int? = Regex("""uid=(\d+)""").find(idOutput)?.groupValues?.get(1)?.toIntOrNull()
}
