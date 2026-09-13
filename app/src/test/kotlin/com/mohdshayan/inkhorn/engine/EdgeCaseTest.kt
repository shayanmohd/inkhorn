package com.mohdshayan.inkhorn.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.TimeZone

/** Zero, negative, huge, boundaries, clocks and corrupt files: the inputs the happy-path tests skip. */
class EdgeCaseTest {
    private val engine = TestContent.engine
    private val content = TestContent.content

    private fun sample(state: GameState = engine.newGame(1_700_000_000_000L, false).state.copy(chapter = 2)): SaveFile = SaveFile(
        exportedAt = 1_700_000_100_000L,
        runs = listOf(RunRecord(3, 1_699_000_000_000L, null, null, 1, 600)),
        snapshot = SnapshotRecord(3, state),
        ledger = listOf(
            LedgerRecord(10, 3, 1, 1, "story", "The first page is crooked.", 1_699_100_000_000L),
            LedgerRecord(11, 3, 2, 1, "action", "Copied a line", 1_699_100_001_000L),
        ),
        choices = listOf(ChoiceRecord(3, "herbal", "candle", 1, 1, 1_699_100_000_500L)),
    )

    // region corrupt save files

    @Test
    fun aCleanSaveIsLoadable() {
        val save = sample()
        assertTrue(SaveCheck.loadable(content, save))
        assertTrue(SaveCodec.decode(SaveCodec.encode(save), content) is Decoded.Ok)
    }

    @Test
    fun emptyAndNonObjectInputIsNotASave() {
        for (text in listOf("", "   ", "null", "[]", "42", "\"inkhorn-save\"", "{", "{}", "{\"format\":\"inkhorn-save\"}")) {
            assertEquals("input <$text>", Decoded.NotASave, SaveCodec.decode(text, content))
        }
        assertEquals("NaN is not JSON", Decoded.NotASave, SaveCodec.decode(SaveCodec.encode(sample()).replace("\"gameSeconds\":0.0", "\"gameSeconds\":NaN"), content))
        val hugeVersion = SaveCodec.encode(sample()).replace("\"formatVersion\":1", "\"formatVersion\":99999999999999999999")
        assertEquals(Decoded.NotASave, SaveCodec.decode(hugeVersion, content))
        val negativeVersion = SaveCodec.encode(sample()).replace("\"engineVersion\":1", "\"engineVersion\":-4")
        assertTrue("an older engine version still loads", SaveCodec.decode(negativeVersion, content) is Decoded.Ok)
    }

    @Test
    fun aSnapshotThisContentCannotRunIsRefused() {
        val base = sample().snapshot!!.state
        val broken = mapOf(
            "chapter 99" to base.copy(chapter = 99),
            "chapter 0" to base.copy(chapter = 0),
            "unknown pending choice" to base.copy(pendingChoiceId = "nope"),
            "pending choice from another chapter" to base.copy(chapter = 1, pendingChoiceId = "edition"),
            "unknown ending" to base.copy(endingId = "nowhere"),
            "negative store" to base.copy(resources = mapOf("pennies" to -5.0)),
            "NaN store" to base.copy(resources = mapOf("pennies" to Double.NaN)),
            "infinite total" to base.copy(totals = mapOf("folios" to Double.POSITIVE_INFINITY)),
            "negative hands" to base.copy(workers = mapOf("novice" to -3)),
            "negative time" to base.copy(gameSeconds = -1.0),
            "absurd time" to base.copy(gameSeconds = 1e300),
            "NaN progress" to base.copy(goalProgress = Double.NaN),
            "unknown choice made" to base.copy(choices = listOf(MadeChoice("ghost", "x", 1, 1))),
            "unknown option" to base.copy(choices = listOf(MadeChoice("herbal", "x", 1, 1))),
            "choice made twice" to base.copy(choices = listOf(MadeChoice("herbal", "burn", 1, 1), MadeChoice("herbal", "candle", 1, 2))),
        )
        for ((why, state) in broken) {
            assertFalse(why, SaveCheck.playable(content, state))
            // JSON cannot carry NaN or infinity at all, so those never reach a file.
            val finite = state.resources.values.all { it.isFinite() } && state.totals.values.all { it.isFinite() } && state.goalProgress.isFinite()
            if (finite) assertEquals(why, Decoded.NotASave, SaveCodec.decode(SaveCodec.encode(sample(state)), content))
        }
    }

