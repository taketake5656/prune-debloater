package io.github.cyb3rl00m.prune.core.shell

import io.github.cyb3rl00m.prune.R
import io.github.cyb3rl00m.prune.core.i18n.str
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** `su` (Magisk、KernelSU、APatch) を通してコマンドを実行する。各引数はクォートする ([Commands.quote] 参照)。 */
class RootShell : PrivilegedShell {
    override val kind = AccessKind.ROOT
    override val uid = 0

    override suspend fun run(argv: List<String>, timeoutMs: Long): ShellResult = withContext(Dispatchers.IO) {
        exec(listOf("su", "-c", Commands.toShell(argv)), timeoutMs)
    }

    companion object {
        /** root マネージャーにアクセスを要求する。コマンドが本当に uid 0 で動く場合だけ true を返す。 */
        suspend fun request(): Boolean = withContext(Dispatchers.IO) {
            val r = exec(listOf("su", "-c", "id"), 30_000)
            r.exitCode == 0 && OutputParsers.uidOf(r.stdout) == 0
        }

        internal fun exec(cmd: List<String>, timeoutMs: Long): ShellResult = try {
            val p = ProcessBuilder(cmd).start()
            p.outputStream.close()
            val out = StringBuilder()
            val err = StringBuilder()
            val t1 = thread { out.append(p.inputStream.bufferedReader().readText()) }
            val t2 = thread { err.append(p.errorStream.bufferedReader().readText()) }
            val done = p.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            if (!done) p.destroyForcibly()
            t1.join(2_000)
            t2.join(2_000)
            ShellResult(if (done) p.exitValue() else -2, out.toString(), if (done) err.toString() else str(R.string.shell_timed_out))
        } catch (e: Exception) {
            ShellResult(-3, "", e.message ?: e.toString())
        }
    }
}
