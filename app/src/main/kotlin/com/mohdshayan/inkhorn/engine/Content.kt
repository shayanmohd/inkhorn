package com.mohdshayan.inkhorn.engine

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json

/*
 * The whole game as data: resources, tap actions, hands, improvements, chapters, choices and
 * endings. Parsed from assets/story.json on the device and from the same file in JVM tests.
 */

@Serializable
data class Content(
    val engineVersion: Int,
    val start: Map<String, Double>,
    val resources: List<ResourceDef>,
    val actions: List<ActionDef>,
    val workers: List<WorkerDef>,
    val upgrades: List<UpgradeDef>,
    val chapters: List<ChapterDef>,
    val choices: List<ChoiceDef>,
    val endings: List<EndingDef>,
    val credits: List<String>,
) {
    @Transient val resourceById = resources.associateBy { it.id }
    @Transient val actionById = actions.associateBy { it.id }
    @Transient val workerById = workers.associateBy { it.id }
    @Transient val upgradeById = upgrades.associateBy { it.id }
    @Transient val choiceById = choices.associateBy { it.id }
    @Transient val endingById = endings.associateBy { it.id }

    @Transient private val chapterByNumber = chapters.associateBy { it.number }
    fun chapter(number: Int): ChapterDef = chapterByNumber.getValue(number)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        fun parse(text: String): Content = json.decodeFromString(serializer(), text)
    }
}

@Serializable
data class ResourceDef(
    val id: String,
    val one: String,
    val many: String,
    /** Past participle for the away entry, "copied" in "412 folios copied". */
    val gained: String,
    val chapter: Int = 1,
)

@Serializable
data class ActionDef(
    val id: String,
    val label: String,
    val chapter: Int,
    val cooldown: Double,
    val costs: Map<String, Double> = emptyMap(),
    val gains: Map<String, Double> = emptyMap(),
    val revealAt: Map<String, Double> = emptyMap(),
    val revealLine: String? = null,
    /** Ledger line after a tap, with the same verb as the button. */
    val doneLine: String,
)

@Serializable
data class WorkerDef(
    val id: String,
    val one: String,
    val many: String,
    val chapter: Int,
    val cost: Map<String, Double>,
    val growth: Double,
    val produces: Map<String, Double> = emptyMap(),
    val consumes: Map<String, Double> = emptyMap(),
    val revealAt: Map<String, Double> = emptyMap(),
    val revealLine: String? = null,
    val about: String,
    /** Written to the ledger after the first hire of this hand. */
    val firstHireLine: String? = null,
)

@Serializable
data class Effect(
    /** throughput (produces and consumes), yield (produces), thrift (consumes), cooldown, cost. */
    val kind: String,
    /** worker:<id> or action:<id>. */
    val target: String,
    val factor: Double,
)

@Serializable
data class UpgradeDef(
    val id: String,
    val name: String,
    /** One line of period colour under the name. */
    val period: String,
    val chapter: Int,
    val cost: Map<String, Double>,
    val effects: List<Effect>,
    val effectText: String,
    val revealAt: Map<String, Double> = emptyMap(),
    val requires: List<String> = emptyList(),
    /** Written to the ledger after the purchase. */
    val boughtLine: String? = null,
)

@Serializable
data class ChoiceTrigger(val choiceId: String, val at: Double)

/** A line of story written to the ledger when the chapter goal passes [at]. */
@Serializable
data class Beat(val at: Double, val text: String)

@Serializable
data class ChapterDef(
    val number: Int,
    val title: String,
    val initial: String,
    val opening: String,
    val goalText: String,
    val goalResource: String,
    val goalAmount: Double,
    /** Noun for the away entry: "three days' copying". */
    val work: String,
    val triggers: List<ChoiceTrigger>,
    val beats: List<Beat> = emptyList(),
    val endLine: String,
    /** Written to the ledger when the chapter is finished while the game is closed. */
    val noteLine: String,
)

@Serializable
data class OptionDef(
    val id: String,
    val label: String,
    val consequence: String,
    val leaning: String,
    val effects: List<Effect> = emptyList(),
    val line: String,
)

@Serializable
data class ChoiceDef(
    val id: String,
    val chapter: Int,
    val title: String,
    val openLine: String,
    val passage: String,
    val weight: Int = 1,
    val options: List<OptionDef>,
)

@Serializable
data class EndingDef(
    val id: String,
    val leaning: String,
    val title: String,
    val passage: List<String>,
)

object Leanings {
    const val COMMONS = "commons"
    const val CROWN = "crown"
    const val CLOISTER = "cloister"
    val all = listOf(COMMONS, CROWN, CLOISTER)

    fun name(id: String): String = when (id) {
        COMMONS -> "the Commons"
        CROWN -> "the Crown"
        CLOISTER -> "the Cloister"
        else -> id
    }
}
