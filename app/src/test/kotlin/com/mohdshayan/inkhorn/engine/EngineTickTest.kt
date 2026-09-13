package com.mohdshayan.inkhorn.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineTickTest {
    private val engine = TestContent.engine
    private fun fresh() = engine.newGame(1L, newGamePlus = false).state

    @Test
    fun openingShowsOneButtonAndSellAppearsAfterFiveCopies() {
        var s = fresh()
        val visible = { st: GameState -> engine.content.actions.filter { Engine.ACTION_PREFIX + it.id in st.revealed }.map { it.id } }
        assertEquals(listOf("copy"), visible(s))
        repeat(4) {
            s = engine.act(s, "copy").state
            s = engine.tick(s, 1.0).state
        }
        assertEquals(listOf("copy"), visible(s))
        s = engine.act(s, "copy").state
        assertTrue("sell appears on the fifth copy", "sell" in visible(s))
        assertEquals(1.0, s.amount("folios"), 1e-9)
    }

    @Test
    fun cooldownBlocksASecondTapUntilItRunsOut() {
        var s = engine.act(fresh(), "copy").state
        val refused = engine.act(s, "copy")
        assertFalse(refused.ok)
        assertTrue(refused.refusal!!.startsWith("Ready in"))
        s = engine.tick(s, 0.5).state
        assertFalse(engine.act(s, "copy").ok)
        s = engine.tick(s, 0.5).state
        assertTrue(engine.act(s, "copy").ok)
    }

    @Test
    fun hireCostGrowsAndShortfallNamesTheMissingAmount() {
        val s = fresh().copy(resources = mapOf("pennies" to 20.0), revealed = setOf("worker:novice"))
        assertEquals(12.0, engine.hireCost(s, "novice").getValue("pennies"), 0.0)
        val hired = engine.hire(s, "novice").state
        assertEquals(1, hired.count("novice"))
        assertEquals(8.0, hired.amount("pennies"), 1e-9)
        // 12 * 1.2 = 14.4, rounded up to 15
        assertEquals(15.0, engine.hireCost(hired, "novice").getValue("pennies"), 0.0)
        assertEquals("Needs 7 more pennies", engine.hire(hired, "novice").refusal)
    }

    @Test
    fun workersProduceAndStallWhenAnInputRunsOut() {
        val s = fresh().copy(chapter = 3, resources = mapOf("folios" to 1000.0, "ink" to 1000.0, "vellum" to 1.0), workers = mapOf("binder" to 2))
        // two binders use 0.2 skins a second between them: five seconds of binding in one skin
        var t = s
        repeat(20) { t = engine.tick(t, 1.0).state }
        assertEquals(1.0, t.amount("books"), 1e-6)
        assertEquals(0.0, t.amount("vellum"), 1e-9)
        assertEquals(1000.0 - 16.0, t.amount("folios"), 1e-6)
        assertEquals(1.0, t.goalProgress, 1e-6)
    }

    @Test
    fun upgradeMultipliesWorkerThroughput() {
        val base = fresh().copy(resources = mapOf("vellum" to 100.0, "pennies" to 100.0), workers = mapOf("novice" to 1), revealed = setOf("upgrade:pumice"))
        val plain = engine.tick(base, 10.0).state.amount("folios")
        val bought = engine.buy(base, "pumice")
        assertTrue(bought.ok)
        val better = engine.tick(bought.state, 10.0).state.amount("folios")
        assertEquals(plain * 1.5, better, 1e-9)
        assertEquals("Already bought.", engine.buy(bought.state, "pumice").refusal)
    }

    @Test
    fun choicePausesProductionAndCommitResumesIt() {
        val goal = engine.goalAmount(fresh())
        val s = fresh().copy(resources = mapOf("vellum" to 1000.0), workers = mapOf("scrivener" to 10), goalProgress = goal * 0.4 - 0.5)
        val opened = engine.tick(s, 1.0)
        assertEquals("herbal", opened.state.pendingChoiceId)
        assertTrue(opened.events.any { it.kind == EventKind.CHOICE_OPENED })
        val paused = engine.tick(opened.state, 30.0).state
        assertEquals(opened.state.amount("folios"), paused.amount("folios"), 1e-9)
        assertFalse(engine.act(paused, "copy").ok)
        val committed = engine.commit(paused, "herbal", "burn")
        assertTrue(committed.ok)
        assertNull(committed.state.pendingChoiceId)
        assertEquals(1, committed.state.leanings["cloister"])
        assertTrue(Engine.TAB_CHRONICLE in committed.state.revealed)
        assertTrue(engine.tick(committed.state, 5.0).state.amount("folios") > paused.amount("folios"))
    }

    @Test
    fun meetingTheGoalAdvancesTheChapter() {
        val goal = engine.goalAmount(fresh())
        val s = fresh().copy(
            resources = mapOf("vellum" to 1000.0),
            workers = mapOf("scrivener" to 10),
            goalProgress = goal - 1.0,
            choices = listOf(MadeChoice("herbal", "burn", 1, 1), MadeChoice("tithe", "pay", 1, 1)),
        )
        val out = engine.tick(s, 1.0)
        assertEquals(2, out.state.chapter)
        assertEquals(0.0, out.state.goalProgress, 1e-9)
        assertNotNull(out.events.firstOrNull { it.kind == EventKind.CHAPTER_END })
        assertTrue("chapter two reveals the grind action", "action:grind" in out.state.revealed)
    }

    @Test
    fun newGamePlusCutsEarlyGoalsAndCostsToSixtyPercent() {
        val plus = engine.newGame(1L, newGamePlus = true).state
        assertEquals(engine.content.chapter(1).goalAmount * 0.6, engine.goalAmount(plus), 1e-9)
        assertEquals(8.0, engine.hireCost(plus, "novice").getValue("pennies"), 0.0)
        val ch3 = plus.copy(chapter = 3)
        assertEquals(engine.content.chapter(3).goalAmount, engine.goalAmount(ch3), 1e-9)
    }
}
