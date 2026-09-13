package com.mohdshayan.inkhorn.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// One DataStore per process. The delegate must stay top level so every AppPrefs shares it.
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_prefs")

enum class ThemeChoice(val key: String, val label: String) {
    SYSTEM("system", "Follow the system"),
    LIGHT("light", "Light"),
    DARK("dark", "Dark"),
    TRUE_BLACK("true_black", "True black");

    companion object {
        fun from(key: String?): ThemeChoice = entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

data class Settings(
    val theme: ThemeChoice = ThemeChoice.SYSTEM,
    val chapterNotes: Boolean = false,
    val notesAsked: Boolean = false,
    val haptics: Boolean = true,
    val firstLinePlayed: Boolean = false,
    val chaptersCompletedTotal: Int = 0,
    val reviewPrompted: Boolean = false,
    val onboardingDone: Boolean = false,
)

/** Settings and small flags from blueprint section 9. Anything that is a list belongs in Room. */
class AppPrefs(private val context: Context) {

    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val CHAPTER_NOTES = booleanPreferencesKey("chapter_notes")
        val NOTES_ASKED = booleanPreferencesKey("notes_asked")
        val HAPTICS = booleanPreferencesKey("haptics")
        val FIRST_LINE_PLAYED = booleanPreferencesKey("first_line_played")
        val CHAPTERS_COMPLETED_TOTAL = intPreferencesKey("chapters_completed_total")
        val REVIEW_PROMPTED = booleanPreferencesKey("review_prompted")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            theme = ThemeChoice.from(p[Keys.THEME]),
            chapterNotes = p[Keys.CHAPTER_NOTES] ?: false,
            notesAsked = p[Keys.NOTES_ASKED] ?: false,
            haptics = p[Keys.HAPTICS] ?: true,
            firstLinePlayed = p[Keys.FIRST_LINE_PLAYED] ?: false,
            chaptersCompletedTotal = p[Keys.CHAPTERS_COMPLETED_TOTAL] ?: 0,
            reviewPrompted = p[Keys.REVIEW_PROMPTED] ?: false,
            onboardingDone = p[Keys.ONBOARDING_DONE] ?: false,
        )
    }

    suspend fun current(): Settings = settings.first()

    suspend fun setTheme(theme: ThemeChoice) = context.dataStore.edit { it[Keys.THEME] = theme.key }
    suspend fun setChapterNotes(on: Boolean) = context.dataStore.edit { it[Keys.CHAPTER_NOTES] = on }
    suspend fun setNotesAsked() = context.dataStore.edit { it[Keys.NOTES_ASKED] = true }
    suspend fun setHaptics(on: Boolean) = context.dataStore.edit { it[Keys.HAPTICS] = on }
    suspend fun setFirstLinePlayed() = context.dataStore.edit { it[Keys.FIRST_LINE_PLAYED] = true }
    suspend fun setReviewPrompted() = context.dataStore.edit { it[Keys.REVIEW_PROMPTED] = true }
    suspend fun setOnboardingDone() = context.dataStore.edit { it[Keys.ONBOARDING_DONE] = true }
    suspend fun addChapterCompleted() = context.dataStore.edit {
        it[Keys.CHAPTERS_COMPLETED_TOTAL] = (it[Keys.CHAPTERS_COMPLETED_TOTAL] ?: 0) + 1
    }
}
