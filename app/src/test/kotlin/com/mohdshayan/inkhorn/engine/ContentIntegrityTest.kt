package com.mohdshayan.inkhorn.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ContentIntegrityTest {
    private val content = TestContent.content

    @Test
    fun countsMatchTheBlueprint() {
        assertEquals(6, content.chapters.size)
        assertEquals(48, content.upgrades.size)
        assertEquals(14, content.workers.size)
        assertEquals(11, content.choices.size)
        assertEquals(3, content.endings.size)
        assertEquals(listOf("The Quill", "The Ink Mill", "The Bindery", "The Lending Library", "The Press", "The Printing City"), content.chapters.map { it.title })
        assertEquals(1, content.choices.count { it.weight == 2 })
        assertEquals("edition", content.choices.last().id)
    }

    @Test
    fun everyIdResolves() {
        val resources = content.resources.map { it.id }.toSet()
        fun checkRes(m: Map<String, Double>, where: String) = m.keys.forEach { assertTrue("$where uses $it", it in resources || it == Engine.TAPS) }
        fun checkEffect(e: Effect, where: String) {
            val (kind, id) = e.target.split(":")
            when (kind) {
                "worker" -> assertTrue("$where targets $id", id in content.workerById)
                "action" -> assertTrue("$where targets $id", id in content.actionById)
                else -> error("$where has target ${e.target}")
            }
            assertTrue("$where kind ${e.kind}", e.kind in setOf("throughput", "yield", "thrift", "cooldown", "cost"))
        }
        content.actions.forEach { checkRes(it.costs, it.id); checkRes(it.gains, it.id); checkRes(it.revealAt, it.id) }
        content.workers.forEach { checkRes(it.cost, it.id); checkRes(it.produces, it.id); checkRes(it.consumes, it.id); checkRes(it.revealAt, it.id) }
        content.upgrades.forEach { u -> checkRes(u.cost, u.id); u.effects.forEach { checkEffect(it, u.id) }; u.requires.forEach { assertTrue(it in content.upgradeById) } }
        content.chapters.forEach { ch ->
            assertTrue(ch.goalResource in resources)
            ch.triggers.forEach { assertEquals(ch.number, content.choiceById.getValue(it.choiceId).chapter) }
        }
        val triggered = content.chapters.flatMap { ch -> ch.triggers.map { it.choiceId } }
        assertEquals("every choice is triggered exactly once", content.choices.map { it.id }.sorted(), triggered.sorted())
        content.choices.forEach { c -> c.options.forEach { o -> o.effects.forEach { checkEffect(it, "${c.id}/${o.id}") }; assertTrue(o.leaning in Leanings.all) } }
        assertEquals(Leanings.all.toSet(), content.endings.map { it.leaning }.toSet())
        assertEquals(content.upgrades.size, content.upgradeById.size)
    }

    @Test
    fun passagesRespectLimits() {
        for (c in content.choices) {
            val words = c.passage.split(Regex("\\s+")).size
            assertTrue("${c.id} passage has $words words", words <= 120)
            assertTrue("${c.id} has two or three options", c.options.size in 2..3)
            c.options.forEach { assertTrue("${c.id}/${it.id} consequence is one sentence", it.consequence.trim().endsWith(".")) }
        }
        content.chapters.forEach { assertEquals(1, it.initial.length); assertTrue(it.opening.startsWith(it.initial)) }
        for (ch in content.chapters) {
            val at = ch.beats.map { it.at }
            assertEquals("${ch.title} beats are in order", at.sorted(), at)
            assertTrue("${ch.title} beats sit inside the chapter", at.all { it > 0.0 && it < 1.0 })
            val triggers = ch.triggers.map { it.at }
            assertTrue("${ch.title} beats never land on a choice", at.none { a -> triggers.any { kotlin.math.abs(it - a) < 0.02 } })
        }
        assertTrue("every hand has a first-hire line", content.workers.all { !it.firstHireLine.isNullOrBlank() })
        assertTrue("every improvement has a purchase line", content.upgrades.all { !it.boughtLine.isNullOrBlank() })
    }

    @Test
    fun noEmOrEnDashesAnywhereInTheStory() {
        val text = listOf(File("src/main/assets/story.json"), File("app/src/main/assets/story.json")).first { it.exists() }.readText()
        assertFalse("em dash", text.contains('\u2014'))
        assertFalse("en dash", text.contains('\u2013'))
    }
}
