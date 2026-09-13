package com.mohdshayan.inkhorn.engine

/** The plain-text chronicle for the share sheet: every run, every choice, every ending found. */
object ChronicleText {

    fun leaningPhrase(leaning: String?): String = if (leaning == null) "" else "Toward ${Leanings.name(leaning)}"

    fun build(content: Content, runs: List<RunRecord>, choices: List<ChoiceRecord>): String {
        val out = StringBuilder("Inkhorn: my chronicle\n")
        val byRun = choices.groupBy { it.runId }
        for (run in runs.sortedBy { it.chronicleNumber }) {
            out.append('\n').append(NumberWords.chronicleName(run.chronicleNumber))
            val ending = run.endingId?.let { content.endingById[it]?.title }
            out.append(
                when {
                    ending != null -> ", which ended in $ending"
                    run.endedAt == null -> ", still being written"
                    else -> ", left unfinished"
                },
            )
            out.append(". ").append(Numbers.playTime(run.playSeconds)).append(" played.\n")
            for (c in byRun[run.id].orEmpty().sortedBy { it.madeAt }) {
                val choice = content.choiceById[c.choiceId] ?: continue
                val option = choice.options.firstOrNull { it.id == c.optionId } ?: continue
                out.append("Day ").append(c.gameDay).append(", ").append(choice.title).append(": ")
                    .append(option.label).append(". ").append(leaningPhrase(option.leaning)).append(".\n")
            }
        }
        val found = runs.mapNotNull { it.endingId }.toSet()
        out.append("\nEndings found: ").append(found.size).append(" of ").append(content.endings.size).append('.')
        return out.toString()
    }
}
