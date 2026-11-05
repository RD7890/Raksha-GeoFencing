package com.rohan.raksha.geofence.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.rohan.raksha.geofence.R

val RakshaFont = FontFamily(
    Font(R.font.raksha_bold, FontWeight.Normal),
    Font(R.font.raksha_bold, FontWeight.Medium),
    Font(R.font.raksha_bold, FontWeight.SemiBold),
    Font(R.font.raksha_bold, FontWeight.Bold),
    Font(R.font.raksha_bold, FontWeight.ExtraBold),
)

private fun style(size: Int, line: Int, weight: FontWeight, spacing: Double = 0.0) = TextStyle(
    fontFamily = RakshaFont, fontWeight = weight,
    fontSize = size.sp, lineHeight = line.sp, letterSpacing = spacing.sp
)

val Typography = Typography(
    headlineLarge  = style(32, 40, FontWeight.Bold),
    headlineMedium = style(24, 32, FontWeight.SemiBold),
    headlineSmall  = style(20, 28, FontWeight.SemiBold),
    titleLarge     = style(20, 28, FontWeight.SemiBold),
    titleMedium    = style(16, 24, FontWeight.SemiBold),
    titleSmall     = style(14, 20, FontWeight.Medium),
    bodyLarge      = style(16, 24, FontWeight.Normal),
    bodyMedium     = style(14, 20, FontWeight.Normal),
    bodySmall      = style(12, 16, FontWeight.Normal),
    labelLarge     = style(14, 20, FontWeight.Medium, 0.1),
    labelMedium    = style(12, 16, FontWeight.Medium, 0.5),
    labelSmall     = style(11, 16, FontWeight.Medium, 0.5),
)
