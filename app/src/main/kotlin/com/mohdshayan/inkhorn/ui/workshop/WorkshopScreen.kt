package com.mohdshayan.inkhorn.ui.workshop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mohdshayan.inkhorn.data.LoadStatus
import com.mohdshayan.inkhorn.ui.components.EmptyState
import com.mohdshayan.inkhorn.ui.components.Hairline
import com.mohdshayan.inkhorn.ui.components.InkSmallButton
import com.mohdshayan.inkhorn.ui.components.LocalSnack
import com.mohdshayan.inkhorn.ui.components.PageGutter
import com.mohdshayan.inkhorn.ui.components.ReadingColumn
import com.mohdshayan.inkhorn.ui.components.SectionHeading
import com.mohdshayan.inkhorn.ui.components.SkeletonBlock
import com.mohdshayan.inkhorn.ui.components.figures
import com.mohdshayan.inkhorn.ui.components.rememberTapFeedback
import com.mohdshayan.inkhorn.ui.theme.LocalInk

@Composable
fun WorkshopScreen(contentPadding: PaddingValues, onBackToLedger: () -> Unit, viewModel: WorkshopViewModel = viewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val ink = LocalInk.current
    val snack = LocalSnack.current
    val tap = rememberTapFeedback()
    val padding = PaddingValues(
        start = PageGutter, end = PageGutter,
        top = 12.dp,
        bottom = contentPadding.calculateBottomPadding() + 32.dp,
    )
    ReadingColumn(Modifier.fillMaxSize()) {
        when {
            ui.status == LoadStatus.LOADING -> Column(Modifier.padding(top = contentPadding.calculateTopPadding()).padding(padding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SkeletonBlock(160.dp, 30.dp)
                repeat(4) { SkeletonBlock(null, 72.dp) }
            }
            ui.status == LoadStatus.UNREADABLE -> EmptyState(
                title = "The workshop could not be read.",
                body = "The save could not be read. The ledger has the ways to fix it.",
                actionLabel = "Back to the ledger",
                onAction = onBackToLedger,
                modifier = Modifier.padding(top = contentPadding.calculateTopPadding()).padding(padding),
            )
            ui.hands.isEmpty() && ui.improvements.isEmpty() && ui.bought.isEmpty() -> EmptyState(
                title = "Nothing to buy yet.",
                body = "Sell pages to earn pennies.",
                actionLabel = "Back to the ledger",
                onAction = onBackToLedger,
                modifier = Modifier.padding(top = contentPadding.calculateTopPadding()).padding(padding),
            )
            else -> LazyColumn(Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding()), contentPadding = padding) {
                item(key = "title") {
                    Text("Workshop", style = MaterialTheme.typography.headlineSmall, color = ink.ironGall, modifier = Modifier.semantics { heading() })
                }
                if (ui.pausedForChoice) {
                    item(key = "paused") {
                        Text(
                            "A choice waits in the ledger. Nothing can be hired or bought until it is made.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ink.fadedInk,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
                if (ui.hands.isNotEmpty()) {
                    item(key = "hands-h") { SectionHeading("Hands") }
                    items(ui.hands, key = { "hand-" + it.id }) { h ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.Top) {
                            Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(h.name, style = MaterialTheme.typography.titleMedium, color = ink.ironGall, modifier = Modifier.weight(1f, fill = false))
                                    if (h.count > 0) Text("  ${h.count}", style = MaterialTheme.typography.titleMedium.figures(), color = ink.verdigris)
                                }
                                Text(h.about, style = MaterialTheme.typography.bodySmall, color = ink.fadedInk)
                                Text(h.output, style = MaterialTheme.typography.bodySmall.figures(), color = ink.ironGall, modifier = Modifier.padding(top = 2.dp))
                                Text(
                                    h.shortfall ?: "Costs ${h.cost}",
                                    style = MaterialTheme.typography.bodySmall.figures(),
                                    color = ink.fadedInk,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                            Spacer(Modifier.padding(start = 12.dp))
                            InkSmallButton("Hire", enabled = h.shortfall == null && !h.blocked, onClick = {
                                tap()
                                val out = viewModel.hire(h.id)
                                out?.events?.firstOrNull()?.let { snack(it.text) }
                            })
                        }
                        Hairline()
                    }
                }
                if (ui.improvements.isNotEmpty()) {
                    item(key = "imp-h") { SectionHeading("Improvements") }
                    items(ui.improvements, key = { "imp-" + it.id }) { u ->
                        ImprovementRow(u, onBuy = {
                            tap()
                            val out = viewModel.buy(u.id)
                            out?.events?.firstOrNull()?.let { snack(it.text) }
                        })
                        Hairline()
                    }
                }
                if (ui.bought.isNotEmpty()) {
                    item(key = "bought-h") { SectionHeading("Already bought") }
                    items(ui.bought, key = { "bought-" + it.id }) { u ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp).semantics(mergeDescendants = true) {}) {
                            Text(u.name, style = MaterialTheme.typography.titleSmall, color = ink.ironGall)
                            Text(u.effect, style = MaterialTheme.typography.bodySmall, color = ink.fadedInk)
                        }
                    }
                }
                item(key = "end") { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
}

@Composable
private fun ImprovementRow(u: ImprovementUi, onBuy: () -> Unit) {
    val ink = LocalInk.current
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
            Text(u.name, style = MaterialTheme.typography.titleMedium, color = ink.ironGall)
            Text(u.period, style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic), color = ink.fadedInk)
            Text(u.effect, style = MaterialTheme.typography.bodyMedium, color = ink.ironGall, modifier = Modifier.padding(top = 4.dp))
            Text(u.shortfall ?: "Costs ${u.cost}", style = MaterialTheme.typography.bodySmall.figures(), color = ink.fadedInk, modifier = Modifier.padding(top = 2.dp))
        }
        Spacer(Modifier.padding(start = 12.dp))
        InkSmallButton("Buy", enabled = u.shortfall == null && !u.blocked, onClick = onBuy)
    }
}
