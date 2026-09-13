package com.mohdshayan.inkhorn.engine

import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.pow

enum class EventKind { STORY, ACTION, PURCHASE, CHOICE_OPENED, CHOICE_MADE, CHAPTER_END, ENDING }

data class EngineEvent(val kind: EventKind, val text: String, val ref: String? = null)

/** A new state plus what happened, or the unchanged state and the reason nothing happened. */
data class Outcome(
    val state: GameState,
    val events: List<EngineEvent> = emptyList(),
    val refusal: String? = null,
) {
    val ok: Boolean get() = refusal == null
}

/**
 * The deterministic workshop. Every foreground tick, every catch-up step and every projection runs
 * through [step], so the three can never disagree.
 */
class Engine(val content: Content) {

    // region public API

    fun newGame(nowMs: Long, newGamePlus: Boolean): Outcome {
        val sim = Sim(GameState(resources = content.start, newGamePlus = newGamePlus, lastWallClockMs = nowMs))
        if (newGamePlus) sim.revealed += TAB_CHRONICLE
        sim.events += EngineEvent(EventKind.STORY, content.chapter(1).opening, "chapter:1")
        checks(sim)
        return Outcome(sim.freeze(), sim.events.toList())
    }

    fun tick(state: GameState, dt: Double): Outcome {
        if (state.finished || dt <= 0.0) return Outcome(state)
        val sim = Sim(state)
        step(sim, dt, mods(sim))
        return Outcome(sim.freeze(), sim.events.toList())
    }

    fun act(state: GameState, actionId: String): Outcome {
        val action = content.actionById[actionId] ?: return Outcome(state, refusal = "That action does not exist.")
        refuseIfBlocked(state)?.let { return Outcome(state, refusal = it) }
        if (ACTION_PREFIX + actionId !in state.revealed) return Outcome(state, refusal = "Not yet.")
        val left = state.cooldowns[actionId] ?: 0.0
        if (left > 0.0) return Outcome(state, refusal = "Ready in ${Numbers.seconds(left)}.")
        val costs = actionCosts(state, actionId)
        shortfall(state, costs)?.let { return Outcome(state, refusal = it) }
        val sim = Sim(state)
        val mods = mods(sim)
        pay(sim, costs)
        for ((r, v) in actionGains(state, actionId)) gain(sim, r, v)
        sim.cooldowns[actionId] = action.cooldown * mods.f(COOLDOWN, ACTION_PREFIX + actionId)
        sim.taps += 1
        sim.events += EngineEvent(EventKind.ACTION, action.doneLine, ACTION_PREFIX + actionId)
        checks(sim)
        return Outcome(sim.freeze(), sim.events.toList())
    }

    fun hire(state: GameState, workerId: String): Outcome {
        val worker = content.workerById[workerId] ?: return Outcome(state, refusal = "No such hand.")
        refuseIfBlocked(state)?.let { return Outcome(state, refusal = it) }
        if (WORKER_PREFIX + workerId !in state.revealed) return Outcome(state, refusal = "Not yet.")
        val cost = hireCost(state, workerId)
        shortfall(state, cost)?.let { return Outcome(state, refusal = it) }
        val sim = Sim(state)
        pay(sim, cost)
        sim.workers[workerId] = (sim.workers[workerId] ?: 0) + 1
        sim.events += EngineEvent(EventKind.PURCHASE, "Hired ${article(worker.one)}", WORKER_PREFIX + workerId)
        if (state.count(workerId) == 0) worker.firstHireLine?.let { sim.events += EngineEvent(EventKind.STORY, it, WORKER_PREFIX + workerId) }
        checks(sim)
        return Outcome(sim.freeze(), sim.events.toList())
    }

