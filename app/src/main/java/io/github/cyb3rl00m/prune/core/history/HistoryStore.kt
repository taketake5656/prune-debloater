package io.github.cyb3rl00m.prune.core.history

import android.content.Context
import io.github.cyb3rl00m.prune.core.plan.PlannedChange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class HistoryEntry(
    val id: String,
    val change: PlannedChange,
    /** 変更前の状態: "enabled" | "disabled" | "removed" | app-op のモード | "granted" | "exempt" | 設定値。 */
    val before: String? = null,
    val after: String? = null,
    /** null = まだ検証していない (結果を確認していない ADB スクリプト)。 */
    val success: Boolean? = null,
    val message: String = "",
    val commands: List<String> = emptyList(),
    val timestamp: Long = System.currentTimeMillis(),
    val undone: Boolean = false,
    /** 取り消し操作の場合、取り消す対象のエントリの id。 */
    val undoOf: String? = null,
)

@Serializable
data class Batch(
    val id: String,
    val title: String,
    val origin: String,
    val timestamp: Long,
    val access: String,
    val entries: List<HistoryEntry>,
    /** ADB スクリプトとして書き出したプランなら true。結果はユーザーが「結果を確認」をタップしたときに確認する。 */
    val script: Boolean = false,
)

/** Prune が最初に変更したときのパッケージの状態。「すべて元に戻す」で使う。 */
@Serializable
data class OriginalState(val packageName: String, val label: String, val state: String, val recordedAt: Long)

@Serializable
data class HistoryFile(
    val version: Int = 1,
    val batches: List<Batch> = emptyList(),
    val originals: Map<String, OriginalState> = emptyMap(),
)

/** 変更履歴。アプリの非公開ストレージに JSON ファイルとして保存する。 */
class HistoryStore(context: Context) {

    private val file = File(context.filesDir, "history.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = false }
    private val lock = Mutex()
    private val _state = MutableStateFlow(load())
    val state: StateFlow<HistoryFile> = _state.asStateFlow()

    private fun load(): HistoryFile = runCatching {
        if (file.exists()) json.decodeFromString(HistoryFile.serializer(), file.readText()) else HistoryFile()
    }.getOrElse {
        // 読めないファイルは黙って履歴を失うのではなく、コピーを残しておく。
        runCatching { file.copyTo(File(file.parentFile, "history-unreadable-${System.currentTimeMillis()}.json")) }
        HistoryFile()
    }

    suspend fun update(transform: (HistoryFile) -> HistoryFile) = lock.withLock {
        val next = transform(_state.value)
        withContext(Dispatchers.IO) {
            val tmp = File(file.parentFile, "history.json.tmp")
            tmp.writeText(json.encodeToString(HistoryFile.serializer(), next))
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        }
        _state.value = next
    }

    suspend fun addBatch(batch: Batch, originals: Map<String, OriginalState>) = update { h ->
        h.copy(
            batches = listOf(batch) + h.batches,
            originals = originals.filterKeys { it !in h.originals } + h.originals,
        )
    }

    suspend fun replaceBatch(batch: Batch) = update { h ->
        h.copy(batches = h.batches.map { if (it.id == batch.id) batch else it })
    }

    suspend fun markUndone(entryIds: Set<String>) = update { h ->
        h.copy(batches = h.batches.map { b ->
            b.copy(entries = b.entries.map { if (it.id in entryIds) it.copy(undone = true) else it })
        })
    }

    fun exportJson(): String = Json { prettyPrint = true; encodeDefaults = true }
        .encodeToString(HistoryFile.serializer(), _state.value)
}
