package com.mohdshayan.inkhorn.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EndingResolverTest {
    private val content = TestContent.content

    private fun pick(vararg leanings: String): List<MadeChoice> =
        content.choices.zip(leanings.toList()).map { (c, l) ->
            MadeChoice(c.id, c.options.first { it.leaning == l }.id, c.chapter, 1)
        }

    @Test
    fun allThreeEndingsAreReachable() {
        val found = Leanings.all.map { l -> EndingResolver.resolve(pick(*Array(11) { l }), content) }.toSet()
        assertEquals(setOf("open_stacks", "kings_printer", "quiet_cloister"), found)
    }

    @Test
    fun finalChoiceCountsDouble() {
        // Five Commons, five Crown, then the last edition for the Crown: 5 against 7.
        val choices = pick("commons", "crown", "commons", "crown", "commons", "crown", "commons", "crown", "commons", "crown", "crown")
        assertEquals(7, EndingResolver.tally(choices, content)["crown"])
        assertEquals("kings_printer", EndingResolver.resolve(choices, content))
    }

    @Test
    fun tieGoesToTheMostRecentChoiceAmongTheTied() {
        // Commons 4, Cloister 4, Crown 2 plus 2 for the double last edition? No: last edition Cloister makes it 6.
        // So build a tie without the last edition: ten choices only.
        val tenChoices = pick("commons", "cloister", "commons", "cloister", "commons", "cloister", "crown", "commons", "cloister", "crown")
        val tally = EndingResolver.tally(tenChoices, content)
        assertEquals(4, tally["commons"])
        assertEquals(4, tally["cloister"])
        // The latest choice that favoured a tied leaning was the ninth, for the Cloister; the tenth favoured the Crown, which is not tied.
        assertEquals("quiet_cloister", EndingResolver.resolve(tenChoices, content))
    }

    @Test
    fun everyChoiceSpansAtLeastTwoLeanings() {
        for (c in content.choices) assertTrue(c.id, c.options.map { it.leaning }.toSet().size >= 2)
    }
}
