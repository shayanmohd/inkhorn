package com.mohdshayan.inkhorn.ui.ledger

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mohdshayan.inkhorn.ui.theme.LocalInk
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The chapter's first letter in Grenze Gotisch, with a verdigris vine drawn around it in
 * proportion to [progress]. The vine is trimmed to the fraction on every frame from state, so it is
 * never an animation and reads the same with animations off.
 */
@Composable
fun GrowingInitial(letter: String, progress: Float, modifier: Modifier = Modifier, size: Dp = 108.dp) {
    val ink = LocalInk.current
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.displayLarge.copy(color = ink.ironGall)
    val layout = remember(letter, style) { measurer.measure(letter, style) }
    val fraction = progress.coerceIn(0f, 1f)
    Canvas(
        modifier
            .size(size)
            .semantics { contentDescription = "Letter $letter, chapter ${(fraction * 100).roundToInt()} percent done" },
    ) {
        val vine = vinePath(this)
        val measure = PathMeasure().apply { setPath(vine, false) }
        val length = measure.length
        val stroke = 2.2.dp.toPx()

        // The whole path, faint, so the unfinished part reads as unfinished.
        drawPath(vine, ink.ironGall.copy(alpha = 0.12f), style = Stroke(width = stroke * 0.7f, cap = StrokeCap.Round))

        if (fraction > 0f) {
            val grown = Path()
            measure.getSegment(0f, length * fraction, grown, true)
            drawPath(grown, ink.verdigris, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
            val leaves = 14
            for (i in 0 until leaves) {
                val t = (i + 0.6f) / leaves
                if (t > fraction) break
                val d = length * t
                val pos = measure.getPosition(d)
                val tan = measure.getTangent(d)
                val angle = atan2(tan.y, tan.x) * 180f / PI.toFloat() + if (i % 2 == 0) -40f else 220f
                leaf(pos, angle, ink.verdigris)
            }
        }

        val x = (this.size.width - layout.size.width) / 2f
        val y = (this.size.height - layout.size.height) / 2f + 2.dp.toPx()
        drawText(layout, topLeft = Offset(x, y))
    }
}

private fun DrawScope.leaf(at: Offset, angleDegrees: Float, color: androidx.compose.ui.graphics.Color) {
    val len = 7.dp.toPx()
    val wide = 3.2.dp.toPx()
    translate(at.x, at.y) {
        rotate(angleDegrees, pivot = Offset.Zero) {
            val p = Path().apply {
                moveTo(0f, 0f)
                quadraticTo(len * 0.5f, -wide, len, 0f)
                quadraticTo(len * 0.5f, wide, 0f, 0f)
                close()
            }
            drawPath(p, color)
        }
    }
}

/** A wandering stem up the left side, over the top, down the right, back along the foot, then a curl. */
private fun vinePath(scope: DrawScope): Path = with(scope) {
    val w = size.width
    val h = size.height
    val inset = 7.dp.toPx()
    val amp = 2.4.dp.toPx()
    val left = inset
    val right = w - inset
    val top = inset
    val bottom = h - inset
    val sideV = bottom - top
    val sideH = right - left
    val perimeter = 2 * (sideV + sideH)
    val path = Path()
    val steps = 220
    for (i in 0..steps) {
        val u = i / steps.toFloat() * 0.94f
        var d = u * perimeter
        val x: Float
        val y: Float
        val nx: Float
        val ny: Float
        when {
            d < sideV -> { x = left; y = bottom - d; nx = 1f; ny = 0f }
            d < sideV + sideH -> { d -= sideV; x = left + d; y = top; nx = 0f; ny = 1f }
            d < 2 * sideV + sideH -> { d -= sideV + sideH; x = right; y = top + d; nx = -1f; ny = 0f }
            else -> { d -= 2 * sideV + sideH; x = right - d; y = bottom; nx = 0f; ny = -1f }
        }
        val wiggle = amp * sin(u * 2f * PI.toFloat() * 7f)
        val px = x + nx * wiggle
        val py = y + ny * wiggle
        if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
    }
    // The curl: a shrinking spiral inside the lower left corner.
    val cx = left + 12.dp.toPx()
    val cy = bottom - 11.dp.toPx()
    val turns = 1.3f
    val curlSteps = 60
    for (i in 0..curlSteps) {
        val t = i / curlSteps.toFloat()
        val r = (9.dp.toPx()) * (1f - 0.75f * t)
        val a = (PI.toFloat() * 0.5f) + t * turns * 2f * PI.toFloat()
        path.lineTo(cx + r * cos(a), cy + r * sin(a))
    }
    path
}