    fun buy(state: GameState, upgradeId: String): Outcome {
        val up = content.upgradeById[upgradeId] ?: return Outcome(state, refusal = "No such improvement.")
        refuseIfBlocked(state)?.let { return Outcome(state, refusal = it) }
        if (upgradeId in state.upgrades) return Outcome(state, refusal = "Already bought.")
        if (UPGRADE_PREFIX + upgradeId !in state.revealed) return Outcome(state, refusal = "Not yet.")
        val cost = upgradeCost(state, upgradeId)
        shortfall(state, cost)?.let { return Outcome(state, refusal = it) }
        val sim = Sim(state)
        pay(sim, cost)
        sim.upgrades += upgradeId
        sim.events += EngineEvent(EventKind.PURCHASE, "Bought ${up.name.replaceFirstChar { it.lowercase() }}", UPGRADE_PREFIX + upgradeId)
        up.boughtLine?.let { sim.events += EngineEvent(EventKind.STORY, it, UPGRADE_PREFIX + upgradeId) }
        checks(sim)
        return Outcome(sim.freeze(), sim.events.toList())
    }

    fun commit(state: GameState, choiceId: String, optionId: String): Outcome {
        if (state.finished) return Outcome(state, refusal = "The chronicle is finished.")
        if (state.pendingChoiceId != choiceId) return Outcome(state, refusal = "That choice is not open.")
        val choice = content.choiceById[choiceId] ?: return Outcome(state, refusal = "That choice does not exist.")
        val option = choice.options.firstOrNull { it.id == optionId }
            ?: return Outcome(state, refusal = "That option does not exist.")
        val sim = Sim(state)
        sim.choices += MadeChoice(choiceId, optionId, choice.chapter, state.gameDay)
        sim.leanings[option.leaning] = (sim.leanings[option.leaning] ?: 0) + choice.weight
        sim.pending = null
        sim.revealed += TAB_CHRONICLE
        sim.events += EngineEvent(EventKind.CHOICE_MADE, option.line, "choice:$choiceId")
        checks(sim)
        return Outcome(sim.freeze(), sim.events.toList())
    }

    fun goalAmount(state: GameState): Double {
        val ch = content.chapter(state.chapter)
        return ch.goalAmount * ngp(state.newGamePlus, ch.number, onCost = true)
    }

    fun goalFraction(state: GameState): Double {
        if (state.finished) return 1.0
        return (state.goalProgress / goalAmount(state)).coerceIn(0.0, 1.0)
    }

    fun hireCost(state: GameState, workerId: String): Map<String, Double> {
        val w = content.workerById.getValue(workerId)
        val mods = modsOf(state.upgrades, state.choices)
        val scale = w.growth.pow(state.count(workerId)) * mods.f(COST, WORKER_PREFIX + workerId) *
            ngp(state.newGamePlus, w.chapter, onCost = true)
        return w.cost.mapValues { (_, v) -> ceil(v * scale) }
    }

    fun upgradeCost(state: GameState, upgradeId: String): Map<String, Double> {
        val u = content.upgradeById.getValue(upgradeId)
        val scale = ngp(state.newGamePlus, u.chapter, onCost = true)
        return u.cost.mapValues { (_, v) -> ceil(v * scale) }
    }

    fun actionCosts(state: GameState, actionId: String): Map<String, Double> {
        val a = content.actionById.getValue(actionId)
        val mods = modsOf(state.upgrades, state.choices)
        val tp = mods.f(THROUGHPUT, ACTION_PREFIX + actionId)
        val scale = ngp(state.newGamePlus, a.chapter, onCost = true)
        return a.costs.mapValues { (r, v) -> if (r == PENNIES) v * tp * scale else v * tp }
    }

    fun actionGains(state: GameState, actionId: String): Map<String, Double> {
        val a = content.actionById.getValue(actionId)
        val mods = modsOf(state.upgrades, state.choices)
        val key = ACTION_PREFIX + actionId
        return a.gains.mapValues { (_, v) -> v * mods.f(THROUGHPUT, key) * mods.f(YIELD, key) }
    }

    fun cooldownOf(state: GameState, actionId: String): Double {
        val a = content.actionById.getValue(actionId)
        return a.cooldown * modsOf(state.upgrades, state.choices).f(COOLDOWN, ACTION_PREFIX + actionId)
    }

