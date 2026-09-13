package com.mohdshayan.inkhorn.ui.settings

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mohdshayan.inkhorn.data.prefs.Settings
import com.mohdshayan.inkhorn.data.prefs.ThemeChoice
import com.mohdshayan.inkhorn.di.ServiceLocator
import com.mohdshayan.inkhorn.engine.Decoded
import com.mohdshayan.inkhorn.engine.NumberWords
import com.mohdshayan.inkhorn.engine.SaveFile
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface LoadAsk {
    data class Confirm(val file: SaveFile, val summary: String) : LoadAsk
    data class Problem(val message: String) : LoadAsk
}

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = ServiceLocator.appPrefs
    private val repo = ServiceLocator.repository
    private val transfer = ServiceLocator.saveTransfer

    /** The open load dialog, kept here so it survives a rotation. */
    var ask: LoadAsk? by mutableStateOf(null)

    val settings: StateFlow<Settings?> = prefs.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setTheme(t: ThemeChoice) = viewModelScope.launch { prefs.setTheme(t) }
    fun setHaptics(on: Boolean) = viewModelScope.launch { prefs.setHaptics(on) }
    fun setChapterNotes(on: Boolean) = viewModelScope.launch {
        prefs.setChapterNotes(on)
        prefs.setNotesAsked()
    }

    suspend fun shareIntent(): Intent = transfer.shareIntent(repo.exportSave())

    suspend fun inspect(uri: Uri): LoadAsk = when (val d = transfer.read(uri, ServiceLocator.contentLoader.load())) {
        is Decoded.Ok -> {
            val file = d.value
            val runs = file.runs.size
            val day = file.snapshot?.state?.gameDay
            val summary = buildString {
                append("Loading replaces your current run. ")
                append("This file holds ").append(
                    when (runs) {
                        0 -> "no chronicles, so a new one begins"
                        1 -> "one chronicle"
                        else -> "${NumberWords.words(runs)} chronicles"
                    },
                )
                if (day != null) append(", the latest on day ").append(day)
                append('.')
            }
            LoadAsk.Confirm(file, summary)
        }
        Decoded.NotASave -> LoadAsk.Problem("That file is not an Inkhorn save.")
        Decoded.Newer -> LoadAsk.Problem("That save is from a newer version of Inkhorn.")
    }

    suspend fun load(file: SaveFile): Boolean = try {
        repo.importSave(file)
        true
    } catch (_: Exception) {
        false
    }
}
