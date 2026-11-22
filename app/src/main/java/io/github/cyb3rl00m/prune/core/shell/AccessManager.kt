package io.github.cyb3rl00m.prune.core.shell

import android.Manifest
import android.content.Context
import io.github.cyb3rl00m.prune.R
import io.github.cyb3rl00m.prune.core.i18n.str
import android.content.pm.PackageManager
import io.github.cyb3rl00m.prune.core.scan.UsageReader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import rikka.shizuku.Shizuku

data class AccessState(
    val shizukuInstalled: Boolean = false,
    val shizukuRunning: Boolean = false,
    val shizukuPermission: Boolean = false,
    val shizukuTooOld: Boolean = false,
    /** Shizuku が root で起動されていれば 0、ADB / ワイヤレスデバッグで起動されていれば 2000。 */
    val shizukuUid: Int? = null,
    val rootGranted: Boolean = false,
    val usageAccess: Boolean = false,
    val writeSecureSettings: Boolean = false,
    /** ユーザー設定: Shizuku が使える場合でも root を使う。 */
    val preferRoot: Boolean = false,
) {
    val active: AccessKind
        get() = when {
            preferRoot && rootGranted -> AccessKind.ROOT
            shizukuRunning && shizukuPermission -> AccessKind.SHIZUKU
            rootGranted -> AccessKind.ROOT
            else -> AccessKind.NONE
        }
    val privileged: Boolean get() = active == AccessKind.SHIZUKU || active == AccessKind.ROOT
}

/** 現在使える権限の段階を追跡し、それに合ったシェルを渡す。 */
class AccessManager(private val context: Context) {

    private val _state = MutableStateFlow(AccessState())
    val state: StateFlow<AccessState> = _state.asStateFlow()

    private val shizukuShell = ShizukuShell(context.packageName)

    /** instrumented テストが UiAutomation によるシェル (Shizuku と同じ uid 2000) を注入する。アプリ本体では決して設定しない。 */
    @Volatile
    @androidx.annotation.VisibleForTesting
    var testShell: PrivilegedShell? = null
    private val rootShell = RootShell()

    private val binderReceived = Shizuku.OnBinderReceivedListener { refresh() }
    private val binderDead = Shizuku.OnBinderDeadListener {
        shizukuShell.release()
        refresh()
    }
    private val permissionResult = Shizuku.OnRequestPermissionResultListener { _, _ -> refresh() }

    init {
        Shizuku.addBinderReceivedListenerSticky(binderReceived)
        Shizuku.addBinderDeadListener(binderDead)
        Shizuku.addRequestPermissionResultListener(permissionResult)
        refresh()
    }

    fun refresh() {
        val installed = isInstalled(SHIZUKU_PACKAGE)
        val test = testShell != null
        val running = test || runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        val tooOld = !test && running && runCatching { Shizuku.isPreV11() }.getOrDefault(false)
        val permission = test || (running && !tooOld &&
            runCatching { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }.getOrDefault(false))
        val uid = if (test) 2000 else if (running) runCatching { Shizuku.getUid() }.getOrNull() else null
        _state.update {
            it.copy(
                shizukuInstalled = installed || running,
                shizukuRunning = running,
                shizukuPermission = permission,
                shizukuTooOld = tooOld,
                shizukuUid = uid,
                usageAccess = UsageReader(context).hasAccess(),
                writeSecureSettings = context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
                    PackageManager.PERMISSION_GRANTED,
            )
        }
    }

    fun requestShizukuPermission() {
        if (runCatching { Shizuku.pingBinder() }.getOrDefault(false)) {
            runCatching { Shizuku.requestPermission(REQUEST_CODE) }
        }
    }

    suspend fun requestRoot(): Boolean {
        val ok = RootShell.request()
        _state.update { it.copy(rootGranted = ok) }
        return ok
    }

    fun setPreferRoot(prefer: Boolean) = _state.update { it.copy(preferRoot = prefer) }

    /** 有効な段階のシェル。Android の標準権限しかない場合は null。 */
    fun shell(): PrivilegedShell? = when (_state.value.active) {
        AccessKind.SHIZUKU -> testShell ?: shizukuShell
        AccessKind.ROOT -> rootShell
        else -> null
    }

    /**
     * 特権シェルが使える場合、Prune は自分が使う 2 つの追加権限を自分に付与する:
     * 使用状況へのアクセス (アプリの使用状況とストレージ統計) と WRITE_SECURE_SETTINGS (アニメーション倍率)。
     * どちらも Android の設定で確認・取り消しできる。
     */
    suspend fun grantSelfPermissions(): List<String> {
        val shell = shell() ?: return emptyList()
        val done = mutableListOf<String>()
        val user = android.os.Process.myUid() / 100_000
        if (!_state.value.usageAccess &&
            shell.run(Commands.appOpSet(context.packageName, "GET_USAGE_STATS", "allow", user)).exitCode == 0) {
            done += str(R.string.perm_usage_access)
        }
        if (!_state.value.writeSecureSettings &&
            shell.run(Commands.grant(context.packageName, Manifest.permission.WRITE_SECURE_SETTINGS, user)).exitCode == 0) {
            done += str(R.string.perm_write_secure_settings)
        }
        refresh()
        return done
    }

    private fun isInstalled(pkg: String) = runCatching {
        context.packageManager.getPackageInfo(pkg, PackageManager.MATCH_UNINSTALLED_PACKAGES); true
    }.getOrDefault(false)

    companion object {
        const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
        const val REQUEST_CODE = 4711
    }
}
