package com.mohdshayan.inkhorn.ui.workshop

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mohdshayan.inkhorn.data.LoadStatus
import com.mohdshayan.inkhorn.di.ServiceLocator
import com.mohdshayan.inkhorn.engine.Engine
import com.mohdshayan.inkhorn.engine.GameState
import com.mohdshayan.inkhorn.engine.Numbers
import com.mohdshayan.inkhorn.engine.joinNaturally
import com.mohdshayan.inkhorn.engine.Outcome
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlin.math.ceil

data class HandUi(
    val id: String,
    val name: String,
    val count: Int,
    val about: String,
    val output: String,
    val cost: String,
    val shortfall: String?,
    val blocked: Boolean,
)

data class ImprovementUi(
    val id: String,
    val name: String,
    val period: String,
    val effect: String,
    val cost: String,
    val shortfall: String?,
    val bought: Boolean,
    val blocked: Boolean,
)

data class WorkshopUi(
    val status: LoadStatus = LoadStatus.LOADING,
    val hands: List<HandUi> = emptyList(),
    val improvements: List<ImprovementUi> = emptyList(),
    val bought: List<ImprovementUi> = emptyList(),
    val pausedForChoice: Boolean = false,
)

class WorkshopViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = ServiceLocator.repository

    val ui: StateFlow<WorkshopUi> = combine(repo.status, repo.state) { status, state ->
        val engine = repo.engine
        if (status != LoadStatus.READY || state == null || engine == null) WorkshopUi(status = status) else build(engine, state).copy(status = status)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkshopUi())

    private fun costText(engine: Engine, cost: Map<String, Double>): String =
        cost.entries.map { (r, v) -> Numbers.whole(ceil(v)).let { f -> "$f ${engine.nounFor(r, f)}" } }.joinNaturally()

    private fun build(engine: Engine, s: GameState): WorkshopUi {
        val content = engine.content
        val blocked = s.pendingChoiceId != null || s.finished
        val hands = content.workers
            .filter { Engine.WORKER_PREFIX + it.id in s.revealed }
            // This chapter's hands first: the ones worth hiring now sit at the top.
            .sortedWith(compareByDescending<com.mohdshayan.inkhorn.engine.WorkerDef> { it.chapter }.thenBy { it.cost[Engine.PENNIES] ?: 0.0 })
            .map { w ->
                val cost = engine.hireCost(s, w.id)
                val perMinute = { r: String, v: Double -> Numbers.perMinute(v).removeSuffix(" a minute").let { f -> "$f ${engine.nounFor(r, f)}" } }
                val out = engine.workerOutput(s, w.id).entries.map { (r, v) -> perMinute(r, v) }.joinNaturally()
                val input = engine.workerInput(s, w.id).entries.map { (r, v) -> perMinute(r, v) }.joinNaturally()
                val count = s.count(w.id)
                HandUi(
                    id = w.id,
                    name = if (count == 1) w.one.replaceFirstChar { it.uppercase() } else w.many.replaceFirstChar { it.uppercase() },
                    count = count,
                    about = w.about,
                    output = "Each makes $out a minute" + if (input.isNotEmpty()) ", using $input" else "",
                    cost = costText(engine, cost),
                    shortfall = engine.shortfall(s, cost),
                    blocked = blocked,
                )
            }
        val visible = content.upgrades.filter { Engine.UPGRADE_PREFIX + it.id in s.revealed }.sortedByDescending { it.chapter }
        fun improvement(id: String): ImprovementUi {
            val u = content.upgradeById.getValue(id)
            val cost = engine.upgradeCost(s, id)
            return ImprovementUi(
                id = u.id, name = u.name, period = u.period, effect = u.effectText,
                cost = costText(engine, cost), shortfall = engine.shortfall(s, cost),
                bought = id in s.upgrades, blocked = blocked,
            )
        }
        val (bought, open) = visible.map { improvement(it.id) }.partition { it.bought }
        return WorkshopUi(
            hands = hands,
            improvements = open,
            bought = bought.reversed(),
            pausedForChoice = s.pendingChoiceId != null,
        )
    }

    fun hire(id: String): Outcome? = repo.hire(id)
    fun buy(id: String): Outcome? = repo.buy(id)
}
