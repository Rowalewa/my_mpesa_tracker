package com.example.my_mpesa_tracker.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.my_mpesa_tracker.data.model.MpesaTransaction
import com.example.my_mpesa_tracker.util.SpendingStats
import com.example.my_mpesa_tracker.util.filterByIncludedSims
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.time.Duration.Companion.milliseconds

// ── Home: curated snapshot only — status, period, net flow, health, urgent alerts ──

@Composable
fun HomeScreen(vm: DashboardViewModel = viewModel()) {
    val state by vm.uiState.collectAsState()
    var showTransactionDetail by remember { mutableStateOf<MpesaTransaction?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        vm.syncMpesaSms()
    }

    if (state.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = MpesaGreen)
                Spacer(Modifier.height(12.dp))
                Text("Loading M-Pesalyzer...", color = TextSecondary, fontSize = 13.sp)
            }
        }
        return
    }

    showTransactionDetail?.let { tx ->
        TransactionDetailDialog(tx = tx, onDismiss = { showTransactionDetail = null })
    }

    val previousNetFlow = rememberPreviousPeriodNetFlow(state.selectedPeriod)

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            isRefreshing = true
            vm.refresh()
            scope.launch {
                delay(800.milliseconds)
                isRefreshing = false
            }
        },
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().background(SurfaceDark),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { SimDiagnosticsCard(vm = vm) }

            if (state.isImporting) {
                item { ImportingHistoryState() }
            }

            if (state.dateLabel.isNotEmpty()) {
                item {
                    Text(
                        text = state.dateLabel,
                        color = MpesaGreen,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        fontFamily = FontFamily.Serif,
                        fontStyle = FontStyle.Italic
                    )
                }
            }

            item { NetFlowCard(state.stats, previousNetFlow) }

            item { MoneyTipCard(stats = state.stats, transactions = state.allTransactions) }

            if (state.selectedPeriod == Period.MONTH) {
                item { ForecastCard(thisMonthTransactions = state.allTransactions.filterByIncludedSims(LocalContext.current)) }
            }

            item { FinancialHealthCard(transactions = state.allTransactions, dailyTotals = state.dailyChart) }

            // Urgent-only alerts — deep analytics live in the Insights tab now
            item {
                DetectedGapsCard(
                    transactions = state.allTransactions,
                    onTransactionClick = { tx -> showTransactionDetail = tx }
                )
            }

            item {
                AnomalyAlertsCard(
                    transactions = state.allTransactions,
                    onTransactionClick = { tx -> showTransactionDetail = tx }
                )
            }

            item {
                val context = LocalContext.current
                val alerts = remember(state.allTransactions) {
                    computeBudgetAlerts(
                        context = context,
                        monthlyBudget = BudgetManager.getMonthlyBudget(context),
                        totalSpent = state.stats.totalSpent,
                        transactions = state.allTransactions
                    )
                }
                BudgetAlertsCard(alerts = alerts)
            }
        }
    }
}

@Composable
fun NetFlowCard(stats: SpendingStats, previousNetFlow: Double? = null) {
    val isPositive = stats.netFlow >= 0
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (isPositive) MpesaGreenDark else NegativeRedDeep)
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("Net Cash Flow", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            Text(formatKsh(stats.netFlow), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 32.sp)
            if (previousNetFlow != null) {
                val delta = stats.netFlow - previousNetFlow
                val improved = delta >= 0
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        if (improved) "↑" else "↓",
                        color = if (improved) PositiveGreenSoft else NegativeRedSoft,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Text(
                        "${formatKsh(kotlin.math.abs(delta))} vs last period",
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 12.sp
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                FlowItem("In", stats.totalReceived, PositiveGreenSoft)
                FlowItem("Out", stats.totalSpent, NegativeRedSoft)
            }
        }
    }
}

@Composable
fun FlowItem(label: String, amount: Double, color: Color) {
    Column {
        Text(label, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
        Text(formatKsh(amount), color = color, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}
