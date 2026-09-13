package com.mohdshayan.inkhorn.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class CatchUpTest {
    private val engine = TestContent.engine

    /** A believable chapter-three workshop, with both chapter-three choices still ahead. */
    private fun midRun(): GameState = engine.newGame(1L, false).state.copy(
        chapter = 3,
        resources = mapOf("vellum" to 400.0, "folios" to 900.0, "pennies" to 30000.0, "ink" to 1500.0, "books" to 4.0),
        workers = mapOf("novice" to 14, "tanner" to 9, "pedlar" to 6, "scrivener" to 11, "gatherer" to 5, "miller" to 6, "binder" to 3, "bookseller" to 2),
        upgrades = setOf("goose_quills", "pumice", "palimpsest", "pricking", "window"),
        choices = listOf(MadeChoice("herbal", "burn", 1, 2), MadeChoice("tithe", "refuse", 1, 3), MadeChoice("river", "charter", 2, 5), MadeChoice("galls", "licence", 2, 7)),
        goalProgress = 0.0,
    )

    private fun within(expected: Double, actual: Double, fraction: Double) =
        abs(expected - actual) <= fraction * maxOf(abs(expected), 1.0)

    @Test
    fun coarseStepsMatchFineStepsWithinOnePercent() {
        val s = midRun()
        val fine = CatchUp.run(engine, s, 1800.0, step = 0.1)
        val coarse = CatchUp.run(engine, s, 1800.0, step = CatchUp.COARSE_STEP)
        assertEquals(fine.stop, coarse.stop)
        assertTrue("goal progress ${fine.state.goalProgress} vs ${coarse.state.goalProgress}", within(fine.state.goalProgress, coarse.state.goalProgress, 0.01))
        for (r in listOf("folios", "pennies", "ink", "books")) {
            assertTrue("$r ${fine.produced[r]} vs ${coarse.produced[r]}", within(fine.produced[r] ?: 0.0, coarse.produced[r] ?: 0.0, 0.01))
        }
    }

    @Test
    fun capIsHonouredPerChapter() {
        // A tanner makes vellum but never advances the psalter, so only the cap can stop the replay.
        val s = midRun().copy(chapter = 1, workers = mapOf("tanner" to 3), choices = emptyList())
        val r = CatchUp.run(engine, s, 10 * 3600.0)
        assertEquals(CatchUpStop.CAP, r.stop)
        assertEquals(2 * 3600.0, r.replayedSeconds, 1e-6)
        assertEquals(8 * 3600.0, CatchUp.capSeconds(3), 0.0)
    }

    @Test
    fun stopsAtAnUnansweredChoice() {
        val r = CatchUp.run(engine, midRun(), 8 * 3600.0)
        assertEquals(CatchUpStop.CHOICE, r.stop)
        assertEquals("chains", r.state.pendingChoiceId)
        assertTrue(r.replayedSeconds < 8 * 3600.0)
        val again = CatchUp.run(engine, r.state, 3600.0)
        assertEquals(0.0, again.replayedSeconds, 0.0)
        assertEquals(r.state.amount("pennies"), again.state.amount("pennies"), 0.0)
    }

    @Test
    fun stopsAtTheGoalAndDoesNotRunIntoTheNextChapter() {
        val goal = engine.goalAmount(midRun())
        val s = midRun().copy(
            goalProgress = goal * 0.95,
            choices = midRun().choices + listOf(MadeChoice("chains", "leave", 3, 9), MadeChoice("seal", "guild", 3, 10)),
        )
        val r = CatchUp.run(engine, s, 8 * 3600.0)
        assertEquals(CatchUpStop.GOAL, r.stop)
        assertEquals(4, r.state.chapter)
        assertEquals(0.0, r.state.goalProgress, 1e-9)
    }

    @Test
    fun backwardsClockCountsAsZero() {
        assertEquals(0.0, CatchUp.elapsedSeconds(lastWallClockMs = 10_000L, nowMs = 4_000L), 0.0)
        assertEquals(6.0, CatchUp.elapsedSeconds(lastWallClockMs = 4_000L, nowMs = 10_000L), 0.0)
        val r = CatchUp.run(engine, midRun(), -500.0)
        assertEquals(0.0, r.replayedSeconds, 0.0)
    }

    @Test
    fun awayEntryNamesDaysAndLargestChanges() {
        val s = midRun()
        val r = CatchUp.run(engine, s, 1800.0)
        val entry = AwaySummary.build(engine.content, s, r)
        assertNotNull(entry)
        assertTrue(entry!!.entry, entry.entry.startsWith("While you were away, three days’ binding: "))
        assertTrue(entry.entry, entry.entry.contains("books bound"))
        assertEquals(3, entry.changes.size)
        assertEquals("the chapter goal leads", "books", entry.changes[0].resource)
        assertTrue(entry.changes[1].amount >= entry.changes[2].amount)
        assertNull("under five minutes writes nothing", AwaySummary.build(engine.content, s, CatchUp.run(engine, s, 240.0)))
    }
}
