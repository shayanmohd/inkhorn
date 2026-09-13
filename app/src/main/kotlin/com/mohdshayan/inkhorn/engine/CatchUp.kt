package com.mohdshayan.inkhorn.engine

import kotlin.math.min

enum class CatchUpStop { ELAPSED, CAP, GOAL, CHOICE, FINISHED }

data class CatchUpResult(
    val state: GameState,
    val events: List<EngineEvent>,
    /** Wall seconds the player was away, never negative. */
    val requestedSeconds: Double,
    /** Seconds actually replayed: min(requested, cap), shorter when a goal or a choice stopped it. */
    val replayedSeconds: Double,
    val stop: CatchUpStop,
    /** Gross amounts produced during the replay, by resource. */
    val produced: Map<String, Double>,
)

/**
 * Replays time away in coarse steps through the same [Engine.step] the foreground uses. Stops at a
 * chapter goal or an unanswered choice, and never replays more than the chapter's cap.
 */
object CatchUp {
    const val COARSE_STEP = 1.0

    fun capSeconds(chapter: Int): Double = when (chapter) {
        1 -> 2 * 3600.0
        2 -> 4 * 3600.0
        else -> 8 * 3600.0
    }

    /** Wall time since the last save; a clock that went backwards counts as zero. */
    fun elapsedSeconds(lastWallClockMs: Long, nowMs: Long): Double =
        if (lastWallClockMs <= 0L || nowMs <= lastWallClockMs) 0.0 else (nowMs - lastWallClockMs) / 1000.0

    fun run(engine: Engine, state: GameState, elapsedSeconds: Double, step: Double = COARSE_STEP): CatchUpResult {
        val requested = elapsedSeconds.coerceAtLeast(0.0)
        if (state.finished) return CatchUpResult(state, emptyList(), requested, 0.0, CatchUpStop.FINISHED, emptyMap())
        if (state.pendingChoiceId != null) return CatchUpResult(state, emptyList(), requested, 0.0, CatchUpStop.CHOICE, emptyMap())
        val cap = capSeconds(state.chapter)
        val budget = min(requested, cap)
        val sim = engine.openSim(state)
        val mods = engine.modsFor(sim)
        val startChapter = state.chapter
        var done = 0.0
        var stop = if (requested > cap) CatchUpStop.CAP else CatchUpStop.ELAPSED
        while (done < budget - 1e-9) {
            val dt = min(step, budget - done)
            engine.step(sim, dt, mods)
            done += dt
            if (sim.pending != null) { stop = CatchUpStop.CHOICE; break }
            if (sim.chapter != startChapter || sim.ending != null) { stop = CatchUpStop.GOAL; break }
        }
        return CatchUpResult(sim.freeze(), sim.events.toList(), requested, done, stop, HashMap(sim.produced))
    }
}

/** When, if the game stays closed, the current chapter's goal would be met inside the away cap. */
object Projection {
    /** Seconds until the chapter finishes, or null if a choice or the cap comes first. */
    fun secondsToGoal(engine: Engine, state: GameState, step: Double = CatchUp.COARSE_STEP): Double? {
        val result = CatchUp.run(engine, state, CatchUp.capSeconds(state.chapter), step)
        return if (result.stop == CatchUpStop.GOAL) result.replayedSeconds else null
    }
}
