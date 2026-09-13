package com.mohdshayan.inkhorn.ui.ending

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mohdshayan.inkhorn.data.LoadStatus
import com.mohdshayan.inkhorn.di.ServiceLocator
import com.mohdshayan.inkhorn.engine.NumberWords
import com.mohdshayan.inkhorn.ui.components.EmptyState
import com.mohdshayan.inkhorn.ui.components.Hairline
import com.mohdshayan.inkhorn.ui.components.InkButton
import com.mohdshayan.inkhorn.ui.components.InkTextButton
import com.mohdshayan.inkhorn.ui.components.LocalSnack
import com.mohdshayan.inkhorn.ui.components.PageGutter
import com.mohdshayan.inkhorn.ui.components.ReadingColumn
import com.mohdshayan.inkhorn.ui.components.SkeletonBlock
import com.mohdshayan.inkhorn.ui.theme.LocalInk
import kotlinx.coroutines.launch

/** The last page: the ending passage, then the credits as a static page. */
@Composable
fun EndingScreen(endingId: String, contentPadding: PaddingValues, onReturnToLedger: () -> Unit) {
    val repo = ServiceLocator.repository
    val status by repo.status.collectAsStateWithLifecycle()
    val content by repo.content.collectAsStateWithLifecycle()
    val chronicle by repo.chronicle.collectAsStateWithLifecycle()
    val next = NumberWords.nextChronicle(chronicle.runs.maxOfOrNull { it.chronicleNumber } ?: 1)
    val ink = LocalInk.current
    val snack = LocalSnack.current
    val scope = rememberCoroutineScope()
    val padding = Modifier
        .padding(horizontal = PageGutter)
        .padding(top = 24.dp, bottom = contentPadding.calculateBottomPadding() + 32.dp)

    ReadingColumn(Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding())) {
        val ending = content?.endingById?.get(endingId)
        when {
            status == LoadStatus.LOADING || content == null -> Column(padding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SkeletonBlock(220.dp, 34.dp)
                repeat(8) { SkeletonBlock(null, 16.dp) }
            }
            ending == null -> EmptyState(
                title = "This ending could not be opened.",
                body = null,
                actionLabel = "Return to the ledger",
                onAction = onReturnToLedger,
                modifier = padding,
            )
            else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).then(padding)) {
                Text("The last page", style = MaterialTheme.typography.labelMedium, color = ink.fadedInk)
                Spacer(Modifier.height(6.dp))
                Text(ending.title, style = MaterialTheme.typography.headlineMedium, color = ink.ironGall, modifier = Modifier.semantics { heading() })
                Spacer(Modifier.height(18.dp))
                for (p in ending.passage) {
                    Text(p, style = MaterialTheme.typography.bodyLarge, color = ink.ironGall, modifier = Modifier.padding(bottom = 14.dp))
                }
                Spacer(Modifier.height(12.dp))
                Hairline()
                Spacer(Modifier.height(20.dp))
                Text("Credits", style = MaterialTheme.typography.titleLarge, color = ink.ironGall, modifier = Modifier.semantics { heading() })
                Spacer(Modifier.height(10.dp))
                content!!.credits.forEachIndexed { i, line ->
                    Text(
                        line,
                        // The wordmark is set in Alegreya: the Gotisch capital I reads as a D on its own.
                        style = if (i == 0) MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold) else MaterialTheme.typography.bodyMedium,
                        color = if (i == 0) ink.ironGall else ink.fadedInk,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                Spacer(Modifier.height(24.dp))
                InkButton("Begin $next", onClick = {
                    scope.launch {
                        repo.beginSecondChronicle()
                        snack("Began $next")
                        onReturnToLedger()
                    }
                })
                Spacer(Modifier.height(4.dp))
                InkTextButton("Return to the ledger", onReturnToLedger)
            }
        }
    }
}
