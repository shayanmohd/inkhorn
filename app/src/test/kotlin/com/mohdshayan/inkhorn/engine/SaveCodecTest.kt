package com.mohdshayan.inkhorn.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveCodecTest {
    private val engine = TestContent.engine

    private fun sample(): SaveFile {
        val state = engine.newGame(1_700_000_000_000L, false).state.copy(
            chapter = 2, gameSeconds = 7_431.5, resources = mapOf("pennies" to 412.25, "ink" to 38.0),
            workers = mapOf("novice" to 7), upgrades = setOf("pumice"),
            choices = listOf(MadeChoice("herbal", "candle", 1, 4)), leanings = mapOf("commons" to 1),
        )
        return SaveFile(
            exportedAt = 1_700_000_100_000L,
            runs = listOf(RunRecord(3, 1_699_000_000_000L, null, null, 2, 7431)),
            snapshot = SnapshotRecord(3, state),
            ledger = listOf(LedgerRecord(10, 3, 4, 1, "choice", "You copied the herbal by candlelight.", 1_699_100_000_000L)),
            choices = listOf(ChoiceRecord(3, "herbal", "candle", 1, 4, 1_699_100_000_000L)),
        )
    }

    @Test
    fun roundTripKeepsEveryField() {
        val save = sample()
        val decoded = SaveCodec.decode(SaveCodec.encode(save))
        assertTrue(decoded is Decoded.Ok)
        assertEquals(save, (decoded as Decoded.Ok).value)
        assertEquals("inkhorn-save-day13.json", SaveCodec.fileName(save.snapshot!!.state))
    }

    @Test
    fun wrongFormatIsRejected() {
        assertEquals(Decoded.NotASave, SaveCodec.decode("{\"format\":\"beatwheel-backup\",\"formatVersion\":1,\"engineVersion\":1}"))
        assertEquals(Decoded.NotASave, SaveCodec.decode("not json at all"))
        assertEquals(Decoded.NotASave, SaveCodec.decode(SaveCodec.encode(sample()).replace("\"runs\":[{\"id\":3", "\"runs\":[{\"id\":4")))
    }

    @Test
    fun newerVersionIsRejected() {
        val newer = SaveCodec.encode(sample()).replace("\"engineVersion\":1", "\"engineVersion\":2")
        assertEquals(Decoded.Newer, SaveCodec.decode(newer))
    }

    @Test
    fun unreadableStateReturnsNullForFallback() {
        assertNull(SaveCodec.decodeState("{\"chapter\": \"three\"}"))
        assertNull(SaveCodec.decodeState(null))
        val s = sample().snapshot!!.state
        assertEquals(s, SaveCodec.decodeState(SaveCodec.encodeState(s)))
    }
}
