package com.mohdshayan.inkhorn.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mohdshayan.inkhorn.ui.theme.FIGURES
import com.mohdshayan.inkhorn.ui.theme.LocalInk
import com.mohdshayan.inkhorn.ui.theme.LocalReducedMotion
import com.mohdshayan.inkhorn.ui.theme.RadiusMd
import com.mohdshayan.inkhorn.ui.theme.RadiusSm

/** The reading column: centred and capped at 600dp on wide screens. */
@Composable
fun ReadingColumn(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = 600.dp).fillMaxWidth(), content = content)
    }
}

val PageGutter = 20.dp

/** Whether taps give a light haptic; provided from the Haptics setting. */
val LocalHaptics = compositionLocalOf { true }

/** Shows a short message in the app's snackbar. */
val LocalSnack = compositionLocalOf<(String) -> Unit> { {} }

@Composable
fun rememberTapFeedback(): () -> Unit {
    val view = LocalView.current
    val on = LocalHaptics.current
    return { if (on) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) }
}

fun TextStyle.figures(): TextStyle = copy(fontFeatureSettings = FIGURES)

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier, thickness = 1.dp, color = LocalInk.current.divider)
}

@Composable
fun SectionHeading(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        color = LocalInk.current.ironGall,
        modifier = modifier.padding(top = 28.dp, bottom = 8.dp),
    )
}

/**
 * A full-width Verdigris button. [cooldown] is the fraction of the cooldown still to run: an ink
 * line along the bottom edge shrinks to nothing as it passes. Under reduced motion the line is
 * replaced by the seconds left, written in the detail slot.
 */
@Composable
fun InkButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
    enabled: Boolean = true,
    cooldown: Float = 0f,
    cooldownText: String? = null,
) {
    val ink = LocalInk.current
    val reduced = LocalReducedMotion.current
    val shape = RoundedCornerShape(RadiusSm)
    val cooling = cooldown > 0f
    val fill = if (enabled) ink.verdigris else Color.Transparent
    val labelColor = if (enabled) ink.onVerdigris else ink.ironGall
    val detailColor = if (enabled) ink.onVerdigris else ink.fadedInk
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .background(fill, shape)
            .then(if (enabled) Modifier else Modifier.border(BorderStroke(1.dp, ink.verdigris), shape))
            .semantics { if (!enabled) disabled() }
            .clickable(enabled = enabled && !cooling, role = Role.Button, onClick = onClick)
            .drawBehind {
                if (cooling && enabled && !reduced) {
                    val h = 3.dp.toPx()
                    drawRect(
                        color = ink.onVerdigris.copy(alpha = 0.85f),
                        topLeft = Offset(0f, size.height - h),
                        size = Size(size.width * cooldown.coerceIn(0f, 1f), h),
                    )
                }
            }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = labelColor, modifier = Modifier.weight(1f))
        val side = if (cooling && reduced && cooldownText != null) cooldownText else detail
        if (side != null) {
            Text(
                side,
                style = MaterialTheme.typography.labelMedium.figures(),
                color = detailColor,
                textAlign = TextAlign.End,
            )
        }
    }
}

/** A quieter 44dp bordered button for rows: Hire, Buy, Share. */
@Composable
fun InkSmallButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val ink = LocalInk.current
    val shape = RoundedCornerShape(RadiusSm)
    Box(
        modifier = modifier
            .heightIn(min = 44.dp)
            .widthIn(min = 72.dp)
            .background(if (enabled) ink.verdigris else Color.Transparent, shape)
            .border(BorderStroke(1.dp, if (enabled) ink.verdigris else ink.divider), shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (enabled) ink.onVerdigris else ink.fadedInk)
    }
}

/** A text-only 48dp button in Verdigris, for secondary actions. */
@Composable
fun InkTextButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = LocalInk.current.verdigris) {
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = color)
    }
}

/** A composed empty state: what is missing, what to do, one button. */
@Composable
fun EmptyState(title: String, body: String?, actionLabel: String?, onAction: (() -> Unit)?, modifier: Modifier = Modifier) {
    val ink = LocalInk.current
    Column(modifier.padding(horizontal = PageGutter, vertical = 48.dp), horizontalAlignment = Alignment.Start) {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = ink.ironGall)
        if (body != null) {
            Spacer(Modifier.height(8.dp))
            Text(body, style = MaterialTheme.typography.bodyLarge, color = ink.fadedInk)
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(24.dp))
            InkButton(actionLabel, onAction)
        }
    }
}

/** An error in the app's voice: what happened, and the buttons that fix it. */
@Composable
fun ErrorNote(text: String, modifier: Modifier = Modifier, actions: @Composable ColumnScope.() -> Unit = {}) {
    val ink = LocalInk.current
    Column(
        modifier
            .fillMaxWidth()
            .border(BorderStroke(1.dp, ink.rubric), RoundedCornerShape(RadiusMd))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = ink.ironGall)
        actions()
    }
}

/** A still grey block in the shape of the content it stands in for while loading. No shimmer. */
@Composable
fun SkeletonBlock(width: Dp?, height: Dp, modifier: Modifier = Modifier) {
    val ink = LocalInk.current
    Box(
        modifier
            .then(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth())
            .height(height)
            .background(ink.ironGall.copy(alpha = 0.08f), RoundedCornerShape(RadiusSm)),
    )
}
