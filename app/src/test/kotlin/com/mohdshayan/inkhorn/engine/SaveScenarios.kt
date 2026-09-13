package com.mohdshayan.inkhorn.engine

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Not a check: writes believable save files for driving the emulator and taking store shots, by
 * letting the greedy bot play. Runs only when SAVE_OUT names a folder; skipped otherwise.
 */
class SaveScenarios {
    private val engine = TestContent.engine
    private val content = engine.content

    private class Writer(val runId: Long) {
        val ledger = ArrayList<LedgerRecord>()
        val choices = ArrayList<ChoiceRecord>()
        var nextId = 1L
        var lastPurchase: String? = null
        var purchaseCount = 0
    }

    /** Plays [runId] until [stop] says so, with choices picked from [picks] by chapter order. */
    private fun play(
        w: Writer,
        start: GameState,
        picks: List<Int>,
        startMs: Long,
        stop: (GameState) -> Boolean,
    ): GameState {
        var s = start
        var t = 0.0
        val bot = GreedyBot(engine, 0)
        var made = s.choices.size
        while (!stop(s) && t < 20 * 3600) {
            if (t % 10.0 == 0.0) {
                val pending = s.pendingChoiceId
                if (pending != null) {
                    val def = content.choiceById.getValue(pending)
                    val option = def.options[picks[made % picks.size].coerceAtMost(def.options.lastIndex)]
                    val out = engine.commit(s, pending, option.id)
                    record(w, out, s, startMs + (t * 1000).toLong())
                    w.choices += ChoiceRecord(w.runId, pending, option.id, def.chapter, s.gameDay, startMs + (t * 1000).toLong())
                    s = out.state
                    made++
                    if (stop(s)) break
                }
                val before = s
                val after = bot.act(s)
                if (after.workers != before.workers) {
                    val hired = after.workers.entries.firstOrNull { (k, v) -> v > (before.workers[k] ?: 0) }?.key
                    if (hired != null) {
                        val name = content.workerById.getValue(hired).one
                        if (w.lastPurchase == hired) w.purchaseCount++ else { w.lastPurchase = hired; w.purchaseCount = 1 }
                        if (w.purchaseCount == 1 && w.ledger.size % 3 == 0) {
                            w.ledger += LedgerRecord(w.nextId++, w.runId, after.gameDay, after.chapter, "purchase", "Hired ${Engine.article(name)}", startMs + (t * 1000).toLong())
                        }
                    }
                }
                s = after
            }
            val out = engine.tick(s, 1.0)
            record(w, out, s, startMs + (t * 1000).toLong())
            s = out.state
            t += 1.0
        }
        return s
    }

    private fun record(w: Writer, out: Outcome, before: GameState, atMs: Long) {
        for (e in out.events) {
            val kind = when (e.kind) {
                EventKind.STORY, EventKind.CHOICE_OPENED -> "story"
                EventKind.CHOICE_MADE -> "choice"
                EventKind.CHAPTER_END -> "chapter_end"
                EventKind.ENDING -> "ending"
                else -> continue
            }
            w.ledger += LedgerRecord(w.nextId++, w.runId, out.state.gameDay, if (kind == "chapter_end") before.chapter else out.state.chapter, kind, e.text, atMs)
        }
    }

    private fun write(name: String, runs: List<RunRecord>, state: GameState, runId: Long, writers: List<Writer>) {
        val dir = File(System.getenv("SAVE_OUT"))
        dir.mkdirs()
        var id = 1L
        val ledger = writers.flatMap { it.ledger }.map { it.copy(id = id++) }
        val save = SaveFile(
            exportedAt = 1_789_000_000_000L,
            runs = runs,
            snapshot = SnapshotRecord(runId, state),
            ledger = ledger,
            choices = writers.flatMap { it.choices },
        )
        File(dir, name).writeText(SaveCodec.encode(save))
        println("$name: chapter ${state.chapter}, day ${state.gameDay}, progress ${(engine.goalFraction(state) * 100).toInt()}%, pending ${state.pendingChoiceId}")
    }

