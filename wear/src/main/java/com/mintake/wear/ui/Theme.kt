package com.mintake.wear.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme
import com.mintake.shared.DoseState

val Mint = Color(0xFF5EE0B4)
val MintInk = Color(0xFF062A1E)
val Coral = Color(0xFFFF8F7E)
val Amber = Color(0xFFFFC766)
val Muted = Color(0xFFA3ABA8)
val Surface1 = Color(0xFF1A1D1F)
val Surface2 = Color(0xFF24292C)
val Track = Color(0xFF1E2326)
val TextPrimary = Color(0xFFF4F6F5)

fun stateColor(state: DoseState): Color = when (state) {
    DoseState.TAKEN, DoseState.DUE -> Mint
    DoseState.NOT_TAKEN -> Coral
    DoseState.SNOOZED -> Amber
    DoseState.UPCOMING -> Muted
}

@Composable
fun MintakeWearTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colors = Colors(
            primary = Mint,
            primaryVariant = Mint,
            secondary = Mint,
            secondaryVariant = Mint,
            background = Color.Black,
            surface = Surface1,
            error = Coral,
            onPrimary = MintInk,
            onSecondary = MintInk,
            onBackground = TextPrimary,
            onSurface = TextPrimary,
            onSurfaceVariant = Muted,
            onError = Color.Black,
        ),
        content = content,
    )
}
