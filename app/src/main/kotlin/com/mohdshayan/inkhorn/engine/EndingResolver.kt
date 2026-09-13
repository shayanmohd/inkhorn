package com.mohdshayan.inkhorn.engine

/**
 * Picks the ending from the committed choices. Each option adds its choice's weight to one
 * leaning (the last edition counts double). The highest leaning wins; a tie goes to the tied
 * leaning favoured by the most recent choice that favoured any of the tied leanings.
 */
object EndingResolver {

    fun tally(choices: List<MadeChoice>, content: Content): Map<String, Int> {
        val out = LinkedHashMap<String, Int>()
        Leanings.all.forEach { out[it] = 0 }
        for (c in choices) {
            val leaning = leaningOf(c, content) ?: continue
            val weight = content.choiceById[c.choiceId]?.weight ?: 1
            out[leaning] = (out[leaning] ?: 0) + weight
        }
        return out
    }

    fun resolve(choices: List<MadeChoice>, content: Content): String {
        val tally = tally(choices, content)
        val best = tally.values.maxOrNull() ?: 0
        val tied = tally.filterValues { it == best }.keys
        val leaning = if (tied.size == 1) {
            tied.first()
        } else {
            choices.asReversed().firstNotNullOfOrNull { c -> leaningOf(c, content)?.takeIf { it in tied } }
                ?: Leanings.all.first { it in tied }
        }
        return content.endings.first { it.leaning == leaning }.id
    }

    fun leaningOf(choice: MadeChoice, content: Content): String? =
        content.choiceById[choice.choiceId]?.options?.firstOrNull { it.id == choice.optionId }?.leaning
}
