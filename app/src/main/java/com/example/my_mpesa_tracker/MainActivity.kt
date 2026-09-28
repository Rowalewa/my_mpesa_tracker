package com.example.my_mpesa_tracker

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.my_mpesa_tracker.ui.dashboard.AppLockManager
import com.example.my_mpesa_tracker.ui.dashboard.AppLockScreen
import com.example.my_mpesa_tracker.ui.dashboard.AutoBackupScheduler
import com.example.my_mpesa_tracker.ui.dashboard.CardDark
import com.example.my_mpesa_tracker.ui.dashboard.CustomDateRangeDialog
import com.example.my_mpesa_tracker.ui.dashboard.DashboardViewModel
import com.example.my_mpesa_tracker.ui.dashboard.Header
import com.example.my_mpesa_tracker.ui.dashboard.HomeScreen
import com.example.my_mpesa_tracker.ui.dashboard.InsightsScreen
import com.example.my_mpesa_tracker.ui.dashboard.MpesaGreen
import com.example.my_mpesa_tracker.ui.dashboard.Period
import com.example.my_mpesa_tracker.ui.dashboard.PeriodSelector
import com.example.my_mpesa_tracker.ui.dashboard.ReportScreen
import com.example.my_mpesa_tracker.ui.dashboard.Screen
import com.example.my_mpesa_tracker.ui.dashboard.SurfaceDark
import com.example.my_mpesa_tracker.ui.dashboard.TextSecondary
import com.example.my_mpesa_tracker.ui.dashboard.TransactionsScreen
import com.example.my_mpesa_tracker.ui.onboarding.OnboardingScreen

class MainActivity : FragmentActivity() {

    private var activeVm: DashboardViewModel? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted.values.any { it }) {
            activeVm?.syncMpesaSms(force = true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val prefs = getSharedPreferences("pesalyzer_prefs", Context.MODE_PRIVATE)

            var isUnlocked by remember {
                mutableStateOf(!AppLockManager.isLockEnabled(this))
            }
            var onboardingComplete by remember {
                mutableStateOf(prefs.getBoolean("onboarding_complete", false))
            }

            when {
                !isUnlocked -> {
                    AppLockScreen(onUnlocked = { isUnlocked = true })
                }
                !onboardingComplete -> {
                    OnboardingScreen(
                        onComplete = { monthlyBudget ->
                            prefs.edit().apply {
                                putBoolean("onboarding_complete", true)
                                if (monthlyBudget != null && monthlyBudget > 0) {
                                    putFloat("monthly_budget", monthlyBudget.toFloat())
                                }
                                apply()
                            }
                            onboardingComplete = true
                            requestSmsPermissionsIfNeeded()
                        }
                    )
                }
                else -> {
                    requestSmsPermissionsIfNeeded()
                    PesalyzerApp()
                }
            }
        }
    }

    @Composable
    fun PesalyzerApp() {
        val vm: DashboardViewModel = viewModel()
        val state by vm.uiState.collectAsState()
        var selectedScreen by remember { mutableStateOf(Screen.Home) }
        var showDatePicker by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            activeVm = vm
//            vm.repairTimestampAndGapArtifacts()
            vm.repairCorruptedEntries()
            vm.resyncForSimTagging()
            vm.syncMpesaSms(force = false)
            // Re-affirms the periodic job on every launch; a no-op if already scheduled
            // or if backup was never enabled.
            AutoBackupScheduler.scheduleIfEnabled(applicationContext)
        }

        if (showDatePicker) {
            CustomDateRangeDialog(
                onDismiss = { showDatePicker = false },
                onConfirm = { from, to ->
                    vm.setCustomRange(from, to)
                    showDatePicker = false
                }
            )
        }

        Scaffold(
            containerColor = SurfaceDark,
            bottomBar = {
                NavigationBar(containerColor = CardDark) {
                    Screen.entries.forEach { screen ->
                        NavigationBarItem(
                            selected = selectedScreen == screen,
                            onClick = { selectedScreen = screen },
                            icon = { Icon(screen.icon, contentDescription = screen.label) },
                            label = { Text(screen.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MpesaGreen,
                                selectedTextColor = MpesaGreen,
                                unselectedIconColor = TextSecondary,
                                unselectedTextColor = TextSecondary,
                                indicatorColor = Color.Transparent
                            )
                        )
                    }
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(SurfaceDark)
                    .padding(padding)
            ) {
                // Frozen header — shared by every tab, doesn't scroll with tab content.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Header(transactions = state.allTransactions)
                    PeriodSelector(
                        selected = state.selectedPeriod,
                        onSelect = { if (it == Period.CUSTOM) showDatePicker = true else vm.selectPeriod(it) }
                    )
                }
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                Box(modifier = Modifier.weight(1f)) {
                    when (selectedScreen) {
                        Screen.Home -> HomeScreen(vm)
                        Screen.Insights -> InsightsScreen(vm)
                        Screen.Transactions -> TransactionsScreen(vm)
                        Screen.Report -> ReportScreen(vm)
                    }
                }
            }
        }
    }

    private fun requestSmsPermissionsIfNeeded() {
        val needed = listOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS
        ).filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) permissionLauncher.launch(needed.toTypedArray())
    }
}