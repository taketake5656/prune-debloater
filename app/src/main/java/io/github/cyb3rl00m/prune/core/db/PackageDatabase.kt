package io.github.cyb3rl00m.prune.core.db

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** 1 パッケージ分のコミュニティデータ (UAD-ng)。キーは tools/build_db.py が書き出す短縮形。 */
@Serializable
data class UadEntry(
    @SerialName("l") val list: String,
    @SerialName("r") val removal: String,
    @SerialName("d") val description: String = "",
    @SerialName("dep") val dependencies: List<String> = emptyList(),
    @SerialName("nb") val neededBy: List<String> = emptyList(),
)

@Serializable
data class LockedEntry(val id: String, val reason: String)

@Serializable
data class LockedPrefix(val prefix: String, val reason: String)

@Serializable
data class Group(
    val key: String,
    val name: String,
    val warning: String,
    val packages: List<String> = emptyList(),
    val prefixes: List<String> = emptyList(),
    /** パッケージ名に含まれる部分文字列 (featureTags のみ)。 */
    val patterns: List<String> = emptyList(),
)

@Serializable
data class CuratedEntry(
    val level: Int,
    val risk: String,
    val note: String? = null,
    val google: Boolean = false,
    /** 設定されている場合、この Android バージョン (API レベル) でのみ適用する。 */
    val minSdk: Int? = null,
    val maxSdk: Int? = null,
    /** 設定されている場合、これらのファームウェア (例: "HyperOS") でのみ適用する。 */
    val firmware: List<String> = emptyList(),
    val consequences: String? = null,
    val recovery: String? = null,
)

@Serializable
data class PrefixEntry(
    val prefix: String,
    val level: Int,
    val risk: String,
    val note: String? = null,
)

/** ルールファイル 1 つ分。core.json またはメーカー別ファイル。 */
@Serializable
data class PruneRules(
    val schema: Int,
    val manufacturer: String = "Generic Android",
    val brands: List<String> = emptyList(),
    val updated: String,
    val comment: String = "",
    val locked: List<LockedEntry> = emptyList(),
    val lockedPrefixes: List<LockedPrefix> = emptyList(),
    val ecosystems: List<Group> = emptyList(),
    val featureTags: List<Group> = emptyList(),
    val entries: Map<String, CuratedEntry> = emptyMap(),
    val prefixEntries: List<PrefixEntry> = emptyList(),
) {
    /** 訳文を当てたコピーを返す。訳のない項目は元の文言のまま。判定に使う値 (レベル・リスク等) は変えない。 */
    fun localized(t: RulesTranslation): PruneRules = copy(
        locked = locked.map { e -> t.locked[e.id]?.let { e.copy(reason = it) } ?: e },
        lockedPrefixes = lockedPrefixes.map { e -> t.lockedPrefixes[e.prefix]?.let { e.copy(reason = it) } ?: e },
        ecosystems = ecosystems.map { it.localized(t) },
        featureTags = featureTags.map { it.localized(t) },
        entries = entries.mapValues { (pkg, e) ->
            val tr = t.entries[pkg] ?: return@mapValues e
            e.copy(
                note = tr.note ?: e.note,
                consequences = tr.consequences ?: e.consequences,
                recovery = tr.recovery ?: e.recovery,
            )
        },
        prefixEntries = prefixEntries.map { e -> t.prefixEntries[e.prefix]?.let { e.copy(note = it) } ?: e },
    )

    private fun Group.localized(t: RulesTranslation): Group {
        val tr = t.groups[key] ?: return this
        return copy(name = tr.name ?: name, warning = tr.warning ?: warning)
    }
}

/**
 * ルールファイル 1 つ分の訳文 (tools/rules/<言語>/<同名ファイル>.json)。
 * 元のルールファイルを変更せずに翻訳できるよう、ID・プレフィックス・グループキーをキーにして文言だけを持つ。
 */
@Serializable
data class RulesTranslation(
    val locked: Map<String, String> = emptyMap(),
    val lockedPrefixes: Map<String, String> = emptyMap(),
    val groups: Map<String, GroupTranslation> = emptyMap(),
    val entries: Map<String, EntryTranslation> = emptyMap(),
    val prefixEntries: Map<String, String> = emptyMap(),
)

@Serializable
data class GroupTranslation(val name: String? = null, val warning: String? = null)

@Serializable
data class EntryTranslation(val note: String? = null, val consequences: String? = null, val recovery: String? = null)

/** キュレーション済みエントリと、その出どころのルールファイル。 */
data class SourcedEntry(val entry: CuratedEntry, val manufacturer: String)

