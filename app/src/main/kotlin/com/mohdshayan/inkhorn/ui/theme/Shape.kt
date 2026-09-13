package com.mohdshayan.inkhorn.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/*
 * The whole radius scale, and the rule for using it:
 *   RadiusSm  buttons and choice options
 *   RadiusMd  the away entry and snackbars
 *   RadiusLg  sheet top corners
 * Nothing in the app uses a corner radius that is not one of these.
 */
val RadiusSm = 4.dp
val RadiusMd = 8.dp
val RadiusLg = 16.dp

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(RadiusSm),
    small = RoundedCornerShape(RadiusSm),
    medium = RoundedCornerShape(RadiusMd),
    large = RoundedCornerShape(RadiusLg),
    extraLarge = RoundedCornerShape(topStart = RadiusLg, topEnd = RadiusLg),
)
