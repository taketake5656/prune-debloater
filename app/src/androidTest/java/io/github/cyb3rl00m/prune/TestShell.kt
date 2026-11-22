package io.github.cyb3rl00m.prune

import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import io.github.cyb3rl00m.prune.core.shell.AccessKind
import io.github.cyb3rl00m.prune.core.shell.PrivilegedShell
import io.github.cyb3rl00m.prune.core.shell.ShellResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * UiAutomation を通して shell ユーザー (uid 2000) としてコマンドを実行する。ワイヤレスデバッグで起動した
 * Shizuku が Prune に与えるのと同じ権限。実際の Android 上で Prune の本物のコマンドを検証するために使う。
 */
class UiAutomationShell : PrivilegedShell {
    override val kind = AccessKind.SHIZUKU
    override val uid = 2000

    override suspend fun run(argv: List<String>, timeoutMs: Long): ShellResult = withContext(Dispatchers.IO) {
        // UiAutomation はコマンドを空白で分割する (シェルを通さない) ため、argv に空白を含めてはならない。Prune のコマンドは決して含まない。
        require(argv.none { it.contains(' ') })
        val fds = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommandRwe(argv.joinToString(" "))
        fds[1].close()
        val err = StringBuilder()
        val t = Thread { err.append(ParcelFileDescriptor.AutoCloseInputStream(fds[2]).bufferedReader().readText()) }.apply { start() }
        val out = ParcelFileDescriptor.AutoCloseInputStream(fds[0]).bufferedReader().readText()
        t.join(timeoutMs)
        ShellResult(0, out, err.toString())
    }
}

object Shots {
    private const val DIR = "/data/local/tmp/prune-shots"

    fun shell(cmd: String): String {
        val pfd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(cmd)
        return ParcelFileDescriptor.AutoCloseInputStream(pfd).bufferedReader().readText()
    }

    fun take(name: String) {
        Thread.sleep(600)
        shell("screencap -p $DIR/$name.png")
    }

    fun exists(name: String) = File("$DIR/$name.png").exists()
}