    /** Net change per second of every resource with every hand fully supplied. */
    fun netRates(state: GameState): Map<String, Double> {
        val mods = modsOf(state.upgrades, state.choices)
        val out = HashMap<String, Double>()
        for (w in content.workers) {
            val n = state.count(w.id)
            if (n == 0) continue
            val key = WORKER_PREFIX + w.id
            val tp = mods.f(THROUGHPUT, key)
            for ((r, v) in w.produces) out[r] = (out[r] ?: 0.0) + v * n * tp * mods.f(YIELD, key)
            for ((r, v) in w.consumes) out[r] = (out[r] ?: 0.0) - v * n * tp * mods.f(THRIFT, key)
        }
        return out
    }

    fun workerOutput(state: GameState, workerId: String): Map<String, Double> {
        val w = content.workerById.getValue(workerId)
        val mods = modsOf(state.upgrades, state.choices)
        val key = WORKER_PREFIX + workerId
        return w.produces.mapValues { (_, v) -> v * mods.f(THROUGHPUT, key) * mods.f(YIELD, key) }
    }

    fun workerInput(state: GameState, workerId: String): Map<String, Double> {
        val w = content.workerById.getValue(workerId)
        val mods = modsOf(state.upgrades, state.choices)
        val key = WORKER_PREFIX + workerId
        return w.consumes.mapValues { (_, v) -> v * mods.f(THROUGHPUT, key) * mods.f(THRIFT, key) }
    }

    /** "Needs 40 more pennies" for the first resource that falls short, or null when affordable. */
    fun shortfall(state: GameState, cost: Map<String, Double>): String? {
        for ((r, need) in cost) {
            val have = state.amount(r)
            if (have + EPS < need) {
                val missing = ceil(need - have - EPS).coerceAtLeast(1.0)
                return "Needs ${Numbers.whole(missing)} more ${noun(r, missing)}"
            }
        }
        return null
    }

    fun affordable(state: GameState, cost: Map<String, Double>): Boolean = shortfall(state, cost) == null

    fun noun(resource: String, amount: Double): String {
        val def = content.resourceById[resource] ?: return resource
        return if (amount in 0.999..1.001) def.one else def.many
    }

    /** The noun that agrees with a figure as written: "1 folio", "1.2 folios", "0 folios". */
    fun nounFor(resource: String, figure: String): String = noun(resource, if (figure == "1") 1.0 else 2.0)

    fun visibleResources(state: GameState): List<ResourceDef> =
        content.resources.filter { RESOURCE_PREFIX + it.id in state.revealed }

    // endregion

    // region stepping, shared by tick, CatchUp and Projection

    internal fun openSim(state: GameState): Sim = Sim(state)
    internal fun modsFor(sim: Sim): Mods = mods(sim)

    internal fun step(sim: Sim, dt: Double, mods: Mods) {
        if (sim.ending != null) return
        if (sim.cooldowns.isNotEmpty()) {
            val it = sim.cooldowns.entries.iterator()
            while (it.hasNext()) {
                val e = it.next()
                val left = e.value - dt
                if (left <= 0.0) it.remove() else e.setValue(left)
            }
        }
        sim.gameSeconds += dt
        if (sim.pending == null) produce(sim, dt, mods)
        checks(sim)
    }

    private fun produce(sim: Sim, dt: Double, mods: Mods) {
        for (w in content.workers) {
            val n = sim.workers[w.id] ?: continue
            if (n <= 0) continue
            val key = WORKER_PREFIX + w.id
            val tp = mods.f(THROUGHPUT, key)
            val inScale = n * tp * mods.f(THRIFT, key) * dt
            var factor = 1.0
            for ((r, rate) in w.consumes) {
                val need = rate * inScale
                if (need > 0.0) factor = min(factor, (sim.res[r] ?: 0.0) / need)
            }
            if (factor <= 0.0) continue
            for ((r, rate) in w.consumes) {
                sim.res[r] = ((sim.res[r] ?: 0.0) - rate * inScale * factor).coerceAtLeast(0.0)
            }
            val outScale = n * tp * mods.f(YIELD, key) * dt * factor
            for ((r, rate) in w.produces) gain(sim, r, rate * outScale)
        }
    }

