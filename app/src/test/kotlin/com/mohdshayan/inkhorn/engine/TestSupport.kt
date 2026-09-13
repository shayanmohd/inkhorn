package com.mohdshayan.inkhorn.engine

import java.io.File

object TestContent {
    val content: Content by lazy {
        val candidates = listOf(File("src/main/assets/story.json"), File("app/src/main/assets/story.json"))
        Content.parse(candidates.first { it.exists() }.readText())
    }
    val engine: Engine by lazy { Engine(content) }
}

/**
 * A greedy player who looks at the game every [every] simulated seconds: answers any open choice
 * with option [strategy] (clamped), taps every ready action, then buys whatever is affordable until
 * nothing is: first a hand that feeds a resource about to run dry, otherwise the cheapest thing.
 */
class GreedyBot(private val engine: Engine, private val strategy: Int, private val every: Double = 10.0, private val trace: Boolean = false) {
    private val content = engine.content

    data class Run(val chapterSeconds: List<Double>, val totalSeconds: Double, val state: GameState)

    fun play(limitSeconds: Double = 20 * 3600.0): Run {
        var state = engine.newGame(1L, newGamePlus = false).state
        val durations = ArrayList<Double>()
        var chapterStart = 0.0
        var t = 0.0
        var sinceAct = every
        while (t < limitSeconds && !state.finished) {
            if (sinceAct >= every) {
                state = act(state)
                sinceAct = 0.0
            }
            val before = state.chapter
            state = engine.tick(state, 1.0).state
            t += 1.0
            sinceAct += 1.0
            if (trace && t % 1200.0 == 0.0) {
                val res = state.resources.entries.joinToString(" ") { "${it.key}=${Numbers.whole(it.value)}" }
                val w = state.workers.entries.joinToString(" ") { "${it.key}=${it.value}" }
                println("t=${(t / 60).toInt()}m ch${state.chapter} ${Numbers.whole(state.goalProgress)}/${Numbers.whole(engine.goalAmount(state))} up=${state.upgrades.size} | $res | $w")
            }
            if (state.chapter != before || state.finished) {
                durations += t - chapterStart
                chapterStart = t
            }
        }
        return Run(durations, t, state)
    }

    fun act(start: GameState): GameState {
        var s = start
        s.pendingChoiceId?.let { id ->
            val options = content.choiceById.getValue(id).options
            s = engine.commit(s, id, options[strategy.coerceIn(0, options.lastIndex)].id).state
        }
        for (a in content.actions) {
            if (Engine.ACTION_PREFIX + a.id !in s.revealed) continue
            val buysInput = (a.costs[Engine.PENNIES] ?: 0.0) > 0.0 && a.gains.keys.all { s.amount(it) > 20.0 }
            if (buysInput) continue
            val next = engine.act(s, a.id)
            if (next.ok) s = next.state
        }
        repeat(200) {
            if (s.pendingChoiceId != null) return s
            val rates = engine.netRates(s)
            val short = rates.filter { (r, v) -> v < 0 && s.amount(r) < -60 * v }.keys
            val feeders = content.workers.filter { w -> w.produces.keys.any { it in short } }.map { it.id }.toSet()
            data class Option(val feeder: Boolean, val pennies: Double, val buy: () -> Outcome)
            val options = ArrayList<Option>()
            for (u in content.upgrades) {
                if (u.id in s.upgrades || Engine.UPGRADE_PREFIX + u.id !in s.revealed) continue
                val cost = engine.upgradeCost(s, u.id)
                if (engine.affordable(s, cost)) { val cur = s; options += Option(false, cost[Engine.PENNIES] ?: 0.0) { engine.buy(cur, u.id) } }
            }
            for (w in content.workers) {
                if (Engine.WORKER_PREFIX + w.id !in s.revealed) continue
                val cost = engine.hireCost(s, w.id)
                if (engine.affordable(s, cost)) { val cur = s; options += Option(w.id in feeders, cost[Engine.PENNIES] ?: 0.0) { engine.hire(cur, w.id) } }
            }
            if (options.isEmpty()) return s
            val next = options.minWith(compareBy<Option>({ !it.feeder }, { it.pennies })).buy()
            if (!next.ok) return s
            s = next.state
        }
        return s
    }
}
