package com.mohdshayan.inkhorn.data

import android.content.Context
import android.os.SystemClock
import com.mohdshayan.inkhorn.content.ContentLoader
import com.mohdshayan.inkhorn.data.db.ChoiceRecordEntity
import com.mohdshayan.inkhorn.data.db.GameDao
import com.mohdshayan.inkhorn.data.db.LedgerEntryEntity
import com.mohdshayan.inkhorn.data.db.RunEntity
import com.mohdshayan.inkhorn.data.db.SnapshotEntity
import com.mohdshayan.inkhorn.data.prefs.AppPrefs
import com.mohdshayan.inkhorn.engine.AwayEntry
import com.mohdshayan.inkhorn.engine.AwaySummary
import com.mohdshayan.inkhorn.engine.CatchUp
import com.mohdshayan.inkhorn.engine.Content
import com.mohdshayan.inkhorn.engine.Engine
import com.mohdshayan.inkhorn.engine.EngineEvent
import com.mohdshayan.inkhorn.engine.EventKind
import com.mohdshayan.inkhorn.engine.GameState
import com.mohdshayan.inkhorn.engine.NumberWords
import com.mohdshayan.inkhorn.engine.Outcome
import com.mohdshayan.inkhorn.engine.Projection
import com.mohdshayan.inkhorn.engine.SaveCheck
import com.mohdshayan.inkhorn.engine.SaveCodec
import com.mohdshayan.inkhorn.engine.SaveFile
import com.mohdshayan.inkhorn.work.ChapterNoteWorker
import com.mohdshayan.inkhorn.work.Notes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class LoadStatus { LOADING, READY, UNREADABLE }

/** A ledger row as the screen shows it. Rows not yet saved have no [dbId]. */
data class LedgerLine(
    val key: Long,
    val dbId: Long?,
    val runId: Long,
    val gameDay: Int,
    val chapter: Int,
    val kind: String,
    val text: String,
    val createdAt: Long,
    val ref: String? = null,
    val base: String = text,
    val count: Int = 1,
    val version: Int = 0,
)

data class ChronicleData(val runs: List<RunEntity> = emptyList(), val choices: List<ChoiceRecordEntity> = emptyList())

/**
 * Owns the running game: StateFlow<GameState>, a 10 Hz foreground tick, autosave every 30 seconds
 * and on stop, catch-up on start, and the chapter-note job on stop. All mutation happens on the
 * main thread; the database and the catch-up replay run off it.
 */