    private fun gain(sim: Sim, resource: String, amount: Double) {
        if (amount <= 0.0) return
        sim.res[resource] = (sim.res[resource] ?: 0.0) + amount
        sim.produced[resource] = (sim.produced[resource] ?: 0.0) + amount
        sim.totals[resource] = (sim.totals[resource] ?: 0.0) + amount
        if (resource == content.chapter(sim.chapter).goalResource) sim.progress += amount
    }

    private fun pay(sim: Sim, cost: Map<String, Double>) {
        for ((r, v) in cost) sim.res[r] = ((sim.res[r] ?: 0.0) - v).coerceAtLeast(0.0)
    }

    /** Reveals, choice triggers and the chapter goal, in that order. */
    private fun checks(sim: Sim) {
        if (sim.ending != null) return
        reveal(sim)
        if (sim.pending != null) return
        val ch = content.chapter(sim.chapter)
        val goal = ch.goalAmount * ngp(sim.newGamePlus, ch.number, onCost = true)
        ch.beats.forEachIndexed { i, beat ->
            val key = "beat:${ch.number}:$i"
            if (key !in sim.revealed && sim.progress + EPS >= beat.at * goal) {
                sim.revealed += key
                sim.events += EngineEvent(EventKind.STORY, beat.text, key)
            }
        }
        val made = sim.choices.mapTo(HashSet()) { it.choiceId }
        for (t in ch.triggers) {
            if (t.choiceId in made) continue
            if (sim.progress + EPS >= t.at * goal) {
                sim.pending = t.choiceId
                sim.events += EngineEvent(EventKind.CHOICE_OPENED, content.choiceById.getValue(t.choiceId).openLine, "choice:${t.choiceId}")
                return
            }
            // Triggers are ordered; a later one never fires before an earlier one.
            break
        }
        if (sim.progress + EPS < goal) return
        if (ch.triggers.any { it.choiceId !in made }) return
        sim.events += EngineEvent(EventKind.CHAPTER_END, ch.endLine, "chapter:${ch.number}")
        val last = content.chapters.maxOf { it.number }
        if (ch.number == last) {
            val ending = EndingResolver.resolve(sim.choices, content)
            sim.ending = ending
            sim.events += EngineEvent(EventKind.ENDING, content.endingById.getValue(ending).title, "ending:$ending")
        } else {
            sim.chapter = ch.number + 1
            sim.progress = 0.0
            sim.events += EngineEvent(EventKind.STORY, content.chapter(sim.chapter).opening, "chapter:${sim.chapter}")
            reveal(sim)
        }
    }

    private fun reveal(sim: Sim) {
        for ((r, v) in sim.res) if (v > 0.0) sim.revealed += RESOURCE_PREFIX + r
        for (a in content.actions) {
            val key = ACTION_PREFIX + a.id
            if (key in sim.revealed || a.chapter > sim.chapter) continue
            if (meets(sim, a.revealAt, a.chapter)) {
                sim.revealed += key
                a.revealLine?.let { sim.events += EngineEvent(EventKind.STORY, it, key) }
            }
        }
        for (w in content.workers) {
            val key = WORKER_PREFIX + w.id
            if (key in sim.revealed || w.chapter > sim.chapter) continue
            if (meets(sim, w.revealAt, w.chapter)) {
                sim.revealed += key
                sim.revealed += TAB_WORKSHOP
                w.revealLine?.let { sim.events += EngineEvent(EventKind.STORY, it, key) }
            }
        }
        for (u in content.upgrades) {
            val key = UPGRADE_PREFIX + u.id
            if (key in sim.revealed || u.chapter > sim.chapter) continue
            if (u.requires.all { it in sim.upgrades } && meets(sim, u.revealAt, u.chapter)) {
                sim.revealed += key
                sim.revealed += TAB_WORKSHOP
            }
        }
    }

