package com.mohdshayan.inkhorn.ui.settings

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import com.mohdshayan.inkhorn.ui.theme.RadiusLg
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mohdshayan.inkhorn.data.prefs.ThemeChoice
import com.mohdshayan.inkhorn.ui.components.Hairline
import com.mohdshayan.inkhorn.ui.components.InkTextButton
import com.mohdshayan.inkhorn.ui.components.LocalSnack
import com.mohdshayan.inkhorn.ui.components.PageGutter
import com.mohdshayan.inkhorn.ui.components.ReadingColumn
import com.mohdshayan.inkhorn.ui.components.SectionHeading
import com.mohdshayan.inkhorn.ui.theme.LocalInk
import com.mohdshayan.inkhorn.work.Notes
import kotlinx.coroutines.launch

/** A back row that sits under the status bar: 48dp target with a described icon, then the title. */
@Composable
fun BackHeader(title: String, onBack: () -> Unit) {
    val ink = LocalInk.current
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = ink.ironGall)
        }
        Spacer(Modifier.width(4.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, color = ink.ironGall, modifier = Modifier.semantics { heading() })
    }
}

@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenLicences: () -> Unit,
    onLoaded: () -> Unit,
    viewModel: SettingsViewModel = viewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val ink = LocalInk.current
    val context = LocalContext.current
    val snack = LocalSnack.current
    val scope = rememberCoroutineScope()
    var themeOpen by rememberSaveable { mutableStateOf(false) }
    var notesBlocked by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.setChapterNotes(granted)
        notesBlocked = !granted
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { viewModel.ask = viewModel.inspect(uri) }
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
            BackHeader("Settings", onBack)
            val s = settings
            if (s != null) {
                Column(Modifier.padding(horizontal = 8.dp)) {
                    SectionHeading("Reading")
                    SettingRow("Theme", s.theme.label, onClick = { themeOpen = true })
                    Hairline()
                    ToggleRow(
                        title = "Chapter notes",
                        body = "One note when a chapter finishes while the game is closed.",
                        checked = s.chapterNotes,
                        onChange = { on ->
                            if (on && Build.VERSION.SDK_INT >= 33 && !Notes.permissionGranted(context)) {
                                permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else if (on && !Notes.canPost(context)) {
                                notesBlocked = true
                            } else {
                                notesBlocked = false
                                viewModel.setChapterNotes(on)
                            }
                        },
                    )
                    if (notesBlocked) {
                        Text(
                            "Android is blocking Inkhorn’s notifications, so chapter notes stay off.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ink.rubric,
                        )
                        InkTextButton("Open notification settings", onClick = {
                            val intent = Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName)
                            context.startActivity(intent)
                        })
                    }
                    Hairline()
                    ToggleRow("Haptics", "A light tap under your finger on every action.", s.haptics, viewModel::setHaptics)

                    SectionHeading("Your save")
                    SettingRow("Send save file", "Share the whole game as a file, to keep or to load on another phone.", onClick = {
                        if (busy) return@SettingRow
                        busy = true
                        scope.launch {
                            try {
                                context.startActivity(viewModel.shareIntent())
                                snack("Save file sent")
                            } catch (_: Exception) {
                                snack("The save file could not be written. Free some storage and send it again.")
                            } finally {
                                busy = false
                            }
                        }
                    })
                    Hairline()
                    SettingRow("Load save file", "Replace this game with one from a save file.", onClick = {
                        pick.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                    })

                    SectionHeading("About")
                    SettingRow("Privacy policy", "Nothing is collected. Everything stays on this phone.", onClick = onOpenPrivacy)
                    Hairline()
                    SettingRow("Licences", "The two typefaces and their licence.", onClick = onOpenLicences)
                }
            }
        }
    }

    if (themeOpen && settings != null) {
        AlertDialog(
            onDismissRequest = { themeOpen = false },
            containerColor = ink.leaf,
            shape = RoundedCornerShape(RadiusLg),
            title = { Text("Theme", style = MaterialTheme.typography.headlineSmall, color = ink.ironGall) },
            text = {
                Column(Modifier.selectableGroup()) {
                    for (t in ThemeChoice.entries) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .selectable(selected = settings?.theme == t, role = Role.RadioButton) {
                                    viewModel.setTheme(t)
                                    themeOpen = false
                                },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = settings?.theme == t,
                                onClick = null,
                                colors = RadioButtonDefaults.colors(selectedColor = ink.verdigris, unselectedColor = ink.fadedInk),
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(t.label, style = MaterialTheme.typography.bodyLarge, color = ink.ironGall)
                        }
                    }
                }
            },
            confirmButton = { InkTextButton("Close", { themeOpen = false }) },
        )
    }

    when (val a = viewModel.ask) {
        is LoadAsk.Confirm -> AlertDialog(
            onDismissRequest = { viewModel.ask = null },
            containerColor = ink.leaf,
            shape = RoundedCornerShape(RadiusLg),
            title = { Text("Load save file", style = MaterialTheme.typography.headlineSmall, color = ink.ironGall) },
            text = { Text(a.summary, style = MaterialTheme.typography.bodyLarge, color = ink.ironGall) },
            confirmButton = {
                InkTextButton("Load save file", {
                    viewModel.ask = null
                    scope.launch {
                        if (viewModel.load(a.file)) {
                            snack("Save file loaded")
                            onLoaded()
                        } else {
                            viewModel.ask = LoadAsk.Problem("The save file could not be written to this phone. Free some storage and load it again.")
                        }
                    }
                })
            },
            dismissButton = { InkTextButton("Keep current run", { viewModel.ask = null }, color = ink.fadedInk) },
        )
        is LoadAsk.Problem -> AlertDialog(
            onDismissRequest = { viewModel.ask = null },
            containerColor = ink.leaf,
            shape = RoundedCornerShape(RadiusLg),
            title = { Text("Nothing was loaded", style = MaterialTheme.typography.headlineSmall, color = ink.ironGall) },
            text = { Text(a.message + " Choose a file sent from Inkhorn’s Send save file.", style = MaterialTheme.typography.bodyLarge, color = ink.ironGall) },
            confirmButton = {
                InkTextButton("Choose another file", {
                    viewModel.ask = null
                    pick.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                })
            },
            dismissButton = { InkTextButton("Close", { viewModel.ask = null }, color = ink.fadedInk) },
        )
        null -> Unit
    }
}

@Composable
private fun SettingRow(title: String, body: String, onClick: () -> Unit) {
    val ink = LocalInk.current
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = ink.ironGall)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = ink.fadedInk)
    }
}

@Composable
private fun ToggleRow(title: String, body: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val ink = LocalInk.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = ink.ironGall)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = ink.fadedInk)
        }
        Spacer(Modifier.width(16.dp))
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = ink.onVerdigris,
                checkedTrackColor = ink.verdigris,
                uncheckedThumbColor = ink.fadedInk,
                uncheckedTrackColor = ink.vellum,
                uncheckedBorderColor = ink.fadedInk,
            ),
        )
    }
}
