package io.github.cyb3rl00m.prune

import android.app.Application
import android.content.Context
import io.github.cyb3rl00m.prune.core.db.Classifier
import io.github.cyb3rl00m.prune.core.db.DeviceContext
import io.github.cyb3rl00m.prune.core.oem.DeviceIdentity
import io.github.cyb3rl00m.prune.core.oem.OemIntegration
import io.github.cyb3rl00m.prune.core.oem.OemRegistry
import io.github.cyb3rl00m.prune.core.scan.DuplicateFinder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import io.github.cyb3rl00m.prune.core.db.PackageDatabase
import io.github.cyb3rl00m.prune.core.device.DeviceInfo
import io.github.cyb3rl00m.prune.core.device.DeviceInfoReader
import io.github.cyb3rl00m.prune.core.history.HistoryStore
import io.github.cyb3rl00m.prune.core.i18n.I18n
import io.github.cyb3rl00m.prune.core.model.AppPackage
import io.github.cyb3rl00m.prune.core.optimize.Optimizer
import io.github.cyb3rl00m.prune.core.plan.Executor
import io.github.cyb3rl00m.prune.core.scan.AppUsage
import io.github.cyb3rl00m.prune.core.scan.PackageScanner
import io.github.cyb3rl00m.prune.core.scan.UsageReader
import io.github.cyb3rl00m.prune.core.settings.SettingsStore
import io.github.cyb3rl00m.prune.core.shell.AccessManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class PruneApp : Application() {
    lateinit var repository: PruneRepository
        private set

    override fun onCreate() {
        super.onCreate()
        // core/ のコードが文字列リソースを引けるよう、リポジトリより先に初期化する。
        I18n.install(this)
        repository = PruneRepository(this)
    }
}

val Context.repository: PruneRepository get() = (applicationContext as PruneApp).repository

data class ScanState(
    val apps: List<AppPackage> = emptyList(),
    val usage: Map<String, AppUsage> = emptyMap(),
    val duplicates: List<DuplicateFinder.DuplicateGroup> = emptyList(),
    val scanning: Boolean = false,
    val lastScan: Long = 0,
    val error: String? = null,
)

/** 全画面で共有する端末状態の唯一の持ち主。 */
class PruneRepository(val context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** 端末の識別情報はアプリ実行中に変わらないので、一度だけ読む。 */
    val identity: DeviceIdentity by lazy { DeviceIdentity.read() }
    val integration: OemIntegration by lazy { OemRegistry.detect(identity) }

    val database: PackageDatabase by lazy {
        val assets = context.assets
        fun read(path: String) = assets.open(path).bufferedReader().use { it.readText() }
        // index.json は core.json を先頭に並べる。壊れたメーカー別ファイルは読み飛ばし、致命的エラーにはしない。
        val files = runCatching {
            Json.parseToJsonElement(read("rules/index.json")).jsonObject["files"]!!.jsonArray.map { it.jsonPrimitive.content }
        }.getOrDefault(listOf("core.json"))
        // 表示言語の訳文 (rules/<言語>/<ファイル名>) があれば、ルールの説明文に当てる。
        val lang = I18n.language
        val loaded = files.mapNotNull { f -> runCatching { read("rules/$f") }.getOrNull()?.let { f to it } }
        val translations = loaded.map { (f, _) -> if (lang == "en") null else runCatching { read("rules/$lang/$f") }.getOrNull() }
        PackageDatabase.parse(loaded.map { it.second }, read("packages_uad.json"), translations)
    }
    val classifier by lazy {
        Classifier(database, DeviceContext(identity.sdkInt, OemRegistry.firmwareSafe(integration, identity)?.name))
    }
    val duplicateFinder = DuplicateFinder(context)
    val scanner = PackageScanner(context)
    val usageReader = UsageReader(context)
    val access = AccessManager(context)
    val history = HistoryStore(context)
    val executor = Executor(context, scanner, history)
    val optimizer = Optimizer(context)
    val settings = SettingsStore(context)

    private val _device = MutableStateFlow<DeviceInfo?>(null)
    val device: StateFlow<DeviceInfo?> = _device.asStateFlow()

    private val _scan = MutableStateFlow(ScanState())
    val scan: StateFlow<ScanState> = _scan.asStateFlow()
    private val scanLock = Mutex()

    init {
        scope.launch { rescan() }
    }

    suspend fun rescan() = scanLock.withLock {
        _scan.update { it.copy(scanning = true, error = null) }
        try {
            withContext(Dispatchers.IO) {
                val info = DeviceInfoReader.read(context, identity)
                _device.value = info
                access.refresh()
                val facts = scanner.scan()
                val oemLocks = runCatching { integration.dynamicProtections(info.services) }.getOrDefault(emptyMap())
                val dynamic = scanner.dynamicProtections(oemLocks)
                val usage = usageReader.read()
                val duplicates = runCatching { duplicateFinder.find() }.getOrDefault(emptyList())
                val dupOf = duplicates.flatMap { g -> g.packages.map { it to g.name } }.toMap()
                val apps = facts.map { f ->
                    AppPackage(f, classifier.classify(f, dynamic), usage[f.packageName]?.lastUsed, dupOf[f.packageName])
                }
                _scan.value = ScanState(apps, usage, duplicates, scanning = false, lastScan = System.currentTimeMillis())
            }
        } catch (t: Throwable) {
            _scan.update { it.copy(scanning = false, error = t.message ?: t.toString()) }
        }
    }

    fun app(pkg: String): AppPackage? = _scan.value.apps.firstOrNull { it.packageName == pkg }
}
