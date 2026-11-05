package com.rohan.raksha.geofence.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Raksha "Pearl" palette: white, soft grey cards, ink text, #FF2541 brand red,
 * green = protected. Light only.  Read it with `Raksha.colors`.
 */
@Immutable
data class RakshaColors(
    val bg: Color,
    val card: Color,
    val cardHigh: Color,
    val border: Color,
    val text: Color,
    val textMuted: Color,
    val textFaint: Color,
    val rose: Color,
    val roseSoft: Color,
    val mint: Color,
    val mintSoft: Color,
    val amber: Color,
    val amberSoft: Color,
    val danger: Color,
    val onAccent: Color,
)

val BrandRed = Color(0xFFFF2541)

val PearlColors = RakshaColors(
    bg        = Color(0xFFFFFFFF),
    card      = Color(0xFFF6F6F8),
    cardHigh  = Color(0xFFECECF0),
    border    = Color(0xFFECECF0),
    text      = Color(0xFF0B0B0D),
    textMuted = Color(0xFF6E6E78),
    textFaint = Color(0xFF9D9DA8),
    rose      = BrandRed,
    roseSoft  = Color(0x1AFF2541),
    mint      = Color(0xFF16A34A),
    mintSoft  = Color(0x1A16A34A),
    amber     = Color(0xFFD98200),
    amberSoft = Color(0x1AD98200),
    danger    = Color(0xFFE11D2E),
    onAccent  = Color(0xFFFFFFFF),
)

val LocalRakshaColors = staticCompositionLocalOf { PearlColors }
