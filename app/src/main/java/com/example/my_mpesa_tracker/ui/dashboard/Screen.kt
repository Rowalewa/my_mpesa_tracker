package com.example.my_mpesa_tracker.ui.dashboard

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The app's four bottom-nav destinations. Centralising this here (instead of raw
 * tab indices in MainActivity) is what lets the nav bar be generated from a list
 * rather than four hand-written NavigationBarItem blocks.
 */
enum class Screen(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Default.Home),
    Insights("Insights", Icons.Default.Insights),
    Transactions("Transactions", Icons.AutoMirrored.Filled.List),
    Report("Report", Icons.Default.Assessment)
}
