package com.mohdshayan.inkhorn.ui.chronicle

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mohdshayan.inkhorn.data.LoadStatus
import com.mohdshayan.inkhorn.ui.components.EmptyState
import com.mohdshayan.inkhorn.ui.components.Hairline
import com.mohdshayan.inkhorn.ui.components.InkButton
import com.mohdshayan.inkhorn.ui.components.LocalSnack
import com.mohdshayan.inkhorn.ui.components.PageGutter
import com.mohdshayan.inkhorn.ui.components.ReadingColumn
import com.mohdshayan.inkhorn.ui.components.SectionHeading
import com.mohdshayan.inkhorn.ui.components.SkeletonBlock
import com.mohdshayan.inkhorn.ui.components.figures
import com.mohdshayan.inkhorn.ui.theme.LocalInk

@Composable
fun ChronicleScreen(contentPadding: PaddingValues, onBackToLedger: () -> Unit, viewModel: ChronicleViewModel = viewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val ink = LocalInk.current
    val context = LocalContext.current
    val snack = LocalSnack.current
    val padding = PaddingValues(
        start = PageGutter, end = PageGutter,
        top = 12.dp,
        bottom = contentPadding.calculateBottomPadding() + 32.dp,
    )
    ReadingColumn(Modifier.fillMaxSize()) {
        when (ui.status) {
            LoadStatus.LOADING -> Column(Modifier.padding(top = contentPadding.calculateTopPadding()).padding(padding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SkeletonBlock(160.dp, 30.dp)
                repeat(5) { SkeletonBlock(null, 48.dp) }
            }
            LoadStatus.UNREADABLE -> EmptyState(
                title = "The chronicle could not be read.",
                body = "The ledger has the ways to load a save file or begin again.",
                actionLabel = "Back to the ledger",
                onAction = onBackToLedger,
                modifier = Modifier.padding(top = contentPadding.calculateTopPadding()).padding(padding),
            )
            LoadStatus.READY -> LazyColumn(Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding()), contentPadding = padding) {
                item(key = "title") {
                    Text("Chronicle", style = MaterialTheme.typography.headlineSmall, color = ink.ironGall, modifier = Modifier.semantics { heading() })
                }
                item(key = "current") {
                    Column(Modifier.padding(top = 12.dp).semantics(mergeDescendants = true) {}) {
                        Text(ui.currentName, style = MaterialTheme.typography.titleLarge, color = ink.ironGall)
                        Text(ui.currentDetail, style = MaterialTheme.typography.bodyMedium.figures(), color = ink.fadedInk)
                    }
                }
                item(key = "choices-h") { SectionHeading("Choices") }
                if (ui.choices.isEmpty()) {
                    item(key = "choices-empty") {
                        Text("Your first choice will be written here.", style = MaterialTheme.typography.bodyLarge, color = ink.fadedInk)
                    }
                }
                items(ui.choices, key = { it.key }) { c ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp).semantics(mergeDescendants = true) {}) {
                        Text(c.day, style = MaterialTheme.typography.labelMedium.figures(), color = ink.fadedInk, modifier = Modifier.width(64.dp).padding(top = 2.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.title, style = MaterialTheme.typography.titleSmall, color = ink.fadedInk)
                            Text(c.option, style = MaterialTheme.typography.bodyLarge, color = ink.ironGall)
                            Text(c.leaning, style = MaterialTheme.typography.bodySmall, color = ink.verdigris)
                        }
                    }
                }
                item(key = "endings-h") { SectionHeading("Endings") }
                items(ui.endings, key = { "ending-" + it.id }) { e ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp).semantics(mergeDescendants = true) {}) {
                        if (e.title != null) {
                            // Ending titles keep the Gotisch of the ending page, a size down.
                            Text(e.title, style = MaterialTheme.typography.headlineMedium.copy(fontSize = 24.sp, lineHeight = 30.sp), color = ink.ironGall)
                            Text(e.foundIn ?: "", style = MaterialTheme.typography.bodySmall, color = ink.fadedInk)
                        } else {
                            Text("An ending not yet found", style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic), color = ink.fadedInk)
                        }
                    }
                    Hairline()
                }
                if (ui.past.isNotEmpty()) {
                    item(key = "past-h") { SectionHeading("Past chronicles") }
                    items(ui.past, key = { "past-" + it.id }) { p ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp).semantics(mergeDescendants = true) {}) {
                            Text(p.name, style = MaterialTheme.typography.titleMedium, color = ink.ironGall)
                            Text(p.detail, style = MaterialTheme.typography.bodySmall.figures(), color = ink.fadedInk)
                        }
                    }
                }
                item(key = "totals-h") { SectionHeading("Kept on this phone") }
                items(ui.totals, key = { "total-" + it.first }) { (label, value) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).semantics(mergeDescendants = true) {}) {
                        Text(label, style = MaterialTheme.typography.bodyLarge, color = ink.fadedInk, modifier = Modifier.weight(1f))
                        Text(value, style = MaterialTheme.typography.bodyLarge.figures(), color = ink.ironGall)
                    }
                }
                item(key = "share") {
                    InkButton(
                        label = "Share chronicle",
                        modifier = Modifier.padding(top = 28.dp),
                        onClick = {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, ui.shareText)
                            }
                            context.startActivity(Intent.createChooser(send, "Share chronicle"))
                            snack("Chronicle shared")
                        },
                    )
                }
            }
        }
    }
}
