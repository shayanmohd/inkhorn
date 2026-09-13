package com.mohdshayan.inkhorn.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.unit.dp
import com.mohdshayan.inkhorn.di.ServiceLocator
import com.mohdshayan.inkhorn.ui.components.PageGutter
import com.mohdshayan.inkhorn.ui.components.ReadingColumn
import com.mohdshayan.inkhorn.ui.components.SkeletonBlock
import com.mohdshayan.inkhorn.ui.theme.LocalInk

/** A block of a text page: a heading, or a paragraph set as body text. */
private data class Block(val heading: Boolean, val text: AnnotatedString)

/** The bundled privacy policy, rendered from assets/privacy-policy.html in the app's own type. */
@Composable
fun PrivacyScreen(contentPadding: PaddingValues, onBack: () -> Unit) {
    TextPage("Privacy policy", contentPadding, onBack) {
        val html = ServiceLocator.contentLoader.asset("privacy-policy.html")
        val body = html.substringAfter("<body>").substringBefore("</body>")
        // One block per heading, paragraph or list item, so headings get their space above.
        Regex("<(h1|h2|p|li)[^>]*>(.*?)</\\1>", RegexOption.DOT_MATCHES_ALL).findAll(body).map { m ->
            Block(heading = m.groupValues[1].startsWith("h"), text = AnnotatedString.fromHtml(m.groupValues[2].replace("\n", " ").trim()))
        }.toList()
    }
}

/** The SIL Open Font License texts for both bundled typefaces, unwrapped into paragraphs. */
@Composable
fun LicencesScreen(contentPadding: PaddingValues, onBack: () -> Unit) {
    TextPage("Licences", contentPadding, onBack) {
        listOf("GrenzeGotisch" to "Grenze Gotisch", "Alegreya" to "Alegreya").flatMap { (file, name) ->
            val text = ServiceLocator.contentLoader.asset("licences/OFL-$file.txt").replace("\r", "")
            val paragraphs = text.split(Regex("\n\\s*\n"))
                .map { para -> para.lines().map { it.trim() }.filter { line -> line.isNotEmpty() && !line.all { it == '-' } }.joinToString(" ") }
                .filter { it.isNotEmpty() }
            listOf(Block(true, AnnotatedString(name))) + paragraphs.map { Block(false, AnnotatedString(it)) }
        }
    }
}

@Composable
private fun TextPage(title: String, contentPadding: PaddingValues, onBack: () -> Unit, load: suspend () -> List<Block>) {
    val ink = LocalInk.current
    var blocks by remember { mutableStateOf<List<Block>?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(title) {
        try {
            blocks = load()
        } catch (_: Exception) {
            failed = true
        }
    }
    ReadingColumn(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = contentPadding.calculateTopPadding())
                .verticalScroll(rememberScrollState())
                .padding(bottom = contentPadding.calculateBottomPadding() + 32.dp)
                .padding(horizontal = PageGutter - 8.dp),
        ) {
            BackHeader(title, onBack)
            Column(Modifier.padding(horizontal = 8.dp, vertical = 12.dp)) {
                when {
                    failed -> Text("This page could not be opened. Reinstalling Inkhorn restores it.", style = MaterialTheme.typography.bodyLarge, color = ink.rubric)
                    blocks == null -> repeat(10) { SkeletonBlock(null, 16.dp, Modifier.padding(vertical = 5.dp)) }
                    else -> blocks!!.forEachIndexed { i, b ->
                        Text(
                            b.text,
                            style = if (b.heading) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyMedium,
                            color = ink.ironGall,
                            modifier = Modifier
                                .padding(top = if (b.heading && i > 0) 24.dp else 0.dp, bottom = if (b.heading) 6.dp else 10.dp)
                                .then(if (b.heading) Modifier.semantics { heading() } else Modifier),
                        )
                    }
                }
            }
        }
    }
}
