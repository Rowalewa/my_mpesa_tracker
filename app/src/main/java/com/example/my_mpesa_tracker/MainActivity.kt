package com.example.my_mpesa_tracker

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.my_mpesa_tracker.ui.dashboard.AppLockManager
import com.example.my_mpesa_tracker.ui.dashboard.AppLockScreen
import com.example.my_mpesa_tracker.ui.dashboard.CardDark
import com.example.my_mpesa_tracker.ui.dashboard.DashboardScreen
import com.example.my_mpesa_tracker.ui.dashboard.DashboardViewModel
import com.example.my_mpesa_tracker.ui.dashboard.MpesaGreen
import com.example.my_mpesa_tracker.ui.dashboard.ReportScreen
import com.example.my_mpesa_tracker.ui.dashboard.SurfaceDark
import com.example.my_mpesa_tracker.ui.dashboard.TextSecondary
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
        var selectedTab by remember { mutableIntStateOf(0) }

        LaunchedEffect(Unit) {
            activeVm = vm
//            vm.repairTimestampAndGapArtifacts()
            vm.repairCorruptedEntries()
            vm.resyncForSimTagging()
            vm.syncMpesaSms(force = false)
        }

        Scaffold(
            containerColor = SurfaceDark,
            bottomBar = {
                NavigationBar(containerColor = CardDark) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Icon(Icons.Default.Home, contentDescription = "Dashboard") },
                        label = { Text("Dashboard") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MpesaGreen,
                            selectedTextColor = MpesaGreen,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary,
                            indicatorColor = Color.Transparent
                        )
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Report") },
                        label = { Text("Report") },
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
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(SurfaceDark)
                    .padding(padding)
            ) {
                when (selectedTab) {
                    0 -> DashboardScreen(vm)
                    1 -> ReportScreen(vm)
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