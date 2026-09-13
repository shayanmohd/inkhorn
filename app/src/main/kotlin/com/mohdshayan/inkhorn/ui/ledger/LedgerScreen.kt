package com.mohdshayan.inkhorn.ui.ledger

import android.app.Activity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mohdshayan.inkhorn.data.LedgerLine
import com.mohdshayan.inkhorn.data.LoadStatus
import com.mohdshayan.inkhorn.di.ServiceLocator
import com.mohdshayan.inkhorn.review.ReviewPrompter
import com.mohdshayan.inkhorn.ui.components.ErrorNote
import com.mohdshayan.inkhorn.ui.components.Hairline
import com.mohdshayan.inkhorn.ui.components.InkButton
import com.mohdshayan.inkhorn.ui.components.InkTextButton
import com.mohdshayan.inkhorn.ui.components.LocalSnack
import com.mohdshayan.inkhorn.ui.components.PageGutter
import com.mohdshayan.inkhorn.ui.components.ReadingColumn
import com.mohdshayan.inkhorn.ui.components.SectionHeading
import com.mohdshayan.inkhorn.ui.components.SkeletonBlock
import com.mohdshayan.inkhorn.ui.components.figures
import com.mohdshayan.inkhorn.ui.components.rememberTapFeedback
import com.mohdshayan.inkhorn.ui.theme.LocalInk
import com.mohdshayan.inkhorn.ui.theme.LocalReducedMotion
import kotlinx.coroutines.launch

@Composable
fun LedgerScreen(
    contentPadding: PaddingValues,
    firstLinePlayed: Boolean,
    onOpenSettings: () -> Unit,
    onOpenEnding: (String) -> Unit,
    viewModel: LedgerViewModel = viewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val tap = rememberTapFeedback()
    val snack = LocalSnack.current
    var choiceHidden by rememberSaveable(ui.choice?.id) { mutableStateOf(false) }

    ReadingColumn(Modifier.fillMaxSize()) {
        when (ui.status) {
            LoadStatus.LOADING -> LedgerSkeleton(contentPadding)
            LoadStatus.UNREADABLE -> UnreadableSave(
                contentPadding = contentPadding,
                onOpenSettings = onOpenSettings,
                onBeginNew = { scope.launch { viewModel.beginNewChronicle() } },
            )
            LoadStatus.READY -> LedgerContent(
                ui = ui,
                contentPadding = contentPadding,
                firstLinePlayed = firstLinePlayed,
                onOpenSettings = onOpenSettings,
                onAct = { id -> tap(); viewModel.act(id) },
                onDismissBanner = viewModel::dismissBanner,
                onOpenEnding = onOpenEnding,
                onBeginSecond = {
                    val next = ui.nextChronicle
                    scope.launch {
                        viewModel.beginSecondChronicle()
                        snack("Began $next")
                    }
                },
                onOpenChoice = { choiceHidden = false },
            )
        }
    }

    if (ui.status == LoadStatus.READY) {
        when {
            ui.away != null -> AwaySheet(ui.away!!, onBack = viewModel::dismissAway)
            ui.choice != null && !choiceHidden -> ChoiceSheet(
                choice = ui.choice!!,
                onDismiss = { choiceHidden = true },
                onCommit = { c, o -> viewModel.commit(c, o) },
            )
            ui.notesAsk -> NotesAskSheet(onDone = viewModel::answerNotesAsk)
        }
    }

    // The review prompt: once, back on the ledger after chapter three, never over a sheet.
    val activity = LocalContext.current as? Activity
    val sheetOpen = ui.away != null || ui.choice != null || ui.notesAsk
    LaunchedEffect(ui.reviewDue, sheetOpen) {
        if (ui.reviewDue && !sheetOpen && activity != null) {
            viewModel.reviewShown()
            ReviewPrompter.prompt(activity)
        }
    }
}

