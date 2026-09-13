package com.mohdshayan.inkhorn.engine

data class AwayChange(val resource: String, val amount: Double, val text: String)

data class AwayEntry(
    val entry: String,
    /** Up to three changes: the chapter goal's resource first, then the largest of the rest. */
    val changes: List<AwayChange>,
    val replayedSeconds: Double,
)

/** Writes the one ledger entry for a catch-up: "While you were away, three days' copying: ...". */
object AwaySummary {
    /** The away sheet opens only after more than five minutes. */
    const val MIN_SECONDS = 300.0

    fun build(content: Content, before: GameState, result: CatchUpResult): AwayEntry? {
        if (result.requestedSeconds <= MIN_SECONDS) return null
        // Nothing was replayed (a choice was already waiting, or the story had ended): nothing to write.
        if (result.replayedSeconds <= 0.0) return null
        val changes = result.produced
            .filter { it.value >= 1.0 && content.resourceById.containsKey(it.key) }
            .entries
            // The chapter's own goal leads, then the largest of the rest.
            .sortedWith(compareByDescending<Map.Entry<String, Double>> { it.key == content.chapter(before.chapter).goalResource }.thenByDescending { it.value })
            .take(3)
            .map { (r, v) ->
                val def = content.resourceById.getValue(r)
                val noun = if (v < 2.0) def.one else def.many
                AwayChange(r, v, "${Numbers.whole(v)} $noun ${def.gained}")
            }
        val work = content.chapter(before.chapter).work
        val days = NumberWords.days(result.replayedSeconds / GameState.SECONDS_PER_DAY)
        val head = "While you were away, $days $work"
        val body = if (changes.isEmpty()) {
            "$head, and nothing came of it."
        } else {
            "$head: ${changes.joinToString(", ") { it.text }}."
        }
        val tail = when (result.stop) {
            CatchUpStop.CAP -> " The workshop stopped after ${NumberWords.duration(result.replayedSeconds)} without you."
            CatchUpStop.CHOICE -> " A choice waits in the ledger."
            CatchUpStop.GOAL -> " ${content.chapter(before.chapter).title} is finished."
            else -> ""
        }
        return AwayEntry(body + tail, changes, result.replayedSeconds)
    }
}
