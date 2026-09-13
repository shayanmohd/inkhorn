package com.mohdshayan.inkhorn.ui.ledger

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mohdshayan.inkhorn.data.ChronicleData
import com.mohdshayan.inkhorn.data.LedgerLine
import com.mohdshayan.inkhorn.data.LoadStatus
import com.mohdshayan.inkhorn.di.ServiceLocator
import com.mohdshayan.inkhorn.engine.AwayEntry
import com.mohdshayan.inkhorn.engine.Content
import com.mohdshayan.inkhorn.engine.Engine
import com.mohdshayan.inkhorn.engine.GameState
import com.mohdshayan.inkhorn.engine.NumberWords
import com.mohdshayan.inkhorn.engine.Numbers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class StoreRow(val id: String, val amount: String, val noun: String, val rate: String?)

data class ActionUi(
    val id: String,
    val label: String,
    val detail: String?,
    val enabled: Boolean,
    val cooldown: Float,
    val cooldownText: String?,
)

data class OptionUi(val id: String, val label: String, val consequence: String)

data class ChoiceUi(
    val id: String,
    val title: String,
    val passage: String,
    val options: List<OptionUi>,
    /** New Game+ margin note: what the last finished chronicle chose here. */
    val marginNote: String?,
)

data class LedgerUi(
    val status: LoadStatus = LoadStatus.LOADING,
    val chapterLabel: String = "",
    val title: String = "",
    val initial: String = "",
    val openingRest: String = "",
    val goalText: String = "",
    val goalLine: String = "",
    val progress: Float = 0f,
    val stores: List<StoreRow> = emptyList(),
    val actions: List<ActionUi> = emptyList(),
    val lines: List<LedgerLine> = emptyList(),
    val choice: ChoiceUi? = null,
    val away: AwayEntry? = null,
    val notesAsk: Boolean = false,
    val banner: String? = null,
    val endingId: String? = null,
    val endingTitle: String? = null,
    val reviewDue: Boolean = false,
    val firstChapterStart: Boolean = false,
    /** "a second chronicle", "the third chronicle": what finishing this run lets the player begin. */
    val nextChronicle: String = "a second chronicle",
)

class LedgerViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = ServiceLocator.repository

    private val sheets = combine(repo.away, repo.notesAsk, repo.banner, repo.reviewDue) { away, ask, banner, review ->
        Sheets(away, ask, banner, review)
    }

    val ui: StateFlow<LedgerUi> = combine(repo.status, repo.state, repo.ledger, repo.chronicle, sheets) { status, state, lines, chronicle, sheets ->
        val content = repo.content.value
        val engine = repo.engine
        if (status != LoadStatus.READY || state == null || content == null || engine == null) {
            LedgerUi(status = status)
        } else {
            build(engine, content, state, lines, chronicle, sheets).copy(status = status)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LedgerUi())

    private data class Sheets(val away: AwayEntry?, val notesAsk: Boolean, val banner: String?, val reviewDue: Boolean)

    private fun build(engine: Engine, content: Content, s: GameState, lines: List<LedgerLine>, chronicle: ChronicleData, sheets: Sheets): LedgerUi {
        val ch = content.chapter(s.chapter)
        // What actually changes in the next second, so a hand waiting on an empty store shows no rate.
        val probe = engine.tick(s, 1.0).state
        val rates = probe.resources.mapValues { (r, v) -> v - s.amount(r) }
        val stores = engine.visibleResources(s).map { r ->
            val amount = s.amount(r.id)
            val rate = rates[r.id]?.takeIf { kotlin.math.abs(it) > 1e-9 }?.let { Numbers.perMinute(it) }
            val figure = Numbers.store(amount)
            StoreRow(r.id, figure, engine.nounFor(r.id, figure), rate)
        }
        // Hand work from long-past chapters leaves the desk; this chapter's and the last one's stay.
        val actions = content.actions.filter { Engine.ACTION_PREFIX + it.id in s.revealed && it.chapter >= s.chapter - 1 }.map { a ->
            val costs = engine.actionCosts(s, a.id)
            val shortfall = engine.shortfall(s, costs)
            val left = s.cooldowns[a.id] ?: 0.0
            val full = engine.cooldownOf(s, a.id)
            val detail = shortfall ?: costs[Engine.PENNIES]?.let { v -> "${Numbers.whole(kotlin.math.ceil(v))} ${engine.noun(Engine.PENNIES, kotlin.math.ceil(v))}" }
            ActionUi(
                id = a.id,
                label = a.label,
                detail = detail,
                enabled = shortfall == null && !s.finished && s.pendingChoiceId == null,
                cooldown = if (full > 0) (left / full).toFloat() else 0f,
                cooldownText = if (left > 0) "Ready in ${Numbers.seconds(left)}" else null,
            )
        }
        val goalRes = ch.goalResource
        val goal = engine.goalAmount(s)
        val choice = s.pendingChoiceId?.let { id ->
            val def = content.choiceById.getValue(id)
            val lastRun = chronicle.runs.filter { it.endedAt != null && it.id != repo.run.value?.id && it.endingId != null }.maxByOrNull { it.id }
            val lastPick = lastRun?.let { r -> chronicle.choices.firstOrNull { it.runId == r.id && it.choiceId == id } }
            val lastLabel = lastPick?.let { p -> def.options.firstOrNull { it.id == p.optionId }?.label }
            ChoiceUi(
                id = def.id,
                title = def.title,
                passage = def.passage,
                options = def.options.map { OptionUi(it.id, it.label, it.consequence) },
                marginNote = if (s.newGamePlus && lastLabel != null) "In your last chronicle you chose: $lastLabel." else null,
            )
        }
        return LedgerUi(
            chapterLabel = NumberWords.chapterName(ch.number),
            title = ch.title,
            initial = ch.initial,
            openingRest = ch.opening.drop(ch.initial.length).trimStart(),
            goalText = ch.goalText,
            goalLine = "${Numbers.whole(minOf(s.goalProgress, goal))} of ${Numbers.whole(goal)} ${engine.noun(goalRes, goal)}",
            progress = engine.goalFraction(s).toFloat(),
            stores = stores,
            actions = actions,
            lines = lines,
            choice = choice,
            away = sheets.away,
            notesAsk = sheets.notesAsk,
            banner = sheets.banner,
            endingId = s.endingId,
            endingTitle = s.endingId?.let { content.endingById[it]?.title },
            reviewDue = sheets.reviewDue,
            firstChapterStart = s.chapter == 1 && s.taps == 0 && !s.newGamePlus,
            nextChronicle = NumberWords.nextChronicle(chronicle.runs.maxOfOrNull { it.chronicleNumber } ?: 1),
        )
    }

    fun act(id: String) = repo.act(id)
    suspend fun commit(choiceId: String, optionId: String) = repo.commit(choiceId, optionId)
    fun dismissAway() = repo.dismissAway()
    fun dismissBanner() = repo.dismissBanner()
    fun answerNotesAsk() = repo.answerNotesAsk()
    fun reviewShown() = repo.reviewShown()
    suspend fun beginNewChronicle() = repo.beginNewChronicle()
    suspend fun beginSecondChronicle() = repo.beginSecondChronicle()
}
