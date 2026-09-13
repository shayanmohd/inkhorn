package com.mohdshayan.inkhorn.ui.ledger

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.mohdshayan.inkhorn.di.ServiceLocator
import com.mohdshayan.inkhorn.engine.AwayEntry
import com.mohdshayan.inkhorn.ui.components.InkButton
import com.mohdshayan.inkhorn.ui.components.InkTextButton
import com.mohdshayan.inkhorn.ui.components.LocalSnack
import com.mohdshayan.inkhorn.ui.components.PageGutter
import com.mohdshayan.inkhorn.ui.components.figures
import com.mohdshayan.inkhorn.ui.components.rememberTapFeedback
import com.mohdshayan.inkhorn.ui.theme.LocalInk
import com.mohdshayan.inkhorn.ui.theme.LocalReducedMotion
import com.mohdshayan.inkhorn.ui.theme.RadiusMd
import com.mohdshayan.inkhorn.ui.theme.RadiusSm
import com.mohdshayan.inkhorn.work.Notes
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Every sheet scrolls, so its buttons stay reachable in landscape and at large font sizes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InkSheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val ink = LocalInk.current
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        // A tall sheet stops below the status bar instead of sliding its handle under the clock.
        modifier = Modifier.statusBarsPadding(),
        containerColor = ink.leaf,
        contentColor = ink.ironGall,
        scrimColor = MaterialTheme.colorScheme.scrim,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = PageGutter)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 600.dp).fillMaxWidth()) { content() }
        }
    }
}

@Composable
fun ChoiceSheet(choice: ChoiceUi, onDismiss: () -> Unit, onCommit: suspend (String, String) -> Boolean) {
    val ink = LocalInk.current
    val reduced = LocalReducedMotion.current
    val scope = rememberCoroutineScope()
    val tap = rememberTapFeedback()
    var selected by rememberSaveable(choice.id) { mutableStateOf<String?>(null) }
    var committing by rememberSaveable(choice.id) { mutableStateOf(false) }
    var failed by rememberSaveable(choice.id) { mutableStateOf(false) }

    InkSheet(onDismiss = { if (!committing) onDismiss() }) {
        Text(choice.title, style = MaterialTheme.typography.headlineSmall, color = ink.ironGall, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(12.dp))
        Text(choice.passage, style = MaterialTheme.typography.bodyLarge, color = ink.ironGall)
        choice.marginNote?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic), color = ink.fadedInk)
        }
        Spacer(Modifier.height(20.dp))
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for (option in choice.options) {
                val isSelected = selected == option.id
                val fade by animateFloatAsState(
                    targetValue = if (committing && !isSelected) 0f else 1f,
                    animationSpec = if (reduced) snap() else tween(250),
                    label = "option",
                )
                val shape = RoundedCornerShape(RadiusSm)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .alpha(fade)
                        .background(if (isSelected) ink.verdigris.copy(alpha = 0.10f) else ink.vellum, shape)
                        .border(BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) ink.verdigris else ink.fadedInk.copy(alpha = 0.5f)), shape)
                        .selectable(selected = isSelected, enabled = !committing, role = Role.RadioButton) {
                            tap()
                            selected = option.id
                            failed = false
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    Text(option.label, style = MaterialTheme.typography.titleMedium, color = ink.ironGall)
                    Spacer(Modifier.height(4.dp))
                    Text(option.consequence, style = MaterialTheme.typography.bodyMedium, color = ink.fadedInk)
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Text("You cannot change this later.", style = MaterialTheme.typography.bodyMedium, color = ink.rubric)
        if (failed) {
            Spacer(Modifier.height(6.dp))
            Text("The choice was not written. Commit again.", style = MaterialTheme.typography.bodyMedium, color = ink.rubric)
        }
        Spacer(Modifier.height(12.dp))
        InkButton(
            label = "Commit to this",
            enabled = selected != null && !committing,
            onClick = {
                val pick = selected ?: return@InkButton
                tap()
                committing = true
                scope.launch {
                    if (!reduced) delay(250)
                    val ok = onCommit(choice.id, pick)
                    if (!ok) {
                        committing = false
                        failed = true
                    }
                }
            },
        )
    }
}

@Composable
fun AwaySheet(entry: AwayEntry, onBack: () -> Unit) {
    val ink = LocalInk.current
    InkSheet(onDismiss = onBack) {
        Text("While you were away", style = MaterialTheme.typography.headlineSmall, color = ink.ironGall, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(14.dp))
        Text(
            entry.entry,
            style = MaterialTheme.typography.bodyLarge,
            color = ink.ironGall,
            modifier = Modifier
                .fillMaxWidth()
                .background(ink.vellum, RoundedCornerShape(RadiusMd))
                .padding(16.dp),
        )
        if (entry.changes.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            Text("The largest changes", style = MaterialTheme.typography.titleMedium, color = ink.ironGall)
            Spacer(Modifier.height(6.dp))
            for (c in entry.changes) {
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).semantics(mergeDescendants = true) {}) {
                    Text(c.text.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyLarge.figures(), color = ink.ironGall)
                }
            }
        }
        Spacer(Modifier.height(22.dp))
        InkButton("Back to work", onBack)
    }
}

@Composable
fun NotesAskSheet(onDone: () -> Unit) {
    val ink = LocalInk.current
    val context = LocalContext.current
    val snack = LocalSnack.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        scope.launch {
            ServiceLocator.appPrefs.setChapterNotes(granted)
            snack(if (granted) "Chapter notes are on" else "Notifications are blocked, so chapter notes stay off. Turn them on in Settings.")
            onDone()
        }
    }
    InkSheet(onDismiss = onDone) {
        Text("A note when a chapter ends", style = MaterialTheme.typography.headlineSmall, color = ink.ironGall, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(12.dp))
        Text(
            "Inkhorn can tell you when a chapter finishes while the game is closed. One note, only then, and the game plays the same without it.",
            style = MaterialTheme.typography.bodyLarge,
            color = ink.ironGall,
        )
        Spacer(Modifier.height(22.dp))
        InkButton("Tell me", onClick = {
            if (Build.VERSION.SDK_INT >= 33 && !Notes.permissionGranted(context)) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                scope.launch {
                    ServiceLocator.appPrefs.setChapterNotes(true)
                    snack("Chapter notes are on")
                    onDone()
                }
            }
        })
        Spacer(Modifier.height(8.dp))
        InkTextButton("Not now", onDone, Modifier.fillMaxWidth())
    }
}
