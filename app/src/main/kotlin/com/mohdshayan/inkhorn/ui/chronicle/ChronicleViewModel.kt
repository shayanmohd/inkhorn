package com.mohdshayan.inkhorn.ui.chronicle

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mohdshayan.inkhorn.data.LoadStatus
import com.mohdshayan.inkhorn.di.ServiceLocator
import com.mohdshayan.inkhorn.engine.ChoiceRecord
import com.mohdshayan.inkhorn.engine.ChronicleText
import com.mohdshayan.inkhorn.engine.NumberWords
import com.mohdshayan.inkhorn.engine.Numbers
import com.mohdshayan.inkhorn.engine.RunRecord
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class ChoiceRow(val key: String, val day: String, val title: String, val option: String, val leaning: String)
data class EndingSlot(val id: String, val title: String?, val foundIn: String?)
data class PastRun(val id: Long, val name: String, val detail: String)

data class ChronicleUi(
    val status: LoadStatus = LoadStatus.LOADING,
    val currentName: String = "",
    val currentDetail: String = "",
    val choices: List<ChoiceRow> = emptyList(),
    val endings: List<EndingSlot> = emptyList(),
    val past: List<PastRun> = emptyList(),
    val totals: List<Pair<String, String>> = emptyList(),
    val shareText: String = "",
)

class ChronicleViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = ServiceLocator.repository

    val ui: StateFlow<ChronicleUi> = combine(repo.status, repo.chronicle, repo.run, repo.state.map { it?.gameDay to it?.chapter }.distinctUntilChanged()) { status, data, run, (gameDay, chapter) ->
        val content = repo.content.value
        if (status == LoadStatus.LOADING || content == null) return@combine ChronicleUi(status = LoadStatus.LOADING)
        val runs = data.runs.map { r ->
            // The open run's play time is still counting in memory; prefer the live value.
            val live = if (run != null && r.id == run.id) run else r
            RunRecord(live.id, live.startedAt, live.endedAt, live.endingId, live.chronicleNumber, live.playSeconds)
        }
        val choices = data.choices.map { ChoiceRecord(it.runId, it.choiceId, it.optionId, it.chapter, it.gameDay, it.madeAt) }
        val current = runs.firstOrNull { it.id == run?.id }
        val currentChoices = choices.filter { it.runId == current?.id }.sortedBy { it.madeAt }.mapNotNull { c ->
            val def = content.choiceById[c.choiceId] ?: return@mapNotNull null
            val opt = def.options.firstOrNull { it.id == c.optionId } ?: return@mapNotNull null
            ChoiceRow(c.runId.toString() + c.choiceId, "Day ${c.gameDay}", def.title, opt.label, ChronicleText.leaningPhrase(opt.leaning))
        }
        val endings = content.endings.map { e ->
            val first = runs.filter { it.endingId == e.id }.minByOrNull { it.chronicleNumber }
            EndingSlot(e.id, first?.let { e.title }, first?.let { "Found in ${NumberWords.chronicleName(it.chronicleNumber).replaceFirstChar { c -> c.lowercase() }}" })
        }
        val past = runs.filter { it.id != current?.id }.sortedByDescending { it.chronicleNumber }.map { r ->
            val ending = r.endingId?.let { content.endingById[it]?.title }
            PastRun(r.id, NumberWords.chronicleName(r.chronicleNumber), (if (ending != null) "Ended in $ending" else "Left unfinished") + ", " + Numbers.playTime(r.playSeconds) + " played")
        }
        val totalSeconds = runs.sumOf { it.playSeconds }
        ChronicleUi(
            status = status,
            currentName = current?.let { NumberWords.chronicleName(it.chronicleNumber) } ?: "",
            currentDetail = if (current == null) "" else buildString {
                append("Day ").append(gameDay ?: 1).append(", ")
                append(Numbers.playTime(current.playSeconds)).append(" played")
                current.endingId?.let { content.endingById[it]?.title }?.let { append(". Ended in ").append(it) }
            },
            choices = currentChoices,
            endings = endings,
            past = past,
            totals = listOf(
                // Counted from the runs themselves, so a loaded save file shows its own history.
                "Chapters finished" to (
                    runs.count { it.endingId != null } * content.chapters.size +
                        (if (current != null && current.endingId == null) (chapter ?: 1) - 1 else 0)
                    ).toString(),
                "Choices made" to choices.size.toString(),
                "Endings found" to "${runs.mapNotNull { it.endingId }.toSet().size} of ${content.endings.size}",
                "Time played" to Numbers.playTime(totalSeconds),
            ),
            shareText = ChronicleText.build(content, runs, choices),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChronicleUi())
}
