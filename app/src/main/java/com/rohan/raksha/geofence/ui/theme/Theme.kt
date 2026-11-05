package com.rohan.raksha.geofence.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** Access the palette: `Raksha.colors.rose`, `Raksha.colors.card`, ... */
object Raksha {
    val colors: RakshaColors
        @Composable get() = LocalRakshaColors.current
}

private fun schemeFor(c: RakshaColors) = lightColorScheme(
    primary = c.rose,
    onPrimary = c.onAccent,
    primaryContainer = c.cardHigh,
    onPrimaryContainer = c.rose,
    secondary = c.mint,
    onSecondary = c.onAccent,
    tertiary = c.amber,
    background = c.bg,
    onBackground = c.text,
    surface = c.card,
    onSurface = c.text,
    surfaceVariant = c.cardHigh,
    onSurfaceVariant = c.textMuted,
    outline = c.border,
    outlineVariant = c.border,
    error = c.danger,
    onError = c.onAccent,
)

@Composable
fun RakshaGeoFencingTheme(content: @Composable () -> Unit) {
    val palette = PearlColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Edge to edge: the map runs under transparent bars, screens pad themselves with insets.
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = true
            controller.isAppearanceLightNavigationBars = true
        }
    }
    CompositionLocalProvider(LocalRakshaColors provides palette) {
        MaterialTheme(colorScheme = schemeFor(palette), typography = Typography, content = content)
    }
}
