package com.mohdshayan.inkhorn.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert

@Entity(tableName = "runs")
data class RunEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val endedAt: Long? = null,
    val endingId: String? = null,
    val chronicleNumber: Int,
    val playSeconds: Long = 0,
)

@Entity(tableName = "snapshots")
data class SnapshotEntity(
    @PrimaryKey val runId: Long,
    val engineVersion: Int,
    val stateJson: String,
    /** The autosave before this one, loaded if stateJson fails to decode. */
    val previousStateJson: String?,
    val savedAt: Long,
)

@Entity(tableName = "ledger", indices = [Index("runId")])
data class LedgerEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val runId: Long,
    val gameDay: Int,
    val chapter: Int,
    /** story, away, choice, chapter_end, action, purchase, ending. */
    val kind: String,
    val text: String,
    val createdAt: Long,
)

@Entity(tableName = "choices", primaryKeys = ["runId", "choiceId"])
data class ChoiceRecordEntity(
    val runId: Long,
    val choiceId: String,
    val optionId: String,
    val chapter: Int,
    val gameDay: Int,
    val madeAt: Long,
)

@Dao
abstract class GameDao {
    @Query("SELECT * FROM runs ORDER BY id DESC")
    abstract suspend fun runs(): List<RunEntity>

    @Query("SELECT * FROM runs WHERE endedAt IS NULL ORDER BY id DESC LIMIT 1")
    abstract suspend fun openRun(): RunEntity?

    @Query("SELECT * FROM runs ORDER BY id DESC LIMIT 1")
    abstract suspend fun latestRun(): RunEntity?

    @Insert
    abstract suspend fun insertRun(run: RunEntity): Long

    @Upsert
    abstract suspend fun upsertRun(run: RunEntity)

    @Query("SELECT * FROM snapshots WHERE runId = :runId")
    abstract suspend fun snapshot(runId: Long): SnapshotEntity?

    @Upsert
    abstract suspend fun upsertSnapshot(snapshot: SnapshotEntity)

    @Query("SELECT * FROM ledger WHERE runId = :runId ORDER BY id DESC LIMIT :limit")
    abstract suspend fun recentLedger(runId: Long, limit: Int): List<LedgerEntryEntity>

    @Query("SELECT * FROM ledger ORDER BY id")
    abstract suspend fun allLedger(): List<LedgerEntryEntity>

    @Insert
    abstract suspend fun insertLedger(rows: List<LedgerEntryEntity>): List<Long>

    @Query("UPDATE ledger SET text = :text WHERE id = :id")
    abstract suspend fun updateLedgerText(id: Long, text: String)

    @Query("SELECT * FROM choices ORDER BY madeAt")
    abstract suspend fun allChoices(): List<ChoiceRecordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertChoice(choice: ChoiceRecordEntity)

    @Query("DELETE FROM runs") abstract suspend fun clearRuns()
    @Query("DELETE FROM snapshots") abstract suspend fun clearSnapshots()
    @Query("DELETE FROM ledger") abstract suspend fun clearLedger()
    @Query("DELETE FROM choices") abstract suspend fun clearChoices()

    @Insert abstract suspend fun insertRuns(rows: List<RunEntity>)
    @Insert abstract suspend fun insertSnapshots(rows: List<SnapshotEntity>)
    @Insert abstract suspend fun insertChoices(rows: List<ChoiceRecordEntity>)

    /** Autosave: the snapshot, any ledger rows written since the last save and a just-committed choice, together. */
    @Transaction
    open suspend fun save(
        run: RunEntity,
        snapshot: SnapshotEntity,
        pending: List<LedgerEntryEntity>,
        edits: Map<Long, String>,
        choice: ChoiceRecordEntity?,
    ): List<Long> {
        upsertRun(run)
        if (choice != null) insertChoice(choice)
        upsertSnapshot(snapshot)
        for ((id, text) in edits) updateLedgerText(id, text)
        return if (pending.isEmpty()) emptyList() else insertLedger(pending)
    }

    /** Load a save file: all four tables replaced at once, or none of them. */
    @Transaction
    open suspend fun replaceAll(runs: List<RunEntity>, snapshots: List<SnapshotEntity>, ledger: List<LedgerEntryEntity>, choices: List<ChoiceRecordEntity>) {
        clearLedger(); clearChoices(); clearSnapshots(); clearRuns()
        insertRuns(runs)
        insertSnapshots(snapshots)
        if (ledger.isNotEmpty()) insertLedger(ledger)
        insertChoices(choices)
    }
}

@Database(
    entities = [RunEntity::class, SnapshotEntity::class, LedgerEntryEntity::class, ChoiceRecordEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun gameDao(): GameDao

    companion object {
        const val NAME = "inkhorn.db"

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                    .build().also { instance = it }
            }
    }
}