    @Test
    fun writeScenarios() {
        assumeTrue(System.getenv("SAVE_OUT") != null)
        val t0 = 1_788_400_000_000L

        // The psalter one tap from done, both chapter-one choices made: finishing it asks about notes.
        val wp = Writer(9)
        val psalterStart = engine.newGame(t0, false)
        record(wp, psalterStart, psalterStart.state, t0)
        val psalter = play(wp, psalterStart.state, listOf(1, 0), t0) { it.chapter == 1 && it.choices.size == 2 && engine.goalFraction(it) >= 0.985 }
        write("inkhorn-psalter-end.json", listOf(RunRecord(9, t0, null, null, 1, (psalter.gameSeconds * 1.2).toLong())), psalter, 9, listOf(wp))

        // One run into the bindery: the chained books waiting, then two-thirds of the way on.
        val w1 = Writer(1)
        val fresh = engine.newGame(t0, false)
        record(w1, fresh, fresh.state, t0)
        val picks = listOf(0, 2, 1, 0, 0, 1, 2, 0, 0, 1, 0)
        val choice = play(w1, fresh.state, picks, t0) { it.pendingChoiceId == "chains" }
        write("inkhorn-bindery-choice.json", listOf(RunRecord(1, t0, null, null, 1, (choice.gameSeconds * 1.13).toLong())), choice, 1, listOf(w1))
        val twoThirds = play(w1, choice, picks, t0 + (choice.gameSeconds * 1000).toLong()) { it.chapter == 3 && engine.goalFraction(it) >= 0.67 }
        write("inkhorn-bindery.json", listOf(RunRecord(1, t0, null, null, 1, (twoThirds.gameSeconds * 1.13).toLong())), twoThirds, 1, listOf(w1))
        val library = play(w1, twoThirds, picks, t0) { it.chapter == 4 && engine.goalFraction(it) >= 0.31 }
        write("inkhorn-library.json", listOf(RunRecord(1, t0, null, null, 1, (library.gameSeconds * 1.13).toLong())), library, 1, listOf(w1))

        // A finished first chronicle, then a second one at the press.
        val ended = play(w1, library, picks, t0) { it.finished }
        val run1 = RunRecord(1, t0, t0 + 31_400_000L, ended.endingId, 1, (ended.gameSeconds * 1.13).toLong())
        val w2 = Writer(2)
        val t1 = t0 + 40_000_000L
        val second = engine.newGame(t1, true)
        record(w2, second, second.state, t1)
        val press = play(w2, second.state, listOf(2, 2, 1, 2, 0, 2, 1, 2, 2, 0, 2), t1) { it.chapter == 5 && engine.goalFraction(it) >= 0.46 }
        w2.nextId = 100_000
        write(
            "inkhorn-press.json",
            listOf(run1, RunRecord(2, t1, null, null, 2, (press.gameSeconds * 1.09).toLong())),
            press, 2, listOf(w1, w2),
        )
        val lastPage = play(w2, press, listOf(2, 2, 1, 2, 0, 2, 1, 2, 2, 0, 2), t1) { it.pendingChoiceId == "edition" }
        write(
            "inkhorn-last-edition.json",
            listOf(run1, RunRecord(2, t1, null, null, 2, (lastPage.gameSeconds * 1.09).toLong())),
            lastPage, 2, listOf(w1, w2),
        )
        val wl = Writer(2).also { it.ledger += w2.ledger; it.choices += w2.choices }
        val nearEnd = play(wl, lastPage, listOf(2, 2, 1, 2, 0, 2, 1, 2, 2, 0, 2), t1) { it.choices.size == 11 && engine.goalFraction(it) >= 0.9995 }
        write(
            "inkhorn-near-end.json",
            listOf(run1, RunRecord(2, t1, null, null, 2, (nearEnd.gameSeconds * 1.09).toLong())),
            nearEnd, 2, listOf(w1, wl),
        )
    }
}
