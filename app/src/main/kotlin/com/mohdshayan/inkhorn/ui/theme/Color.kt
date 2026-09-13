package com.mohdshayan.inkhorn.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/*
 * Verdigris and iron-gall, from BLUEPRINT.md section 7. Screens read these through
 * MaterialTheme.colorScheme or LocalInk, never as hex. LightBackground and DarkBackground match
 * the window background in colors.xml so the first frame agrees with Compose.
 */

// Light
val LightBackground = Color(0xFFE9EEE8) // Vellum
val LightLeaf = Color(0xFFF5F7F2)
val LightIronGall = Color(0xFF1C2233)
val LightFadedInk = Color(0xFF4F5A6E)
val LightVerdigris = Color(0xFF1F7466)
val LightRubric = Color(0xFFA33A2C)

// Dark
val DarkBackground = Color(0xFF131A1B) // Vellum
val DarkLeaf = Color(0xFF1B2426)
val DarkIronGall = Color(0xFFE2E8E1)
val DarkFadedInk = Color(0xFF9AA8A3)
val DarkVerdigris = Color(0xFF62B8A8)
val DarkRubric = Color(0xFFE48A78)

// True black swaps Vellum and Leaf; everything else is the dark set.
val TrueBlackBackground = Color(0xFF050707)
val TrueBlackLeaf = Color(0xFF101617)

/** The six named tokens plus the derived divider, for places Material 3 does not model. */
@Immutable
data class InkColors(
    val vellum: Color,
    val leaf: Color,
    val ironGall: Color,
    val fadedInk: Color,
    val verdigris: Color,
    val rubric: Color,
    /** Label colour on a Verdigris button: Leaf on light, Vellum on dark. */
    val onVerdigris: Color,
    val isDark: Boolean,
) {
    /** IronGall at 12 percent. */
    val divider: Color get() = ironGall.copy(alpha = 0.12f)
}

val LightInk = InkColors(LightBackground, LightLeaf, LightIronGall, LightFadedInk, LightVerdigris, LightRubric, LightLeaf, false)
val DarkInk = InkColors(DarkBackground, DarkLeaf, DarkIronGall, DarkFadedInk, DarkVerdigris, DarkRubric, DarkBackground, true)
val TrueBlackInk = DarkInk.copy(vellum = TrueBlackBackground, leaf = TrueBlackLeaf, onVerdigris = TrueBlackBackground)

val LocalInk = staticCompositionLocalOf { LightInk }