    @Test
    fun rowsThatWouldCollideInTheDatabaseAreRefused() {
        val save = sample()
        assertFalse("duplicate ledger id", SaveCheck.loadable(content, save.copy(ledger = save.ledger + save.ledger.first())))
        assertFalse("duplicate run id", SaveCheck.loadable(content, save.copy(runs = save.runs + save.runs.first())))
        assertFalse("duplicate choice", SaveCheck.loadable(content, save.copy(choices = save.choices + save.choices.first().copy(optionId = "burn"))))
        assertFalse("ledger rows for a run the file does not hold", SaveCheck.loadable(content, save.copy(ledger = save.ledger.map { it.copy(runId = 9) })))
        assertFalse("choices for a run the file does not hold", SaveCheck.loadable(content, save.copy(runs = emptyList(), snapshot = null, ledger = emptyList())))
        assertFalse("runs without a snapshot", SaveCheck.loadable(content, save.copy(snapshot = null)))
        assertTrue("an empty file starts a new game", SaveCheck.loadable(content, save.copy(runs = emptyList(), snapshot = null, ledger = emptyList(), choices = emptyList())))
    }

    // endregion

    // region clocks

    @Test
    fun awayTimeIsWallClockDurationAcrossDaylightSavingAndTimeZones() {
        // 01:30 to 03:30 on the night New York springs forward is one real hour, not two.
        val ny = ZoneId.of("America/New_York")
        val before = ZonedDateTime.of(2026, 3, 8, 1, 30, 0, 0, ny).toInstant().toEpochMilli()
        val after = ZonedDateTime.of(2026, 3, 8, 3, 30, 0, 0, ny).toInstant().toEpochMilli()
        assertEquals(3600.0, CatchUp.elapsedSeconds(before, after), 0.0)
        // London falls back: 01:30 BST to 01:30 GMT is one real hour, with the same wall time.
        val london = ZoneId.of("Europe/London")
        val bst = ZonedDateTime.ofLocal(java.time.LocalDateTime.of(2026, 10, 25, 1, 30), london, java.time.ZoneOffset.ofHours(1))
        val gmt = bst.withLaterOffsetAtOverlap()
        assertEquals(3600.0, CatchUp.elapsedSeconds(bst.toInstant().toEpochMilli(), gmt.toInstant().toEpochMilli()), 0.0)

        // Moving the phone to another time zone changes nothing: the engine never reads one.
        val state = engine.newGame(before, false).state.copy(workers = mapOf("novice" to 2))
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"))
            val a = CatchUp.run(engine, state, CatchUp.elapsedSeconds(before, after))
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Auckland"))
            val b = CatchUp.run(engine, state, CatchUp.elapsedSeconds(before, after))
            assertEquals(a.state, b.state)
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test
    fun zeroMissingAndHugeClocks() {
        assertEquals("never saved", 0.0, CatchUp.elapsedSeconds(0L, 1_700_000_000_000L), 0.0)
        assertEquals("same instant", 0.0, CatchUp.elapsedSeconds(5_000L, 5_000L), 0.0)
        assertEquals("negative stamp", 0.0, CatchUp.elapsedSeconds(-10L, 5_000L), 0.0)
        val huge = CatchUp.elapsedSeconds(1L, Long.MAX_VALUE)
        assertTrue(huge.isFinite() && huge > 0)
        val s = engine.newGame(1L, false).state.copy(workers = mapOf("tanner" to 1))
        val r = CatchUp.run(engine, s, huge)
        assertEquals(CatchUpStop.CAP, r.stop)
        assertEquals(CatchUp.capSeconds(1), r.replayedSeconds, 1e-6)
        val zero = CatchUp.run(engine, s, 0.0)
        assertEquals(CatchUpStop.ELAPSED, zero.stop)
        assertEquals(s, zero.state)
    }

    @Test
    fun capBoundariesByChapter() {
        assertEquals(2 * 3600.0, CatchUp.capSeconds(1), 0.0)
        assertEquals(4 * 3600.0, CatchUp.capSeconds(2), 0.0)
        assertEquals(8 * 3600.0, CatchUp.capSeconds(3), 0.0)
        assertEquals(8 * 3600.0, CatchUp.capSeconds(6), 0.0)
        val s = engine.newGame(1L, false).state.copy(workers = mapOf("tanner" to 1))
        assertEquals("exactly the cap is not over it", CatchUpStop.ELAPSED, CatchUp.run(engine, s, 2 * 3600.0).stop)
        assertEquals(CatchUpStop.CAP, CatchUp.run(engine, s, 2 * 3600.0 + 0.5).stop)
    }

    @Test
    fun awaySheetBoundaryAndNothingToReport() {
        val s = engine.newGame(1L, false).state.copy(workers = mapOf("tanner" to 4))
        assertNull("exactly five minutes writes nothing", AwaySummary.build(content, s, CatchUp.run(engine, s, 300.0)))
        val entry = AwaySummary.build(content, s, CatchUp.run(engine, s, 301.0))
        assertTrue(entry!!.entry, entry.entry.startsWith("While you were away, part of a day’s copying"))
        // A choice was already waiting when the player left: nothing was replayed, so no away sheet covers it.
        val waiting = s.copy(pendingChoiceId = "herbal")
        assertNull(AwaySummary.build(content, waiting, CatchUp.run(engine, waiting, 3 * 3600.0)))
        val empty = engine.newGame(1L, false).state
        val idle = AwaySummary.build(content, empty, CatchUp.run(engine, empty, 3600.0))
        assertTrue(idle!!.entry, idle.entry.contains("and nothing came of it"))
        assertTrue(idle.changes.isEmpty())
    }

    // endregion

    // region engine refusals

    @Test
    fun theEngineRefusesNonsenseWithoutChangingState() {
        val s = engine.newGame(1L, false).state
        for (out in listOf(engine.act(s, "fly"), engine.hire(s, "dragon"), engine.buy(s, "moon"), engine.commit(s, "herbal", "candle"))) {
            assertFalse(out.ok)
            assertEquals(s, out.state)
        }
        assertEquals("negative time does nothing", s, engine.tick(s, -5.0).state)
        assertEquals("zero time does nothing", s, engine.tick(s, 0.0).state)
        val broke = s.copy(resources = emptyMap(), revealed = s.revealed + "worker:novice")
        assertEquals("Needs 12 more pennies", engine.hire(broke, "novice").refusal)
        val finished = s.copy(endingId = "open_stacks")
        assertEquals("The chronicle is finished.", engine.act(finished, "copy").refusal)
        assertEquals(finished, engine.tick(finished, 60.0).state)
    }

    @Test
    fun aVeryRichWorkshopStaysFinite() {
        val rich = engine.newGame(1L, false).state.copy(
            chapter = 6,
            resources = content.resources.associate { it.id to 1e15 },
            workers = content.workers.associate { it.id to 5_000 },
            upgrades = content.upgrades.map { it.id }.toSet(),
        )
        val r = CatchUp.run(engine, rich, 3600.0)
        assertTrue(r.state.resources.values.all { it.isFinite() && it >= 0.0 })
        assertTrue(SaveCheck.playable(content, r.state))
        assertEquals(r.state, SaveCodec.decodeState(SaveCodec.encodeState(r.state)))
    }

    @Test
    fun newGamePlusTouchesOnlyTheFirstTwoChapters() {
        val plus = engine.newGame(1L, newGamePlus = true).state
        assertEquals(content.chapter(1).goalAmount * 0.6, engine.goalAmount(plus), 1e-9)
        assertEquals(content.chapter(2).goalAmount * 0.6, engine.goalAmount(plus.copy(chapter = 2)), 1e-9)
        assertEquals(content.chapter(3).goalAmount, engine.goalAmount(plus.copy(chapter = 3)), 1e-9)
        assertEquals(1.0, engine.goalFraction(plus.copy(goalProgress = 1e12)), 0.0)
        assertEquals(0.0, engine.goalFraction(plus.copy(goalProgress = 0.0)), 0.0)
    }

    // endregion

    // region figures and words

    @Test
    fun wordsAtTheEdges() {
        assertEquals("minus five", NumberWords.words(-5))
        assertEquals("nineteen", NumberWords.words(19))
        assertEquals("twenty", NumberWords.words(20))
        assertEquals("one hundred", NumberWords.words(100))
        assertEquals("nine hundred and ninety-nine thousand nine hundred and ninety-nine", NumberWords.words(999_999))
        assertEquals("1,000,000", NumberWords.words(1_000_000))
        assertEquals("-2147.5 million", NumberWords.words(Int.MIN_VALUE))
        assertEquals("part of a day’s", NumberWords.days(0.0))
        assertEquals("part of a day’s", NumberWords.days(-3.0))
        assertEquals("zero minutes", NumberWords.duration(0.0))
        assertEquals("one minute", NumberWords.duration(60.0))
        assertEquals("fifty-nine minutes", NumberWords.duration(59 * 60.0))
        assertEquals("one hour", NumberWords.duration(3600.0))
        assertEquals("Chronicle 13", NumberWords.chronicleName(13))
        assertEquals("Chronicle 0", NumberWords.chronicleName(0))
        assertEquals("a second chronicle", NumberWords.nextChronicle(0))
        assertEquals("a second chronicle", NumberWords.nextChronicle(1))
        assertEquals("the third chronicle", NumberWords.nextChronicle(2))
        assertEquals("the twelfth chronicle", NumberWords.nextChronicle(11))
        assertEquals("chronicle 13", NumberWords.nextChronicle(12))
    }

    @Test
    fun figuresAtTheEdges() {
        assertEquals("0", Numbers.whole(0.0))
        assertEquals("999", Numbers.whole(999.999))
        assertEquals("-1,235", Numbers.whole(-1234.5))
        assertEquals("9,999,999", Numbers.whole(9_999_999.0))
        assertEquals("10 million", Numbers.whole(10_000_000.0))
        assertEquals("0", Numbers.store(0.0))
        assertEquals("0", Numbers.store(0.04))
        assertEquals("9.9", Numbers.store(9.99999))
        assertEquals("10", Numbers.store(10.0))
        assertEquals("-0.3", Numbers.oneDecimal(-0.3))
        assertEquals("-1.5", Numbers.oneDecimal(-1.5))
        assertEquals("0 a minute", Numbers.perMinute(0.0))
        assertEquals("under a minute", Numbers.playTime(0))
        assertEquals("under a minute", Numbers.playTime(-40))
        assertEquals("1 hour", Numbers.playTime(3600))
        assertEquals("1 second", Numbers.seconds(1.0))
        assertEquals("0.4 seconds", Numbers.seconds(0.4))
        assertEquals("", emptyList<String>().joinNaturally())
        assertEquals("ink", listOf("ink").joinNaturally())
        assertEquals("folios and ink", listOf("folios", "ink").joinNaturally())
    }

    @Test
    fun nounsAgreeWithTheFigureAsWritten() {
        assertEquals("folio", engine.nounFor("folios", Numbers.store(1.03)))
        assertEquals("folios", engine.nounFor("folios", Numbers.store(1.2)))
        assertEquals("folios", engine.nounFor("folios", Numbers.store(0.0)))
        assertEquals("penny", engine.nounFor("pennies", "1"))
        assertEquals("unknown", engine.nounFor("unknown", "3"))
    }

    // endregion
}
