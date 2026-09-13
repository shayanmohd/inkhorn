package com.mohdshayan.inkhorn.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class NumberWordsTest {
    @Test
    fun words() {
        assertEquals("zero", NumberWords.words(0))
        assertEquals("twelve", NumberWords.words(12))
        assertEquals("forty-two", NumberWords.words(42))
        assertEquals("three hundred and six", NumberWords.words(306))
        assertEquals("one thousand and nine", NumberWords.words(1009))
        assertEquals("twelve thousand four hundred and twelve", NumberWords.words(12412))
        assertEquals("Chapter six", NumberWords.chapterName(6))
    }

    @Test
    fun daysAndDurations() {
        assertEquals("part of a day’s", NumberWords.days(0.6))
        assertEquals("one day’s", NumberWords.days(1.99))
        assertEquals("three days’", NumberWords.days(3.0))
        assertEquals("two hours", NumberWords.duration(7200.0))
        assertEquals("forty minutes", NumberWords.duration(2400.0))
    }

    @Test
    fun figures() {
        assertEquals("412", Numbers.whole(412.9))
        assertEquals("1,203", Numbers.whole(1203.0))
        assertEquals("12.4 million", Numbers.whole(12_412_000.0))
        assertEquals("3 a minute", Numbers.perMinute(0.05))
        assertEquals("0.6 a minute", Numbers.perMinute(0.01))
        assertEquals("falling 18 a minute", Numbers.perMinute(-0.3))
    }
}

class ChronicleTextTest {
    private val content = TestContent.content

    @Test
    fun sharesEveryRunInOrderWithChoicesAndEndings() {
        val runs = listOf(
            RunRecord(2, 2_000, null, null, 2, 1_500),
            RunRecord(1, 1_000, 1_900, "quiet_cloister", 1, 27_800),
        )
        val choices = listOf(
            ChoiceRecord(1, "tithe", "pay", 1, 9, 1_200),
            ChoiceRecord(1, "herbal", "burn", 1, 4, 1_100),
            ChoiceRecord(2, "herbal", "candle", 1, 3, 2_100),
        )
        val text = ChronicleText.build(content, runs, choices)
        val first = text.indexOf("The first chronicle, which ended in The Quiet Cloister. 7 hours 43 minutes played.")
        val second = text.indexOf("The second chronicle, still being written. 25 minutes played.")
        assertEquals(true, first in 0 until second)
        val burn = text.indexOf("Day 4, The forbidden herbal: Burn it in the abbey kitchen. Toward the Cloister.")
        val tithe = text.indexOf("Day 9, The abbot’s tithe: Give the tenth gladly. Toward the Cloister.")
        assertEquals(true, burn in first until tithe)
        assertEquals(true, text.endsWith("Endings found: 1 of 3."))
        assertEquals("under a minute", Numbers.playTime(59))
        assertEquals("1 hour 5 minutes", Numbers.playTime(3_900))
        assertEquals("The twelfth chronicle", NumberWords.chronicleName(12))
    }
}

class StoreFigureTest {
    @Test
    fun storeCountsShowTenthsOnlyBelowTen() {
        assertEquals("0.2", Numbers.store(0.2000000001))
        assertEquals("3", Numbers.store(3.0))
        assertEquals("9.9", Numbers.store(9.99))
        assertEquals("412", Numbers.store(412.7))
    }
}
