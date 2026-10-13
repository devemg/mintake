package com.mintake.mobile.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Mint = Color(0xFF5EE0B4)
val MintInk = Color(0xFF062A1E)
val Coral = Color(0xFFFF8F7E)
val Amber = Color(0xFFFFC766)
val Muted = Color(0xFFA3ABA8)
val Ink = Color(0xFF0E1113)
val NavSurface = Color(0xFF14171A)
val Surface1 = Color(0xFF1A1D1F)
val Surface2 = Color(0xFF24292C)
val Outline = Color(0xFF2E3438)
val TextPrimary = Color(0xFFF4F6F5)

private val MintakeColors = darkColorScheme(
    primary = Mint,
    onPrimary = MintInk,
    primaryContainer = Mint,
    onPrimaryContainer = MintInk,
    secondary = Mint,
    onSecondary = MintInk,
    secondaryContainer = Surface2,
    onSecondaryContainer = TextPrimary,
    background = Ink,
    onBackground = TextPrimary,
    surface = Ink,
    onSurface = TextPrimary,
    surfaceVariant = Surface1,
    onSurfaceVariant = Muted,
    surfaceContainer = Surface1,
    surfaceContainerHigh = Surface2,
    surfaceContainerHighest = Surface2,
    outline = Outline,
    outlineVariant = Outline,
    error = Coral,
    onError = Color.Black,
)

@Composable
fun MintakeMobileTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = MintakeColors, content = content)
}
