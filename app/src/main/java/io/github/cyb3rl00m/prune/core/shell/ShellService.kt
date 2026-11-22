package io.github.cyb3rl00m.prune.core.shell

import android.os.Bundle
import android.os.Process
import io.github.cyb3rl00m.prune.IShellService
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.system.exitProcess

/**
 * Shizuku が起動する別プロセスで、shell ユーザー (uid 2000) または root として動く。
 * 受け取った argv をそのまま ProcessBuilder で実行する。シェルは一切介在しない。
 * このプロセスでは I18n が初期化されないため、メッセージは英語のまま返す。
 */
class ShellService : IShellService.Stub() {

    override fun destroy() {
        exitProcess(0)
    }

    override fun uid(): Int = Process.myUid()

    override fun exec(argv: Array<out String>, timeoutMillis: Long): Bundle {
        val result = Bundle()
        if (argv.isEmpty()) {
            result.putInt(KEY_EXIT, -1)
            result.putString(KEY_ERR, "empty command")
            return result
        }
        val cmd = argv.toMutableList()
        if (!cmd[0].startsWith("/")) {
            listOf("/system/bin/", "/system/xbin/", "/vendor/bin/").map { File(it + cmd[0]) }
                .firstOrNull { it.exists() }?.let { cmd[0] = it.path }
        }
        try {
            val process = ProcessBuilder(cmd).start()
            process.outputStream.close()
            val out = StringBuilder()
            val err = StringBuilder()
            val tOut = thread { out.append(process.inputStream.bufferedReader().readText()) }
            val tErr = thread { err.append(process.errorStream.bufferedReader().readText()) }
            val finished = process.waitFor(timeoutMillis.coerceIn(1_000, 30 * 60_000), TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroyForcibly()
                result.putInt(KEY_EXIT, -2)
                result.putString(KEY_ERR, "Timed out after ${timeoutMillis / 1000} s")
            } else {
                result.putInt(KEY_EXIT, process.exitValue())
            }
            tOut.join(2_000)
            tErr.join(2_000)
            result.putString(KEY_OUT, out.toString().take(MAX_OUTPUT))
            if (!result.containsKey(KEY_ERR)) result.putString(KEY_ERR, err.toString().take(MAX_OUTPUT))
        } catch (t: Throwable) {
            result.putInt(KEY_EXIT, -3)
            result.putString(KEY_ERR, t.toString())
        }
        return result
    }

    companion object {
        const val KEY_EXIT = "exit"
        const val KEY_OUT = "out"
        const val KEY_ERR = "err"
        /** Binder のトランザクションは約 1 MB に制限されるため、dumpsys の出力はそれより十分小さく切り詰める。 */
        const val MAX_OUTPUT = 400_000
    }
}
