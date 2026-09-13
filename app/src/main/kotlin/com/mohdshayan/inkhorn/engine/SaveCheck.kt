package com.mohdshayan.inkhorn.engine

/**
 * A save can be well-formed JSON and still describe a game this content cannot run: chapter 99, a
 * choice that does not exist, a negative store. Loading one would crash the engine on every launch,
 * so a state or a save file must pass these checks before it is played.
 */
object SaveCheck {

    fun playable(content: Content, s: GameState): Boolean {
        if (content.chapters.none { it.number == s.chapter }) return false
        if (!s.gameSeconds.isFinite() || s.gameSeconds < 0.0 || s.gameSeconds > MAX_GAME_SECONDS) return false
        if (!s.goalProgress.isFinite() || s.goalProgress < 0.0) return false
        if (s.resources.values.any { !it.isFinite() || it < 0.0 }) return false
        if (s.totals.values.any { !it.isFinite() || it < 0.0 }) return false
        if (s.cooldowns.values.any { !it.isFinite() }) return false
        if (s.workers.values.any { it < 0 }) return false
        if (s.taps < 0) return false
        s.pendingChoiceId?.let { id ->
            val choice = content.choiceById[id] ?: return false
            if (choice.chapter != s.chapter) return false
        }
        s.endingId?.let { if (it !in content.endingById) return false }
        for (c in s.choices) {
            val choice = content.choiceById[c.choiceId] ?: return false
            if (choice.options.none { it.id == c.optionId }) return false
        }
        if (s.choices.map { it.choiceId }.toSet().size != s.choices.size) return false
        return true
    }

    /** The whole file: a playable snapshot, and no two rows sharing a key the database needs unique. */
    fun loadable(content: Content, save: SaveFile): Boolean {
        if (save.runs.map { it.id }.toSet().size != save.runs.size) return false
        if (save.ledger.map { it.id }.toSet().size != save.ledger.size) return false
        if (save.choices.map { it.runId to it.choiceId }.toSet().size != save.choices.size) return false
        if (save.runs.isNotEmpty() && save.snapshot == null) return false
        val runIds = save.runs.map { it.id }.toSet()
        if (save.ledger.any { it.runId !in runIds } || save.choices.any { it.runId !in runIds }) return false
        val snap = save.snapshot ?: return true
        if (save.runs.none { it.id == snap.runId }) return false
        return playable(content, snap.state)
    }

    /** A thousand years of game time; anything beyond is a corrupt number, not a long game. */
    private const val MAX_GAME_SECONDS = 1000.0 * 365 * 24 * 3600
}