@Composable
private fun LedgerContent(
    ui: LedgerUi,
    contentPadding: PaddingValues,
    firstLinePlayed: Boolean,
    onOpenSettings: () -> Unit,
    onAct: (String) -> Unit,
    onDismissBanner: () -> Unit,
    onOpenEnding: (String) -> Unit,
    onBeginSecond: () -> Unit,
    onOpenChoice: () -> Unit,
) {
    val ink = LocalInk.current
    val reduced = LocalReducedMotion.current
    LazyColumn(
        // The status bar inset is padding outside the list, so scrolled rows never slide under it.
        modifier = Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding()),
        contentPadding = PaddingValues(
            start = PageGutter, end = PageGutter,
            top = 4.dp,
            bottom = contentPadding.calculateBottomPadding() + 32.dp,
        ),
    ) {
        item(key = "top") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(ui.chapterLabel, style = MaterialTheme.typography.labelMedium, color = ink.fadedInk, modifier = Modifier.weight(1f))
                IconButton(onClick = onOpenSettings, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.Settings, contentDescription = "Settings", tint = ink.fadedInk)
                }
            }
        }
        item(key = "title") {
            Text(
                ui.title,
                style = MaterialTheme.typography.headlineMedium,
                color = ink.ironGall,
                modifier = Modifier.semantics { heading() },
            )
        }
        item(key = "opening") {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.Top) {
                GrowingInitial(ui.initial, ui.progress)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    RevealText(
                        text = ui.openingRest,
                        reveal = ui.firstChapterStart && !firstLinePlayed && !reduced,
                    )
                }
            }
        }
        item(key = "goal") {
            Column(Modifier.padding(top = 16.dp).semantics(mergeDescendants = true) {}) {
                Text(ui.goalText, style = MaterialTheme.typography.titleMedium, color = ink.ironGall)
                Text(ui.goalLine, style = MaterialTheme.typography.bodyMedium.figures(), color = ink.fadedInk)
            }
        }
        ui.banner?.let { banner ->
            item(key = "banner") {
                ErrorNote(banner, Modifier.padding(top = 20.dp)) {
                    InkTextButton("Dismiss", onDismissBanner, Modifier.align(Alignment.End))
                }
            }
        }
        if (ui.endingId != null) {
            item(key = "finished") {
                Column(Modifier.padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Hairline()
                    Text(
                        "This chronicle is finished. It ended in ${ui.endingTitle}.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = ink.ironGall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    InkButton("Read the ending again", { onOpenEnding(ui.endingId) })
                    InkButton("Begin ${ui.nextChronicle}", onBeginSecond)
                }
            }
        }
        if (ui.endingId == null && ui.actions.isNotEmpty()) {
            item(key = "actions-h") { Spacer(Modifier.height(16.dp)) }
            if (ui.choice != null) {
                item(key = "waiting") {
                    Column(Modifier.padding(bottom = 8.dp)) {
                        Text(
                            "Production is paused. A choice waits in the ledger.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ink.fadedInk,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        InkButton("Open the choice", onOpenChoice)
                    }
                }
            }
            items(ui.actions, key = { "action-" + it.id }) { a ->
                InkButton(
                    label = a.label,
                    onClick = { onAct(a.id) },
                    detail = a.detail,
                    enabled = a.enabled,
                    cooldown = a.cooldown,
                    cooldownText = a.cooldownText,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
        }
        if (ui.stores.isNotEmpty()) {
            item(key = "stores-h") { SectionHeading("Stores") }
            items(ui.stores, key = { "store-" + it.id }) { row ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp)
                        .semantics(mergeDescendants = true) {},
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${row.amount} ${row.noun}",
                        style = MaterialTheme.typography.bodyLarge.figures(),
                        color = ink.ironGall,
                        modifier = Modifier.weight(1f),
                    )
                    if (row.rate != null) {
                        Text(row.rate, style = MaterialTheme.typography.bodyMedium.figures(), color = ink.fadedInk)
                    }
                }
            }
        }
        item(key = "ledger-h") { SectionHeading("Ledger") }
        ledgerRows(ui.lines, reduced)
    }
}

private fun LazyListScope.ledgerRows(lines: List<LedgerLine>, reduced: Boolean) {
    items(lines, key = { it.key }) { line ->
        val ink = LocalInk.current
        val strong = line.kind == "choice" || line.kind == "chapter_end" || line.kind == "away" || line.kind == "ending"
        Row(
            Modifier
                .fillMaxWidth()
                .animateItem(fadeInSpec = if (reduced) null else tween(200), placementSpec = null, fadeOutSpec = null)
                .padding(vertical = 7.dp)
                .semantics(mergeDescendants = true) {},
        ) {
            Text(
                "Day ${line.gameDay}",
                style = MaterialTheme.typography.labelMedium.figures(),
                color = ink.fadedInk,
                modifier = Modifier.width(64.dp).padding(top = 2.dp),
                maxLines = 1,
                overflow = TextOverflow.Visible,
            )
            Text(
                line.text,
                style = if (strong) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
                color = if (strong) ink.ironGall else ink.fadedInk,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** The one orchestrated first-run moment: the opening line inks in left to right over 900 ms. */
@Composable
private fun RevealText(text: String, reveal: Boolean) {
    val ink = LocalInk.current
    val progress = remember { Animatable(if (reveal) 0f else 1f) }
    LaunchedEffect(reveal) {
        if (reveal) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(900, easing = LinearEasing))
            ServiceLocator.appPrefs.setFirstLinePlayed()
        } else {
            progress.snapTo(1f)
        }
    }
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = ink.ironGall,
        modifier = Modifier.drawWithContent {
            val p = progress.value
            if (p >= 1f) {
                drawContent()
            } else {
                clipRect(right = size.width * p) { this@drawWithContent.drawContent() }
            }
        },
    )
}

@Composable
private fun LedgerSkeleton(contentPadding: PaddingValues) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = PageGutter)
            .padding(top = contentPadding.calculateTopPadding() + 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SkeletonBlock(96.dp, 14.dp)
        SkeletonBlock(180.dp, 30.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            SkeletonBlock(108.dp, 108.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(4) { SkeletonBlock(null, 14.dp) }
            }
        }
        Spacer(Modifier.height(20.dp))
        repeat(3) { SkeletonBlock(null, 18.dp) }
        Spacer(Modifier.height(16.dp))
        repeat(3) { SkeletonBlock(null, 52.dp) }
    }
}

@Composable
private fun UnreadableSave(contentPadding: PaddingValues, onOpenSettings: () -> Unit, onBeginNew: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = PageGutter)
            .padding(top = contentPadding.calculateTopPadding() + 48.dp),
    ) {
        ErrorNote("This save could not be read. Load a save file from Settings or begin a new chronicle.") {
            InkButton("Load a save file", onOpenSettings)
            InkButton("Begin a new chronicle", onBeginNew)
        }
    }
}
