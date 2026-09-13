package com.mohdshayan.inkhorn.engine

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class RunRecord(
    val id: Long,
    val startedAt: Long,
    val endedAt: Long? = null,
    val endingId: String? = null,
    val chronicleNumber: Int,
    val playSeconds: Long,
)

@Serializable
data class SnapshotRecord(val runId: Long, val state: GameState)

@Serializable
data class LedgerRecord(
    val id: Long,
    val runId: Long,
    val gameDay: Int,
    val chapter: Int,
    val kind: String,
    val text: String,
    val createdAt: Long,
)

@Serializable
data class ChoiceRecord(
    val runId: Long,
    val choiceId: String,
    val optionId: String,
    val chapter: Int,
    val gameDay: Int,
    val madeAt: Long,
)

@Serializable
data class SaveFile(
    val format: String = SaveCodec.FORMAT,
    val formatVersion: Int = SaveCodec.FORMAT_VERSION,
    val engineVersion: Int = Engine.VERSION,
    val exportedAt: Long,
    val runs: List<RunRecord>,
    val snapshot: SnapshotRecord?,
    val ledger: List<LedgerRecord>,
    val choices: List<ChoiceRecord>,
)

sealed interface Decoded<out T> {
    data class Ok<T>(val value: T) : Decoded<T>
    /** Not JSON, or JSON without the inkhorn-save marker. */
    data object NotASave : Decoded<Nothing>
    /** Written by a newer version of Inkhorn than this one. */
    data object Newer : Decoded<Nothing>
}

/** Encodes the save file and the snapshot state, and refuses anything that is not ours. */
object SaveCodec {
    const val FORMAT = "inkhorn-save"
    const val FORMAT_VERSION = 1

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    fun encode(save: SaveFile): String = json.encodeToString(SaveFile.serializer(), save)

    /** With [content], a file whose snapshot this game cannot run is refused as not a save. */
    fun decode(text: String, content: Content? = null): Decoded<SaveFile> {
        val root = try {
            json.parseToJsonElement(text).jsonObject
        } catch (_: Exception) {
            return Decoded.NotASave
        }
        val format = try { root["format"]?.jsonPrimitive?.content } catch (_: Exception) { null }
        if (format != FORMAT) return Decoded.NotASave
        val formatVersion = try { root["formatVersion"]?.jsonPrimitive?.int } catch (_: Exception) { null } ?: return Decoded.NotASave
        val engineVersion = try { root["engineVersion"]?.jsonPrimitive?.int } catch (_: Exception) { null } ?: return Decoded.NotASave
        if (formatVersion > FORMAT_VERSION || engineVersion > Engine.VERSION) return Decoded.Newer
        return try {
            val save = json.decodeFromJsonElement(SaveFile.serializer(), root)
            when {
                save.snapshot != null && save.runs.none { it.id == save.snapshot.runId } -> Decoded.NotASave
                content != null && !SaveCheck.loadable(content, save) -> Decoded.NotASave
                else -> Decoded.Ok(save)
            }
        } catch (_: Exception) {
            Decoded.NotASave
        }
    }

    fun encodeState(state: GameState): String = json.encodeToString(GameState.serializer(), state)

    /** Null when the text is not a readable state, so the caller can fall back to the previous save. */
    fun decodeState(text: String?): GameState? {
        if (text.isNullOrBlank()) return null
        return try {
            json.decodeFromString(GameState.serializer(), text)
        } catch (_: Exception) {
            null
        }
    }

    fun fileName(state: GameState?): String = "inkhorn-save-day${state?.gameDay ?: 1}.json"
}
