package com.mohdshayan.inkhorn.engine

import kotlinx.serialization.Serializable

/** One committed choice, in the order it was made. */
@Serializable
data class MadeChoice(
    val choiceId: String,
    val optionId: String,
    val chapter: Int,
    val gameDay: Int,
)

/**
 * The whole run, serialised into SnapshotEntity.stateJson. Immutable; the engine returns a new
 * state for every change.
 */
@Serializable
data class GameState(
    val chapter: Int = 1,
    val gameSeconds: Double = 0.0,
    val resources: Map<String, Double> = emptyMap(),
    val workers: Map<String, Int> = emptyMap(),
    val upgrades: Set<String> = emptySet(),
    val revealed: Set<String> = emptySet(),
    val cooldowns: Map<String, Double> = emptyMap(),
    val pendingChoiceId: String? = null,
    val leanings: Map<String, Int> = emptyMap(),
    val newGamePlus: Boolean = false,
    val lastWallClockMs: Long = 0L,
    /** Units of the chapter's goal resource produced since the chapter began. */
    val goalProgress: Double = 0.0,
    val choices: List<MadeChoice> = emptyList(),
    val endingId: String? = null,
    /** Taps on any action, used only by the slow reveal of the opening. */
    val taps: Int = 0,
    /** Everything ever gained this run, by resource. Reveals read these, so spending never hides a reveal. */
    val totals: Map<String, Double> = emptyMap(),
) {
    val gameDay: Int get() = (gameSeconds / SECONDS_PER_DAY).toInt() + 1
    val finished: Boolean get() = endingId != null

    fun amount(resource: String): Double = resources[resource] ?: 0.0
    fun count(worker: String): Int = workers[worker] ?: 0

    companion object {
        const val SECONDS_PER_DAY = 600.0
    }
}
