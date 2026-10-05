package com.buyorbye.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import java.util.Locale
import kotlin.math.abs

val BuyGreen = Color(0xFF3DDC84)
val ByeAmber = Color(0xFFFFB020)

private val colors = darkColorScheme(
    primary = Color(0xFFF2F2F2),
    onPrimary = Color(0xFF111111),
    background = Color(0xFF111111),
    onBackground = Color(0xFFF2F2F2),
    surface = Color(0xFF111111),
    onSurface = Color(0xFFF2F2F2),
    surfaceVariant = Color(0xFF1E1E1E),
    onSurfaceVariant = Color(0xFFA8A8A8),
    surfaceContainerHighest = Color(0xFF242424),
    outline = Color(0xFF3A3A3A),
)

/** Minimal, dark-only palette: color is reserved for the verdict. */
@Composable
fun BuyOrByeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, content = content)
}

fun money(d: Double): String = (if (d < 0) "-" else "") + "$" + "%.2f".format(Locale.US, abs(d))
fun miles(d: Double): String = "%.1f mi".format(Locale.US, d)
fun minutes(d: Double): String = "%.0f min".format(Locale.US, d)
