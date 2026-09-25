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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.my_mpesa_tracker.data.model.MpesaTransaction
import com.example.my_mpesa_tracker.data.model.TransactionType
import com.example.my_mpesa_tracker.util.SpendingStats
import com.example.my_mpesa_tracker.util.filterByIncludedSims

// ── Insights: the deep-dive analytics — net worth, heatmap, goals, breakdowns, charts ──

@Composable
fun InsightsScreen(vm: DashboardViewModel = viewModel()) {
    val state by vm.uiState.collectAsState()

    if (state.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MpesaGreen)
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(SurfaceDark),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { NetWorthCard(transactions = state.allTransactions.filterByIncludedSims(LocalContext.current)) }

        item {
            SpendingHeatmapCard(
                transactions = state.allTransactions,
                onDaySelected = { date -> vm.setCustomRange(date, date) }
            )
        }

        item { GoalsCard() }

        item { SubcategoryBreakdownCard(transactions = state.allTransactions) }

        if (state.dailyChart.size > 1) {
            item { LineChartCard(state.dailyChart) }
        }

        item {
            BeautifulBarChartCard(
                incomeAmount = state.stats.totalReceived,
                expenseAmount = state.stats.totalSpent,
                dateLabel = state.dateLabel
            )
        }

        item { StatsGrid(state.stats) }

        item { InsightsCard(state.insights) }

        item { CategoryBreakdown(state.stats.byCategory) }

        item { BalanceTrackerCard(transactions = state.allTransactions.filterToCurrentSim()) }

        item { RecurringTransactionsCard(transactions = state.allTransactions) }

        item {
            InflowSenderBreakdown(
                transactions = state.allTransactions.filter { it.type == TransactionType.RECEIVE_MONEY }
            )
        }
    }
}

@Composable
fun InsightsCard(insights: InsightData) {
    if (insights.topSender.isBlank() && insights.topReceiver.isBlank()) return
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Insights", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)

            if (insights.mostUsedCategory.isNotBlank())
                InsightRow("🏆", "Most used category", insights.mostUsedCategory)
            if (insights.topSender.isNotBlank())
                InsightRow("📤", "Most sent to", insights.topSender)
            if (insights.topReceiver.isNotBlank())
                InsightRow("📥", "Most received from", insights.topReceiver)
            if (insights.busiestDay.isNotBlank())
                InsightRow("📅", "Busiest day", insights.busiestDay)
            if (insights.biggestSpendDay.isNotBlank())
                InsightRow("💸", "Highest spend day", insights.biggestSpendDay)
            if (insights.highestInflowDay.isNotBlank())
                InsightRow("💰", "Highest income day", insights.highestInflowDay)

            if (insights.topByCategory.isNotEmpty()) {
                HorizontalDivider(
                    Modifier,
                    DividerDefaults.Thickness,
                    color = Color.White.copy(alpha = 0.08f)
                )
                Text("Top per category", color = TextSecondary, fontSize = 12.sp)
                insights.topByCategory.entries.forEach { (cat, name) ->
                    if (name.isNotBlank()) InsightRow("·", cat, name)
                }
            }
        }
    }
}

@Composable
fun InsightRow(icon: String, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = 14.sp)
            Text(label, color = TextSecondary, fontSize = 13.sp)
        }
        Text(value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.widthIn(max = 180.dp), maxLines = 1)
    }
}

@Composable
fun StatsGrid(stats: SpendingStats) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Max", formatKsh(stats.maxTransaction), Modifier.weight(1f))
            StatCard("Min", formatKsh(stats.minTransaction), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Average", formatKsh(stats.avgTransaction), Modifier.weight(1f))
            StatCard("Transactions", stats.transactionCount.toString(), Modifier.weight(1f))
        }
    }
}

@Composable
fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier, shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(label, color = TextSecondary, fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
    }
}

@Composable
fun CategoryBreakdown(byCategory: Map<String, Double>) {
    if (byCategory.isEmpty()) return
    Card(
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Spending by Category", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.height(12.dp))
            val total = byCategory.values.sum()
            byCategory.entries.sortedByDescending { it.value }.forEach { (cat, amount) ->
                val pct = if (total > 0) (amount / total).toFloat() else 0f
                CategoryRow(cat, amount, pct)
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun CategoryRow(label: String, amount: Double, fraction: Float) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = TextSecondary, fontSize = 13.sp)
            Text(formatKsh(amount), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth().height(4.dp),
            color = MpesaGreen,
            trackColor = Color.White.copy(alpha = 0.1f)
        )
    }
}

@Composable
fun InflowSenderBreakdown(transactions: List<MpesaTransaction>) {
    val senderBreakdown = remember(transactions) {
        transactions
            .groupBy { it.counterparty }
            .mapValues { entry -> entry.value.sumOf { tx -> tx.amount } }
            .entries
            .sortedByDescending { it.value }
    }

    if (senderBreakdown.isEmpty()) return
    val totalInflow = remember(senderBreakdown) { senderBreakdown.sumOf { it.value } }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = "Inflow Breakdown by Sender",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            Spacer(Modifier.height(12.dp))

            senderBreakdown.forEach { (sender, amount) ->
                val fraction = if (totalInflow > 0) (amount / totalInflow).toFloat() else 0f

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(sender, color = TextSecondary, fontSize = 13.sp, maxLines = 1)
                        Text(
                            text = formatKsh(amount),
                            color = PositiveGreenSoft,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                        color = MpesaGreen,
                        trackColor = Color.White.copy(alpha = 0.1f)
                    )
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}