    private fun meets(sim: Sim, at: Map<String, Double>, chapter: Int): Boolean {
        val scale = ngp(sim.newGamePlus, chapter, onCost = true)
        for ((r, v) in at) {
            val need = if (r == TAPS) v else v * scale
            val have = if (r == TAPS) sim.taps.toDouble() else sim.totals[r] ?: 0.0
            if (have + EPS < need) return false
        }
        return true
    }

    private fun refuseIfBlocked(state: GameState): String? = when {
        state.finished -> "The chronicle is finished."
        state.pendingChoiceId != null -> "A choice waits in the ledger."
        else -> null
    }

    private fun mods(sim: Sim): Mods = modsOf(sim.upgrades, sim.choices)

    private fun modsOf(upgrades: Collection<String>, choices: List<MadeChoice>): Mods {
        val effects = ArrayList<Effect>()
        for (id in upgrades) content.upgradeById[id]?.let { effects += it.effects }
        for (c in choices) {
            content.choiceById[c.choiceId]?.options?.firstOrNull { it.id == c.optionId }?.let { effects += it.effects }
        }
        return Mods(effects)
    }

    // endregion

    internal class Mods(effects: List<Effect>) {
        private val factors = HashMap<String, Double>()

        init {
            for (e in effects) {
                val k = e.kind + "|" + e.target
                factors[k] = (factors[k] ?: 1.0) * e.factor
            }
        }

        fun f(kind: String, target: String): Double = factors[kind + "|" + target] ?: 1.0
    }

    internal class Sim(s: GameState) {
        var chapter = s.chapter
        var gameSeconds = s.gameSeconds
        val res = HashMap(s.resources)
        val workers = HashMap(s.workers)
        val upgrades = HashSet(s.upgrades)
        val revealed = HashSet(s.revealed)
        val cooldowns = HashMap(s.cooldowns)
        var pending = s.pendingChoiceId
        val leanings = HashMap(s.leanings)
        val newGamePlus = s.newGamePlus
        val wall = s.lastWallClockMs
        var progress = s.goalProgress
        val choices = ArrayList(s.choices)
        var ending = s.endingId
        var taps = s.taps
        val totals = HashMap(s.totals)
        val events = ArrayList<EngineEvent>()
        val produced = HashMap<String, Double>()

        fun freeze(): GameState = GameState(
            chapter = chapter,
            gameSeconds = gameSeconds,
            resources = HashMap(res),
            workers = HashMap(workers),
            upgrades = HashSet(upgrades),
            revealed = HashSet(revealed),
            cooldowns = HashMap(cooldowns),
            pendingChoiceId = pending,
            leanings = HashMap(leanings),
            newGamePlus = newGamePlus,
            lastWallClockMs = wall,
            goalProgress = progress,
            choices = ArrayList(choices),
            endingId = ending,
            taps = taps,
            totals = HashMap(totals),
        )
    }

    companion object {
        const val VERSION = 1
        const val NG_PLUS_FACTOR = 0.6
        const val PENNIES = "pennies"
        const val TAPS = "taps"
        const val THROUGHPUT = "throughput"
        const val YIELD = "yield"
        const val THRIFT = "thrift"
        const val COOLDOWN = "cooldown"
        const val COST = "cost"
        const val ACTION_PREFIX = "action:"
        const val WORKER_PREFIX = "worker:"
        const val UPGRADE_PREFIX = "upgrade:"
        const val RESOURCE_PREFIX = "res:"
        const val TAB_WORKSHOP = "tab:workshop"
        const val TAB_CHRONICLE = "tab:chronicle"
        private const val EPS = 1e-9

        /** New Game+ makes chapter one and two goals and costs 60 percent. */
        fun ngp(newGamePlus: Boolean, chapter: Int, onCost: Boolean): Double =
            if (newGamePlus && onCost && chapter <= 2) NG_PLUS_FACTOR else 1.0

        fun article(noun: String): String =
            if (noun.firstOrNull()?.lowercaseChar() in setOf('a', 'e', 'i', 'o', 'u')) "an $noun" else "a $noun"
    }
}