class GameRepository(
    context: Context,
    private val dao: GameDao,
    private val prefs: AppPrefs,
    private val loader: ContentLoader,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _status = MutableStateFlow(LoadStatus.LOADING)
    val status: StateFlow<LoadStatus> = _status

    private val _content = MutableStateFlow<Content?>(null)
    val content: StateFlow<Content?> = _content

    private val _state = MutableStateFlow<GameState?>(null)
    val state: StateFlow<GameState?> = _state

    private val _run = MutableStateFlow<RunEntity?>(null)
    val run: StateFlow<RunEntity?> = _run

    private val _ledger = MutableStateFlow<List<LedgerLine>>(emptyList())
    val ledger: StateFlow<List<LedgerLine>> = _ledger

    private val _away = MutableStateFlow<AwayEntry?>(null)
    val away: StateFlow<AwayEntry?> = _away

    private val _notesAsk = MutableStateFlow(false)
    val notesAsk: StateFlow<Boolean> = _notesAsk

    private val _banner = MutableStateFlow<String?>(null)
    val banner: StateFlow<String?> = _banner

    private val _chronicle = MutableStateFlow(ChronicleData())
    val chronicle: StateFlow<ChronicleData> = _chronicle

    private val _reviewDue = MutableStateFlow(false)
    val reviewDue: StateFlow<Boolean> = _reviewDue

    private val _endingToShow = MutableStateFlow<String?>(null)
    /** An ending the run has just reached and the screen has not opened yet, even if it was reached while away. */
    val endingToShow: StateFlow<String?> = _endingToShow

    var engine: Engine? = null
        private set

    private var foreground = false
    private var loadJob: Job? = null
    private var tickJob: Job? = null
    private var resuming = false
    private var previousJson: String? = null
    private var nextKey = -1L
    private val dirty = HashSet<Long>()
    private var playAccum = 0.0
    private val saveMutex = Mutex()

    // region lifecycle

    fun start() {
        if (loadJob == null) loadJob = scope.launch { load() }
    }

    fun onForeground() {
        foreground = true
        start()
        if (_status.value == LoadStatus.READY) scope.launch { resume() }
    }

    fun onBackground() {
        foreground = false
        tickJob?.cancel()
        tickJob = null
        if (_status.value != LoadStatus.READY) return
        scope.launch {
            save()
            scheduleNote()
        }
    }

    private suspend fun load() {
        _status.value = LoadStatus.LOADING
        val content = loader.load()
        _content.value = content
        val eng = Engine(content)
        engine = eng
        val run = dao.openRun() ?: dao.latestRun()
        if (run == null) {
            newRun(eng, chronicleNumber = 1, newGamePlus = false)
        } else {
            val snap = dao.snapshot(run.id)
            // A state this content cannot run counts as unreadable, so a bad save never crash-loops the app.
            val readable = { json: String? -> SaveCodec.decodeState(json)?.takeIf { SaveCheck.playable(content, it) } }
            var decoded = readable(snap?.stateJson)
            previousJson = snap?.stateJson
            if (decoded == null) {
                decoded = readable(snap?.previousStateJson)
                previousJson = snap?.previousStateJson
                if (decoded != null) _banner.value = "This save could not be read. Your last good save is loaded."
            }
            _run.value = run
            if (decoded == null) {
                refreshChronicle()
                _status.value = LoadStatus.UNREADABLE
                return
            }
            _state.value = decoded
            _ledger.value = dao.recentLedger(run.id, LEDGER_LIMIT).map { it.toLine() }
        }
        refreshChronicle()
        _status.value = LoadStatus.READY
        if (foreground) resume()
    }

    private suspend fun resume() {
        val eng = engine ?: return
        if (tickJob != null || resuming) return
        resuming = true
        try {
            catchUpAndTick(eng)
        } finally {
            resuming = false
        }
    }

    private suspend fun catchUpAndTick(eng: Engine) {
        ChapterNoteWorker.cancel(appContext)
        val before = _state.value ?: return
        val now = System.currentTimeMillis()
        val elapsed = CatchUp.elapsedSeconds(before.lastWallClockMs, now)
        if (elapsed > 0.0 && !before.finished) {
            val result = withContext(Dispatchers.Default) { CatchUp.run(eng, before, elapsed) }
            // A tap may have landed while the replay ran; the replay wins because it saw the same start.
            _state.value = result.state
            handleEvents(result.events, before)
            AwaySummary.build(eng.content, before, result)?.let { entry ->
                addLine("away", entry.entry, null, result.state)
                _away.value = entry
            }
        }
        _state.value = _state.value?.copy(lastWallClockMs = now)
        if (foreground) startTicking(eng) else save()
    }

    private fun startTicking(eng: Engine) {
        tickJob?.cancel()
        tickJob = scope.launch {
            var last = SystemClock.elapsedRealtime()
            var carry = 0L
            var sinceSave = 0L
            while (isActive) {
                delay(TICK_MS)
                val now = SystemClock.elapsedRealtime()
                val passed = (now - last).coerceAtLeast(0)
                last = now
                carry += passed
                sinceSave += passed
                // A long stall (debugger, a frozen frame) is not replayed tick by tick.
                if (carry > 5_000) carry = TICK_MS
                var s = _state.value ?: continue
                val events = ArrayList<EngineEvent>()
                val start = s
                while (carry >= TICK_MS) {
                    carry -= TICK_MS
                    val out = eng.tick(s, TICK_MS / 1000.0)
                    s = out.state
                    events += out.events
                    playAccum += TICK_MS / 1000.0
                }
                if (s !== start) {
                    _state.value = s
                    if (events.isNotEmpty()) handleEvents(events, start)
                }
                if (sinceSave >= AUTOSAVE_MS) {
                    sinceSave = 0
                    save()
                }
            }
        }
    }

    // endregion

    // region player actions

    fun act(actionId: String): Outcome? = apply { it.act(this, actionId) }
    fun hire(workerId: String): Outcome? = apply { it.hire(this, workerId) }
    fun buy(upgradeId: String): Outcome? = apply { it.buy(this, upgradeId) }

    private inline fun apply(block: GameState.(Engine) -> Outcome): Outcome? {
        val eng = engine ?: return null
        val s = _state.value ?: return null
        val out = s.block(eng)
        if (out.ok) {
            _state.value = out.state
            handleEvents(out.events, s)
        }
        return out
    }

    /** Writes the choice and the snapshot in one transaction. False when the write failed; nothing changes. */
    suspend fun commit(choiceId: String, optionId: String): Boolean {
        val eng = engine ?: return false
        val before = _state.value ?: return false
        val run = _run.value ?: return false
        val out = eng.commit(before, choiceId, optionId)
        if (!out.ok) return false
        val linesBefore = _ledger.value
        _state.value = out.state
        handleEvents(out.events, before)
        val record = ChoiceRecordEntity(run.id, choiceId, optionId, eng.content.choiceById.getValue(choiceId).chapter, before.gameDay, System.currentTimeMillis())
        return try {
            saveMutex.withLock { persist(record) }
            refreshChronicle()
            true
        } catch (e: Exception) {
            _state.value = before
            _ledger.value = linesBefore
            false
        }
    }

    fun endingShown() {
        _endingToShow.value = null
    }

    fun dismissAway() {
        _away.value = null
    }

    fun dismissBanner() {
        _banner.value = null
    }

    fun answerNotesAsk() {
        _notesAsk.value = false
        scope.launch { prefs.setNotesAsked() }
    }

    fun reviewShown() {
        _reviewDue.value = false
        scope.launch { prefs.setReviewPrompted() }
    }

    suspend fun beginSecondChronicle() {
        val eng = engine ?: return
        save()
        val number = (dao.runs().maxOfOrNull { it.chronicleNumber } ?: 0) + 1
        newRun(eng, number, newGamePlus = true)
        refreshChronicle()
    }

    /** From the unreadable-save screen: the broken run is closed and a fresh one begins. */
    suspend fun beginNewChronicle() {
        val eng = engine ?: Engine(loader.load()).also { engine = it }
        _run.value?.let { dao.upsertRun(it.copy(endedAt = System.currentTimeMillis())) }
        val runs = dao.runs()
        val number = (runs.maxOfOrNull { it.chronicleNumber } ?: 0) + 1
        newRun(eng, number, newGamePlus = runs.any { it.endingId != null })
        refreshChronicle()
        _status.value = LoadStatus.READY
        if (foreground) resume()
    }

    private suspend fun newRun(eng: Engine, chronicleNumber: Int, newGamePlus: Boolean) {
        tickJob?.cancel()
        tickJob = null
        val now = System.currentTimeMillis()
        val id = dao.insertRun(RunEntity(startedAt = now, chronicleNumber = chronicleNumber))
        _run.value = dao.runs().first { it.id == id }
        val out = eng.newGame(now, newGamePlus)
        previousJson = null
        dirty.clear()
        _ledger.value = emptyList()
        _state.value = out.state
        _away.value = null
        _banner.value = null
        handleEvents(out.events, out.state)
        save()
        if (foreground) startTicking(eng)
    }

    // endregion

    // region events and ledger

    private fun handleEvents(events: List<EngineEvent>, before: GameState) {
        val after = _state.value ?: return
        for (e in events) {
            when (e.kind) {
                EventKind.STORY -> addLine("story", e.text, e.ref, after)
                EventKind.ACTION -> addLine("action", e.text, e.ref, after)
                EventKind.PURCHASE -> addLine("purchase", e.text, e.ref, after)
                EventKind.CHOICE_OPENED -> addLine("story", e.text, e.ref, after)
                EventKind.CHOICE_MADE -> addLine("choice", e.text, e.ref, after)
                EventKind.CHAPTER_END -> {
                    val finished = e.ref?.substringAfter(':')?.toIntOrNull() ?: before.chapter
                    addLine("chapter_end", e.text, e.ref, after, chapter = finished)
                    scope.launch {
                        prefs.addChapterCompleted()
                        val settings = prefs.current()
                        if (finished == 1 && !settings.notesAsked) _notesAsk.value = true
                        if (finished == 3 && !settings.reviewPrompted) _reviewDue.value = true
                    }
                }
                EventKind.ENDING -> {
                    val endingId = e.ref?.substringAfter(':') ?: continue
                    addLine("ending", e.text, e.ref, after)
                    _run.value = _run.value?.copy(endedAt = System.currentTimeMillis(), endingId = endingId)
                    scope.launch {
                        save()
                        refreshChronicle()
                    }
                    _endingToShow.value = endingId
                }
            }
        }
    }

    private fun addLine(kind: String, text: String, ref: String?, state: GameState, chapter: Int = state.chapter) {
        val run = _run.value ?: return
        val lines = _ledger.value
        val repeatable = kind == "action" || kind == "purchase"
        if (repeatable) {
            // Routine rows at the top of the ledger fold together: "Copied a line, thirty-one times".
            val routine = lines.takeWhile { it.kind == "action" || it.kind == "purchase" }
            val index = routine.indexOfFirst { it.kind == kind && it.ref == ref && it.base == text }
            if (index >= 0) {
                val row = lines[index]
                val count = row.count + 1
                val times = if (count == 2) "twice" else "${NumberWords.words(count)} times"
                val merged = row.copy(text = "$text, $times", count = count, version = row.version + 1, gameDay = state.gameDay)
                if (merged.dbId != null) dirty += merged.key
                _ledger.value = lines.toMutableList().also { it[index] = merged }
                return
            }
        }
        val line = LedgerLine(
            key = nextKey--, dbId = null, runId = run.id, gameDay = state.gameDay, chapter = chapter,
            kind = kind, text = text, createdAt = System.currentTimeMillis(), ref = ref,
        )
        _ledger.value = (listOf(line) + lines).take(LEDGER_LIMIT)
    }

    // endregion

    // region persistence

    suspend fun save() {
        try {
            saveMutex.withLock { persist(null) }
        } catch (_: Exception) {
            // The next autosave tries again; the previous snapshot is still intact.
        }
    }

    private suspend fun persist(choice: ChoiceRecordEntity?) {
        val run = _run.value ?: return
        val s = _state.value ?: return
        val now = System.currentTimeMillis()
        val stamped = s.copy(lastWallClockMs = now)
        _state.value = stamped
        val json = SaveCodec.encodeState(stamped)
        val lines = _ledger.value
        val pending = lines.filter { it.dbId == null }.reversed()
        val versions = pending.associate { it.key to it.version }
        val edits = lines.filter { it.dbId != null && it.key in dirty }.associate { it.dbId!! to it.text }
        val whole = playAccum.toLong()
        val updatedRun = run.copy(playSeconds = run.playSeconds + whole)
        val snapshot = SnapshotEntity(run.id, Engine.VERSION, json, previousJson, now)
        val ids = dao.save(updatedRun, snapshot, pending.map { it.toEntity() }, edits, choice)
        playAccum -= whole
        dirty.removeAll(edits.keys.mapNotNull { id -> lines.firstOrNull { it.dbId == id }?.key }.toSet())
        previousJson = json
        _run.value = _run.value?.let { current -> if (current.id == updatedRun.id) current.copy(playSeconds = updatedRun.playSeconds) else current }
        if (ids.isNotEmpty()) {
            val idByKey = pending.map { it.key }.zip(ids).toMap()
            _ledger.value = _ledger.value.map { l ->
                val id = idByKey[l.key] ?: return@map l
                if (l.version != versions[l.key]) dirty += l.key
                l.copy(dbId = id)
            }
        }
    }

    suspend fun refreshChronicle() {
        _chronicle.value = ChronicleData(dao.runs(), dao.allChoices())
    }

    private suspend fun scheduleNote() {
        val eng = engine ?: return
        if (!prefs.current().chapterNotes || !Notes.canPost(appContext)) return
        val s = _state.value ?: return
        if (s.finished || s.pendingChoiceId != null) return
        val seconds = withContext(Dispatchers.Default) { Projection.secondsToGoal(eng, s) } ?: return
        ChapterNoteWorker.enqueue(appContext, seconds)
    }

    /** Everything the save file holds, after a fresh save so the snapshot is current. */
    suspend fun exportSave(): SaveFile {
        save()
        return SaveTransfer.toSaveFile(dao, _run.value?.id)
    }

    /** Replaces every table with the file's contents, then reloads as if the app had just started. */
    suspend fun importSave(file: SaveFile) {
        tickJob?.cancel()
        tickJob = null
        try {
            saveMutex.withLock {
                SaveTransfer.replaceAll(dao, file, System.currentTimeMillis())
            }
        } catch (e: Exception) {
            // Nothing was replaced; the current run carries on.
            engine?.let { if (foreground && _status.value == LoadStatus.READY) startTicking(it) }
            throw e
        }
        previousJson = null
        dirty.clear()
        _ledger.value = emptyList()
        _away.value = null
        _banner.value = null
        _notesAsk.value = false
        loadJob = scope.launch { load() }
        loadJob?.join()
    }

    // endregion

    private fun LedgerEntryEntity.toLine() = LedgerLine(
        key = id, dbId = id, runId = runId, gameDay = gameDay, chapter = chapter, kind = kind, text = text, createdAt = createdAt,
    )

    private fun LedgerLine.toEntity() = LedgerEntryEntity(
        runId = runId, gameDay = gameDay, chapter = chapter, kind = kind, text = text, createdAt = createdAt,
    )

    companion object {
        const val TICK_MS = 100L
        const val AUTOSAVE_MS = 30_000L
        const val LEDGER_LIMIT = 200
    }
}
