package com.mohdshayan.inkhorn.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.mohdshayan.inkhorn.R

/*
 * Grenze Gotisch SemiBold for the initial and chapter titles only; Alegreya for everything read.
 * Both bundled under res/font, SIL Open Font License 1.1, licences in docs/.
 */
val Gotisch = FontFamily(Font(R.font.grenze_gotisch_semibold, FontWeight.SemiBold))

val Alegreya = FontFamily(
    Font(R.font.alegreya_regular, FontWeight.Normal),
    Font(R.font.alegreya_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.alegreya_medium, FontWeight.Medium),
    Font(R.font.alegreya_bold, FontWeight.Bold),
)

/** Lining, tabular figures for counts that change while you watch. */
const val FIGURES = "lnum, tnum"

val AppTypography = Typography(
    // The growing initial.
    displayLarge = TextStyle(fontFamily = Gotisch, fontWeight = FontWeight.SemiBold, fontSize = 72.sp, lineHeight = 72.sp),
    // Chapter and ending titles, the only Gotisch besides the initial.
    headlineMedium = TextStyle(fontFamily = Gotisch, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 34.sp),
    // Screen, sheet and dialog titles: read, not admired, so Alegreya.
    headlineSmall = TextStyle(fontFamily = Alegreya, fontWeight = FontWeight.Medium, fontSize = 24.sp, lineHeight = 30.sp),
    // Headings.
    titleLarge = TextStyle(fontFamily = Alegreya, fontWeight = FontWeight.Medium, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = Alegreya, fontWeight = FontWeight.Medium, fontSize = 17.sp, lineHeight = 23.sp),
    titleSmall = TextStyle(fontFamily = Alegreya, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp),
    // Body at a 1.5 line height.
    bodyLarge = TextStyle(fontFamily = Alegreya, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Alegreya, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontFamily = Alegreya, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    // Buttons and labels.
    labelLarge = TextStyle(fontFamily = Alegreya, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = Alegreya, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 18.sp),
    labelSmall = TextStyle(fontFamily = Alegreya, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 16.sp),
)
