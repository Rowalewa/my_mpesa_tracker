package com.example.my_mpesa_tracker.ui.dashboard

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// ── Brand & surfaces ────────────────────────────────────────────────────────
// Relocated verbatim from the old DashboardScreen.kt. Kept in this package
// (not ui.theme) so every existing file that referenced these by same-package
// visibility keeps compiling unchanged.
val MpesaGreen = Color(0xFF00A550)
val MpesaGreenDark = Color(0xFF007A3C)
val SurfaceDark = Color(0xFF1A1A2E)
val CardDark = Color(0xFF16213E)
val TextSecondary = Color(0xFFB0B8C8)

// ── Semantic colors ─────────────────────────────────────────────────────────
// Named versions of hexes that were previously scattered inline. Used in the
// new Home/Insights/Transactions screens; existing cards are untouched for now.
val PositiveGreen = Color(0xFF4CAF50)
val PositiveGreenSoft = Color(0xFF90EE90)
val NegativeRed = Color(0xFFFF6B6B)
val NegativeRedSoft = Color(0xFFFFB3B3)
val NegativeRedDeep = Color(0xFF8B0000)

// ── Spacing scale ───────────────────────────────────────────────────────────
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

// ── Corner radius scale ─────────────────────────────────────────────────────
object Corners {
    val sm = 12.dp
    val md = 16.dp
    val lg = 24.dp
}
