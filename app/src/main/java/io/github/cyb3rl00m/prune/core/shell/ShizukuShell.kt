package io.github.cyb3rl00m.prune.core.shell

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import io.github.cyb3rl00m.prune.BuildConfig
import io.github.cyb3rl00m.prune.IShellService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import rikka.shizuku.Shizuku
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Shizuku のユーザーサービスを通してコマンドを実行する (shell として。Shizuku が root で起動されていれば root として)。 */
class ShizukuShell(private val packageName: String) : PrivilegedShell {

    override val kind = AccessKind.SHIZUKU
    override var uid: Int = 2000
        private set

    @Volatile private var service: IShellService? = null
    private val bindLock = Mutex()

    private val args by lazy {
        Shizuku.UserServiceArgs(ComponentName(packageName, ShellService::class.java.name))
            .daemon(false)
            .processNameSuffix("shell")
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE)
    }

    private var connection: ServiceConnection? = null

    private suspend fun ensureService(): IShellService {
        service?.takeIf { it.asBinder().isBinderAlive }?.let { return it }
        return bindLock.withLock {
            service?.takeIf { it.asBinder().isBinderAlive }?.let { return@withLock it }
            withTimeout(15_000) {
                suspendCancellableCoroutine { cont ->
                    val conn = object : ServiceConnection {
                        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                            val s = binder?.takeIf { it.pingBinder() }?.let { IShellService.Stub.asInterface(it) }
                            service = s
                            if (s != null) {
                                uid = runCatching { s.uid() }.getOrDefault(2000)
                                if (cont.isActive) cont.resume(s)
                            }
                        }

                        override fun onServiceDisconnected(name: ComponentName?) {
                            service = null
                        }
                    }
                    connection = conn
                    // Shizuku は接続コールバックをメインスレッドで呼ぶので、バインドもメインスレッドから行う。
                    Handler(Looper.getMainLooper()).post {
                        runCatching { Shizuku.bindUserService(args, conn) }.onFailure { e ->
                            if (cont.isActive) cont.resumeWithException(e)
                        }
                    }
                }
            }
        }
    }

    override suspend fun run(argv: List<String>, timeoutMs: Long): ShellResult = withContext(Dispatchers.IO) {
        val s = ensureService()
        val bundle = s.exec(argv.toTypedArray(), timeoutMs)
        ShellResult(
            exitCode = bundle.getInt(ShellService.KEY_EXIT, -1),
            stdout = bundle.getString(ShellService.KEY_OUT).orEmpty(),
            stderr = bundle.getString(ShellService.KEY_ERR).orEmpty(),
        )
    }

    fun release() {
        connection?.let { runCatching { Shizuku.unbindUserService(args, it, true) } }
        connection = null
        service = null
    }
}