/** ファームウェアやバージョン限定のエントリを適用するために、分類器が知る必要のある端末情報。 */
data class DeviceContext(val sdkInt: Int = Int.MAX_VALUE, val firmware: String? = null)

/**
 * Prune のパッケージ知識: コアルール、全メーカーのルール、コミュニティの説明文。
 * メーカー別ファイルは常にすべて読み込む。パッケージ名の意味はどの端末でも同じであり、
 * リブランドされたファームウェア (Redmi、POCO、OnePlus、realme など) でもルールが見つかるようにするため。
 */
class PackageDatabase(val files: List<PruneRules>, val uad: Map<String, UadEntry>) {

    private val lockedById: Map<String, String> = files.flatMap { it.locked }.associate { it.id to it.reason }
    private val lockedPrefixes: List<LockedPrefix> = files.flatMap { it.lockedPrefixes }
    private val entries: Map<String, SourcedEntry> =
        files.flatMap { f -> f.entries.map { (k, v) -> k to SourcedEntry(v, f.manufacturer) } }.toMap()
    private val prefixEntries: List<PrefixEntry> = files.flatMap { it.prefixEntries }

    /** エコシステム (「使っている機能」)。ファイルをまたいでキーごとにマージする。 */
    val ecosystems: List<Group> = merge(files.flatMap { it.ecosystems })
    val featureTags: List<Group> = merge(files.flatMap { it.featureTags })
    val ecosystemsByKey: Map<String, Group> = ecosystems.associateBy { it.key }
    val updated: String = files.maxOfOrNull { it.updated } ?: ""
    val curatedCount: Int = entries.size

    fun lockedReason(pkg: String): String? =
        lockedById[pkg] ?: lockedPrefixes.firstOrNull { pkg.startsWith(it.prefix) }?.reason

    /** パッケージのキュレーション済みエントリ。この Android バージョンとファームウェアに当てはまる場合のみ返す。 */
    fun curated(pkg: String, device: DeviceContext = DeviceContext()): SourcedEntry? {
        val s = entries[pkg] ?: return null
        val e = s.entry
        if (e.minSdk != null && device.sdkInt < e.minSdk) return null
        if (e.maxSdk != null && device.sdkInt > e.maxSdk) return null
        if (e.firmware.isNotEmpty() && device.firmware != null &&
            e.firmware.none { device.firmware.startsWith(it, ignoreCase = true) }) return null
        return s
    }

    fun prefixEntry(pkg: String): PrefixEntry? = prefixEntries.firstOrNull { pkg.startsWith(it.prefix) }

    fun ecosystemsOf(pkg: String): List<Group> = ecosystems.filter { g -> matches(g, pkg) }

    fun featureTagsOf(pkg: String): List<Group> = featureTags.filter { g -> matches(g, pkg) }

    private fun matches(g: Group, pkg: String): Boolean =
        pkg in g.packages || g.prefixes.any { pkg.startsWith(it) } ||
            g.patterns.any { pkg.contains(it, ignoreCase = true) }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** 複数ファイルで同じキーのグループは 1 つにまとめる (名前と警告は最初のものを採用)。 */
        private fun merge(groups: List<Group>): List<Group> = groups.groupBy { it.key }.map { (_, gs) ->
            gs.first().copy(
                packages = gs.flatMap { it.packages }.distinct(),
                prefixes = gs.flatMap { it.prefixes }.distinct(),
                patterns = gs.flatMap { it.patterns }.distinct(),
            )
        }

        fun parseRules(text: String): PruneRules = json.decodeFromString(PruneRules.serializer(), text)

        fun parseTranslation(text: String): RulesTranslation = json.decodeFromString(RulesTranslation.serializer(), text)

        /**
         * @param translations [rulesJson] と同じ順序の訳文 JSON。訳がないファイルは null。
         *   壊れた訳文は無視して元の文言を使う (訳文のせいでルールが読めなくなることはない)。
         */
        fun parse(rulesJson: List<String>, uadJson: String, translations: List<String?> = emptyList()): PackageDatabase =
            PackageDatabase(
                rulesJson.mapIndexedNotNull { i, text ->
                    val rules = runCatching { parseRules(text) }.getOrNull() ?: return@mapIndexedNotNull null
                    val tr = translations.getOrNull(i)?.let { runCatching { parseTranslation(it) }.getOrNull() }
                    if (tr == null) rules else rules.localized(tr)
                },
                json.decodeFromString(MapSerializer(String.serializer(), UadEntry.serializer()), uadJson),
            )
    }
}
