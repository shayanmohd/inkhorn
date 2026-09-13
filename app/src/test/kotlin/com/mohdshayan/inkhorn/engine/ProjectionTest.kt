package com.mohdshayan.inkhorn.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ProjectionTest {
    private val engine = TestContent.engine

    private fun nearGoal(): GameState {
        val base = engine.newGame(1L, false).state
        val goal = engine.goalAmount(base)
        return base.copy(
            resources = mapOf("vellum" to 50.0, "pennies" to 400.0),
            workers = mapOf("novice" to 4, "tanner" to 3, "scrivener" to 2),
            choices = listOf(MadeChoice("herbal", "candle", 1, 1), MadeChoice("tithe", "pay", 1, 2)),
            goalProgress = goal * 0.85,
        )
    }

    @Test
    fun projectionMatchesCatchUp() {
        val s = nearGoal()
        val seconds = Projection.secondsToGoal(engine, s)
        assertNotNull(seconds)
        val atProjection = CatchUp.run(engine, s, seconds!!)
        assertEquals(CatchUpStop.GOAL, atProjection.stop)
        assertEquals(2, atProjection.state.chapter)
        val justBefore = CatchUp.run(engine, s, seconds - 2.0)
        assertEquals(1, justBefore.state.chapter)
    }

    @Test
    fun noProjectionWhenAChoiceComesFirst() {
        val s = nearGoal().copy(choices = listOf(MadeChoice("herbal", "candle", 1, 1)))
        assertNull(Projection.secondsToGoal(engine, s))
    }

    @Test
    fun noProjectionBeyondTheCap() {
        val s = nearGoal().copy(workers = mapOf("tanner" to 1), goalProgress = 0.0)
        assertNull(Projection.secondsToGoal(engine, s))
    }
}
