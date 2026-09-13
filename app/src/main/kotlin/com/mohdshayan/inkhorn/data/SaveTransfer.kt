package com.mohdshayan.inkhorn.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.mohdshayan.inkhorn.data.db.ChoiceRecordEntity
import com.mohdshayan.inkhorn.data.db.GameDao
import com.mohdshayan.inkhorn.data.db.LedgerEntryEntity
import com.mohdshayan.inkhorn.data.db.RunEntity
import com.mohdshayan.inkhorn.data.db.SnapshotEntity
import com.mohdshayan.inkhorn.engine.ChoiceRecord
import com.mohdshayan.inkhorn.engine.Content
import com.mohdshayan.inkhorn.engine.Decoded
import com.mohdshayan.inkhorn.engine.Engine
import com.mohdshayan.inkhorn.engine.LedgerRecord
import com.mohdshayan.inkhorn.engine.RunRecord
import com.mohdshayan.inkhorn.engine.SaveCodec
import com.mohdshayan.inkhorn.engine.SaveFile
import com.mohdshayan.inkhorn.engine.SnapshotRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Sends the save through the share sheet as a JSON file, and reads one back from a document URI. */
class SaveTransfer(private val context: Context) {

    /** Writes the file into cache/exports and returns a share-sheet intent for it. */
    suspend fun shareIntent(save: SaveFile): Intent = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, SaveCodec.fileName(save.snapshot?.state))
        file.writeText(SaveCodec.encode(save))
        val uri: Uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        Intent.createChooser(send, "Send save file").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    suspend fun read(uri: Uri, content: Content): Decoded<SaveFile> = withContext(Dispatchers.IO) {
        val text = try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val bytes = stream.readBytes()
                if (bytes.size > MAX_BYTES) null else bytes.decodeToString()
            }
        } catch (_: Exception) {
            null
        }
        if (text == null) Decoded.NotASave else SaveCodec.decode(text, content)
    }

    companion object {
        private const val MAX_BYTES = 20 * 1024 * 1024

        suspend fun toSaveFile(dao: GameDao, currentRunId: Long?): SaveFile {
            val runs = dao.runs().sortedBy { it.id }
            val runId = currentRunId ?: runs.lastOrNull()?.id
            val snapshot = runId?.let { dao.snapshot(it) }
            val state = SaveCodec.decodeState(snapshot?.stateJson) ?: SaveCodec.decodeState(snapshot?.previousStateJson)
            return SaveFile(
                exportedAt = System.currentTimeMillis(),
                runs = runs.map { RunRecord(it.id, it.startedAt, it.endedAt, it.endingId, it.chronicleNumber, it.playSeconds) },
                snapshot = if (runId != null && state != null) SnapshotRecord(runId, state) else null,
                ledger = dao.allLedger().map { LedgerRecord(it.id, it.runId, it.gameDay, it.chapter, it.kind, it.text, it.createdAt) },
                choices = dao.allChoices().map { ChoiceRecord(it.runId, it.choiceId, it.optionId, it.chapter, it.gameDay, it.madeAt) },
            )
        }

        suspend fun replaceAll(dao: GameDao, file: SaveFile, nowMs: Long) {
            // The loaded run resumes from now, so a file sent days ago does not replay days of work.
            val snapshots = listOfNotNull(file.snapshot?.let {
                val state = it.state.copy(lastWallClockMs = nowMs)
                SnapshotEntity(it.runId, Engine.VERSION, SaveCodec.encodeState(state), null, nowMs)
            })
            dao.replaceAll(
                runs = file.runs.map { RunEntity(it.id, it.startedAt, it.endedAt, it.endingId, it.chronicleNumber, it.playSeconds) },
                snapshots = snapshots,
                ledger = file.ledger.map { LedgerEntryEntity(it.id, it.runId, it.gameDay, it.chapter, it.kind, it.text, it.createdAt) },
                choices = file.choices.map { ChoiceRecordEntity(it.runId, it.choiceId, it.optionId, it.chapter, it.gameDay, it.madeAt) },
            )
        }
    }
}
