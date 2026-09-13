package com.mohdshayan.inkhorn.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BalanceSimTest {
    private val engine = TestContent.engine

    private fun report(strategy: Int): GreedyBot.Run {
        val run = GreedyBot(engine, strategy, trace = System.getenv("SIM_TRACE") != null).play()
        println("strategy $strategy: " + run.chapterSeconds.joinToString { "%.1f min".format(it / 60) } +
            " total %.2f h, ending ${run.state.endingId}".format(run.totalSeconds / 3600))
        return run
    }

    @Test
    fun greedyBotFinishesInSixToTenHoursWithEveryStrategy() {
        for (strategy in 0..2) {
            val run = report(strategy)
            assertEquals("strategy $strategy finished all six chapters", 6, run.chapterSeconds.size)
            assertTrue("chapter one under 30 minutes (strategy $strategy)", run.chapterSeconds[0] <= 30 * 60)
            val hours = run.totalSeconds / 3600
            assertTrue("arc of %.2f h is inside 6 to 10 hours (strategy $strategy)".format(hours), hours in 6.0..10.0)
            for (i in 1 until run.chapterSeconds.size) {
                val a = run.chapterSeconds[i - 1]
                val b = run.chapterSeconds[i]
                assertTrue("chapter ${i + 1} is not over twice chapter $i (strategy $strategy)", b <= 2 * a && a <= 2 * b)
            }
        }
    }
}
