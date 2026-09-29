package com.example.my_mpesa_tracker.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.my_mpesa_tracker.data.db.AppDatabase
import com.example.my_mpesa_tracker.data.model.MpesaTransaction
import com.example.my_mpesa_tracker.data.model.TransactionType
import com.example.my_mpesa_tracker.util.SpendingStats
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

// ── Money Tip — one card, changes daily, grounded in real numbers where the ──
// ── data supports it rather than staying purely generic advice.            ──

private fun buildTipPool(stats: SpendingStats, transactions: List<MpesaTransaction>): List<String> {
    val tips = mutableListOf(
        "The 50/30/20 rule: roughly 50% needs, 30% wants, 20% savings or debt repayment. Most budgets that fail skip the third bucket entirely.",
        "M-Pesa transaction costs are a real expense, not a rounding error. Consolidating small frequent transfers into fewer, larger ones can meaningfully cut what you pay in fees over a year.",
        "An emergency fund of 3–6 months' expenses is one of the strongest predictors of lower financial stress in most research — more than income level.",
        "Net worth — what you own minus what you owe — matters more than income alone. Two people earning the same can be moving in opposite financial directions.",
        "Fuliza and similar overdraft facilities charge interest daily. Worth treating as a last resort rather than a buffer — the cost compounds faster than it looks."
    )

    if (stats.totalSpent > 0) {
        stats.byCategory.maxByOrNull { it.value }?.let { (category, amount) ->
            val pct = (amount / stats.totalSpent * 100).toInt()
            if (pct >= 40) {
                tips += "$category makes up $pct% of your spending this period. Not automatically a problem — worth asking if it's a choice or a default."
            }
        }

        val totalCosts = transactions.filter { it.isDebit }.sumOf { it.transactionCost }
        if (totalCosts > 0) {
            val costPct = totalCosts / stats.totalSpent * 100
            if (costPct >= 1.0) {
                tips += "You've paid ${formatKsh(totalCosts)} in M-Pesa transaction costs this period — about ${"%.1f".format(costPct)}% of what you spent."
            }
        }
    }

    if (stats.totalReceived > 0) {
        if (stats.netFlow > 0) {
            val keptPct = (stats.netFlow / stats.totalReceived * 100).toInt()
            tips += "You kept $keptPct% of what came in this period. The 20%-savings guideline puts the bar around there, worth tracking over time."
        } else if (stats.netFlow < 0) {
            tips += "You spent more than you received this period. Not always a problem, but worth knowing if it is a one-off or a pattern."
        }
    }

    if (transactions.any { it.type == TransactionType.FULIZA }) {
        tips += "You used Fuliza this period. Overdraft interest is one of the more expensive forms of short-term credit, worth checking if it is becoming routine rather than an emergency tool."
    }

    return tips
}

@Composable
fun MoneyTipCard(stats: SpendingStats, transactions: List<MpesaTransaction>) {
    val tip = remember(stats, transactions) {
        val pool = buildTipPool(stats, transactions)
        pool[LocalDate.now().dayOfYear % pool.size]
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("💡", fontSize = 16.sp)
                Text("Money Tip", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Text(tip, color = TextSecondary, fontSize = 13.sp, lineHeight = 19.sp)
        }
    }
}

// ── Previous-period net flow, for the "vs last period" delta on the hero card. ──
// ── Mirrors TimeRangeHelper's own boundary conventions (Sunday-start week,    ──
// ── calendar month/year) shifted back one unit, rather than a rolling window, ──
// ── so "last month" means the same thing here as it does everywhere else.    ──

private fun previousPeriodRange(period: Period): Pair<Long, Long>? {
    val zone = ZoneId.systemDefault()
    return when (period) {
        Period.TODAY -> {
            val yesterday = LocalDate.now().minusDays(1)
            yesterday.atStartOfDay(zone).toInstant().toEpochMilli() to
                    yesterday.atTime(LocalTime.MAX).atZone(zone).toInstant().toEpochMilli()
        }
        Period.WEEK -> {
            val thisWeekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
            val lastWeekStart = thisWeekStart.minusWeeks(1)
            lastWeekStart.atStartOfDay(zone).toInstant().toEpochMilli() to
                    lastWeekStart.plusDays(6).atTime(LocalTime.MAX).atZone(zone).toInstant().toEpochMilli()
        }
        Period.MONTH -> {
            val firstOfThisMonth = LocalDate.now().with(TemporalAdjusters.firstDayOfMonth())
            val firstOfLastMonth = firstOfThisMonth.minusMonths(1)
            firstOfLastMonth.atStartOfDay(zone).toInstant().toEpochMilli() to
                    firstOfLastMonth.with(TemporalAdjusters.lastDayOfMonth()).atTime(LocalTime.MAX).atZone(zone).toInstant().toEpochMilli()
        }
        Period.YEAR -> {
            val firstOfThisYear = LocalDate.now().with(TemporalAdjusters.firstDayOfYear())
            val firstOfLastYear = firstOfThisYear.minusYears(1)
            firstOfLastYear.atStartOfDay(zone).toInstant().toEpochMilli() to
                    firstOfLastYear.with(TemporalAdjusters.lastDayOfYear()).atTime(LocalTime.MAX).atZone(zone).toInstant().toEpochMilli()
        }
        Period.CUSTOM -> null
    }
}

/** Null while loading or for Period.CUSTOM, which has no natural "previous" range. */
@Composable
fun rememberPreviousPeriodNetFlow(period: Period): Double? {
    val context = LocalContext.current
    var previousNetFlow by remember(period) { mutableStateOf<Double?>(null) }

    LaunchedEffect(period) {
        val range = previousPeriodRange(period)
        if (range == null) {
            previousNetFlow = null
            return@LaunchedEffect
        }
        val (start, end) = range
        val all = AppDatabase.getInstance(context).transactionDao().allTransactions().first()
        previousNetFlow = all
            .filter { it.timestamp in start..end }
            .sumOf { if (it.isDebit) -it.amount else it.amount }
    }

    return previousNetFlow
}
