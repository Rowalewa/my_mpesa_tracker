package com.example.my_mpesa_tracker.ui.dashboard

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import com.example.my_mpesa_tracker.data.model.MpesaTransaction
import com.example.my_mpesa_tracker.data.model.TransactionType
import com.example.my_mpesa_tracker.util.SpendingStats
import com.example.my_mpesa_tracker.util.label
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ── Report Engine ─────────────────────────────────────────────────────────────

data class SpendingReport(
    val periodLabel: String,
    val summary: String,
    val patterns: List<String>,
    val alerts: List<String>,
    val highlights: List<String>,
    val tableRows: List<TableRow>, // 100% Preserved original name
    val inflowRows: List<TableRow>, // Clean addition for income tracking
    val plainText: String
)

data class TableRow( // 100% Preserved original data class name
    val category: String,
    val amount: Double,
    val count: Int,
    val percentage: Double
)

object ReportEngine {

    fun generate(
        stats: SpendingStats,
        transactions: List<MpesaTransaction>,
        periodLabel: String
    ): SpendingReport {

        val debits = transactions.filter { it.isDebit }
        val credits = transactions.filter { !it.isDebit }

        // ── Table (Spending Breakdown) ────────────────────────────────────
        val tableRows = TransactionType.entries.mapNotNull { type ->
            val txs = debits.filter { it.type == type }
            if (txs.isEmpty()) return@mapNotNull null
            val total = txs.sumOf { it.amount }
            val pct = if (stats.totalSpent > 0) (total / stats.totalSpent) * 100 else 0.0
            TableRow(type.label(), total, txs.size, pct)
        }.sortedByDescending { it.amount }

        // ── Inflow Rows (Income Breakdown by Sender Name) ──────────────────
        val inflowRows = credits.groupBy { it.counterparty }.map { (sender, txs) ->
            val total = txs.sumOf { it.amount }
            val pct = if (stats.totalReceived > 0) (total / stats.totalReceived) * 100 else 0.0
            TableRow(sender, total, txs.size, pct)
        }.sortedByDescending { it.amount }

        // ── Summary sentence ──────────────────────────────────────────────
        val summary = buildString {
            append("During $periodLabel, you made ${stats.transactionCount} transaction")
            if (stats.transactionCount != 1) append("s")
            append(". You spent ${formatKsh(stats.totalSpent)} and received ${formatKsh(stats.totalReceived)}. ")
            val flow = stats.netFlow
            if (flow >= 0) append("Net flow is positive at ${formatKsh(flow)}.")
            else append("You are ${formatKsh(-flow)} in the red for this period.")
        }

        // ── Pattern commentary ────────────────────────────────────────────
        val patterns = mutableListOf<String>()

        tableRows.firstOrNull()?.let { top ->
            patterns.add("${top.category} dominates your spending at ${"%.2f".format(top.percentage)}% of total outflow.")
        }

        val sendMoneyRow = tableRows.find { it.category == "Send Money" }
        if (sendMoneyRow != null && sendMoneyRow.percentage > 50) {
            patterns.add("Over half your spending goes to sending money directly to people — consider if all transfers are necessary.")
        }

        val busiestHour = debits.groupBy { tx ->
            val cal = Calendar.getInstance()
            cal.timeInMillis = tx.timestamp
            cal.get(Calendar.HOUR_OF_DAY)
        }.maxByOrNull { it.value.size }?.key
        if (busiestHour != null) {
            val timeLabel = when {
                busiestHour < 12 -> "${busiestHour}am"
                busiestHour == 12 -> "12pm"
                else -> "${busiestHour - 12}pm"
            }
            patterns.add("You spend most frequently around $timeLabel.")
        }

        if (stats.maxTransaction > 0 && stats.avgTransaction > 0) {
            val ratio = stats.maxTransaction / stats.avgTransaction
            if (ratio > 3) {
                patterns.add("Your largest transaction (${formatKsh(stats.maxTransaction)}) is ${ratio.toInt()}x your average — one big payment is skewing your spend profile.")
            }
        }

        if (stats.transactionCount > 0) {
            val avgPerDay = stats.transactionCount.toDouble()
            if (avgPerDay >= 5) patterns.add("You are transacting frequently — averaging ${stats.transactionCount} movements this period.")
        }

        val ziidiRow = tableRows.find { it.category == "Ziidi" }
        if (ziidiRow != null) {
            patterns.add("Ziidi deductions account for ${formatKsh(ziidiRow.amount)}.")
        }

        val repeatRecipients = debits.groupBy { it.counterparty }
            .filter { it.value.size >= 2 }
            .maxByOrNull { it.value.size }
        if (repeatRecipients != null) {
            patterns.add("${repeatRecipients.key} appears ${repeatRecipients.value.size} times in your transactions this period.")
        }

        // ── Alerts ────────────────────────────────────────────────────────
        val alerts = mutableListOf<String>()

        if (stats.netFlow < 0) {
            alerts.add("⚠️ Outflow exceeds inflow by ${formatKsh(-stats.netFlow)}. You spent more than you received this period.")
        }

        if (stats.totalSpent > 0 && stats.totalReceived == 0.0) {
            alerts.add("⚠️ No income recorded this period — only outflows detected.")
        }

        val airtime = tableRows.find { it.category == "Airtime" }
        if (airtime != null && airtime.percentage > 20) {
            alerts.add("⚠️ Airtime purchases are ${"%.2f".format(airtime.percentage)}% of your spend.")
        }

        // ── Highlights ────────────────────────────────────────────────────
        val highlights = mutableListOf<String>()

        stats.largestTransaction?.let {
            highlights.add("💸 Largest transaction: ${formatKsh(it.amount)} to ${it.counterparty} on ${formatDate(it.timestamp)}.")
        }

        stats.smallestTransaction?.let {
            highlights.add("🪙 Smallest transaction: ${formatKsh(it.amount)} to ${it.counterparty}.")
        }

        credits.maxByOrNull { it.amount }?.let {
            highlights.add("📥 Largest receipt: ${formatKsh(it.amount)} from ${it.counterparty} on ${formatDate(it.timestamp)}.")
        }

        val plainText = buildPlainText(periodLabel, summary, stats, tableRows, inflowRows, patterns, alerts, highlights)

        return SpendingReport(
            periodLabel = periodLabel,
            summary = summary,
            patterns = patterns,
            alerts = alerts,
            highlights = highlights,
            tableRows = tableRows,
            inflowRows = inflowRows,
            plainText = plainText
        )
    }

    private fun buildPlainText(
        period: String,
        summary: String,
        stats: SpendingStats,
        tableRows: List<TableRow>,
        inflowRows: List<TableRow>,
        patterns: List<String>,
        alerts: List<String>,
        highlights: List<String>
    ): String = buildString {
        appendLine("═══════════════════════════")
        appendLine("  M-PESA SPENDING REPORT")
        appendLine("  $period")
        appendLine("═══════════════════════════")
        appendLine()
        appendLine("SUMMARY")
        appendLine(summary)
        appendLine()
        appendLine("BREAKDOWN BY CATEGORY")
        tableRows.forEach { row ->
            appendLine("  ${row.category.padEnd(16)} ${formatKsh(row.amount).padStart(12)}  (${row.percentage.toInt()}%,  ${row.count} tx)")
        }
        if (inflowRows.isNotEmpty()) {
            appendLine()
            appendLine("INFLOW BREAKDOWN BY SENDER")
            inflowRows.forEach { row ->
                appendLine("  ${row.category.padEnd(16)} ${formatKsh(row.amount).padStart(12)}  (${row.percentage.toInt()}%,  ${row.count} tx)")
            }
        }
        appendLine()
        appendLine("STATS")
        appendLine("  Total Spent:    ${formatKsh(stats.totalSpent)}")
        appendLine("  Total Received: ${formatKsh(stats.totalReceived)}")
        appendLine("  Net Flow:       ${formatKsh(stats.netFlow)}")
        appendLine("  Max:            ${formatKsh(stats.maxTransaction)}")
        appendLine("  Min:            ${formatKsh(stats.minTransaction)}")
        appendLine("  Average:        ${formatKsh(stats.avgTransaction)}")
        appendLine("  Transactions:   ${stats.transactionCount}")
        if (alerts.isNotEmpty()) {
            appendLine()
            appendLine("ALERTS")
            alerts.forEach { appendLine("  $it") }
        }
        if (patterns.isNotEmpty()) {
            appendLine()
            appendLine("PATTERNS")
            patterns.forEach { appendLine("  • $it") }
        }
        if (highlights.isNotEmpty()) {
            appendLine()
            appendLine("HIGHLIGHTS")
            highlights.forEach { appendLine("  $it") }
        }
        appendLine()
        appendLine("Generated by M-Pesalyzer")
    }

    private fun formatDate(epoch: Long): String {
        val sdf = SimpleDateFormat("d MMM yyyy", Locale.US) // Maintained 4-digit year rule
        return sdf.format(Date(epoch))
    }
}

// ── Report Screen ─────────────────────────────────────────────────────────────

@Composable
fun ReportScreen(vm: DashboardViewModel) {
    val state by vm.uiState.collectAsState()
//    val clipboardManager = LocalClipboard.current
//    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    val totalCosts = state.filteredTransactions.filter { it.isDebit }.sumOf { it.transactionCost }
    val context = LocalContext.current
    val clipboardManager = remember {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    }

    val report = remember(state.filteredTransactions, state.stats, state.dateLabel) {
        ReportEngine.generate(state.stats, state.filteredTransactions, state.dateLabel)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(SurfaceDark),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            var exportMessage by remember { mutableStateOf("") }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Spending Report", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Text(report.periodLabel, color = MpesaGreen, fontSize = 13.sp)
                }
                Row {
                    // Copy plain text to clipboard
                    IconButton(onClick = {
                        clipboardManager.setPrimaryClip(
                            android.content.ClipData.newPlainText("Pesalyzer Report", report.plainText)
                        )
                        copied = true
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy report", tint = MpesaGreen)
                    }
                    // Export as CSV
                    IconButton(onClick = {
                        val file = CsvExporter.export(context, state.filteredTransactions, state.dateLabel)
                        if (file != null) {
                            CsvExporter.share(context, file)
                        } else {
                            exportMessage = "Export failed"
                        }
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Export CSV", tint = MpesaGreen)
                    }
                }
            }

            if (exportMessage.isNotBlank()) {
                Text(exportMessage, color = Color(0xFFFF6B6B), fontSize = 12.sp)
            }
        }

        if (copied) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MpesaGreenDark)
                ) {
                    Text(
                        "✓ Report copied to clipboard",
                        color = Color.White,
                        modifier = Modifier.padding(12.dp),
                        fontSize = 13.sp
                    )
                }
            }
        }

        // Summary Card
        item {
            ReportCard(title = "Summary") {
                Text(report.summary, color = TextSecondary, fontSize = 14.sp, lineHeight = 22.sp)
            }
        }

        // Spending Breakdown Table (tableRows)
        item {
            ReportCard(title = "Breakdown by Category") {
                Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                        Text("Category", color = TextSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        Text("Amount", color = TextSecondary, fontSize = 12.sp, modifier = Modifier.width(80.dp))
                        Text("Tx", color = TextSecondary, fontSize = 12.sp, modifier = Modifier.width(30.dp))
                        Text("%", color = TextSecondary, fontSize = 12.sp, modifier = Modifier.width(46.dp))
                    }
                    HorizontalDivider(
                        Modifier,
                        DividerDefaults.Thickness,
                        color = Color.White.copy(alpha = 0.08f)
                    )
                    Spacer(Modifier.height(8.dp))
                    report.tableRows.forEach { row ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(row.category, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            Text(formatKsh(row.amount), color = Color(0xFFFF6B6B), fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.width(80.dp))
                            Text("${row.count}", color = TextSecondary, fontSize = 13.sp, modifier = Modifier.width(30.dp))
                            Text("${"%.2f".format(row.percentage)}%", color = TextSecondary, fontSize = 13.sp, modifier = Modifier.width(46.dp))
                        }
                        LinearProgressIndicator(
                            progress = { (row.percentage / 100).toFloat() },
                            modifier = Modifier.fillMaxWidth().height(2.dp).padding(bottom = 2.dp),
                            color = MpesaGreen,
                            trackColor = Color.White.copy(alpha = 0.06f)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(
                        Modifier,
                        DividerDefaults.Thickness,
                        color = Color.White.copy(alpha = 0.08f)
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth()) {
                        Text("Total Spending", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Text(formatKsh(state.stats.totalSpent), color = Color(0xFFFF6B6B), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }

        // New Itemized Income Breakdown Table (inflowRows)
        if (report.inflowRows.isNotEmpty()) {
            item {
                ReportCard(title = "Inflow Breakdown by Sender") {
                    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                            Text("Sender / Source", color = TextSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                            Text("Received", color = TextSecondary, fontSize = 12.sp, modifier = Modifier.width(80.dp))
                            Text("Tx", color = TextSecondary, fontSize = 12.sp, modifier = Modifier.width(30.dp))
                            Text("%", color = TextSecondary, fontSize = 12.sp, modifier = Modifier.width(46.dp))
                        }
                        HorizontalDivider(
                            Modifier,
                            DividerDefaults.Thickness,
                            color = Color.White.copy(alpha = 0.08f)
                        )
                        Spacer(Modifier.height(8.dp))
                        report.inflowRows.forEach { row ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(row.category, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1)
                                Text(formatKsh(row.amount), color = Color(0xFF4CAF50), fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.width(80.dp))
                                Text("${row.count}", color = TextSecondary, fontSize = 13.sp, modifier = Modifier.width(30.dp))
                                Text("${"%.2f".format(row.percentage)}%", color = TextSecondary, fontSize = 13.sp, modifier = Modifier.width(46.dp))
                            }
                            LinearProgressIndicator(
                                progress = { (row.percentage / 100).toFloat() },
                                modifier = Modifier.fillMaxWidth().height(2.dp).padding(bottom = 2.dp),
                                color = Color(0xFF4CAF50), // Visual balance for tracking income paths
                                trackColor = Color.White.copy(alpha = 0.06f)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider(
                            Modifier,
                            DividerDefaults.Thickness,
                            color = Color.White.copy(alpha = 0.08f)
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth()) {
                            Text("Total Received", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            Text(formatKsh(state.stats.totalReceived), color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        // Alerts Card
        if (report.alerts.isNotEmpty()) {
            item {
                ReportCard(title = "Alerts") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        report.alerts.forEach { alert ->
                            Text(alert, color = Color(0xFFFFB3B3), fontSize = 13.sp, lineHeight = 20.sp)
                        }
                    }
                }
            }
        }

        // Spending Patterns Card
        if (report.patterns.isNotEmpty()) {
            item {
                ReportCard(title = "Spending Patterns") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        report.patterns.forEach { pattern ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("•", color = MpesaGreen, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text(pattern, color = TextSecondary, fontSize = 13.sp, lineHeight = 20.sp, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        // Highlights Card
        if (report.highlights.isNotEmpty()) {
            item {
                ReportCard(title = "Highlights") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        report.highlights.forEach { highlight ->
                            Text(highlight, color = Color.White, fontSize = 13.sp, lineHeight = 20.sp)
                        }
                    }
                }
            }
        }

        // Statistics Card
        item {
            ReportCard(title = "Statistics") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatRow("Total Spent", formatKsh(state.stats.totalSpent), Color(0xFFFF6B6B))
                    StatRow("Total Received", formatKsh(state.stats.totalReceived), Color(0xFF4CAF50))
                    StatRow("Net Flow", formatKsh(state.stats.netFlow),
                        if (state.stats.netFlow >= 0) Color(0xFF4CAF50) else Color(0xFFFF6B6B))
                    HorizontalDivider(
                        Modifier,
                        DividerDefaults.Thickness,
                        color = Color.White.copy(alpha = 0.08f)
                    )
                    StatRow("Max Transaction", formatKsh(state.stats.maxTransaction), Color.White)
                    StatRow("Min Transaction", formatKsh(state.stats.minTransaction), Color.White)
                    StatRow("Average", formatKsh(state.stats.avgTransaction), Color.White)
                    StatRow("Total Transactions", "${state.stats.transactionCount}", Color.White)
                    StatRow("Transaction Costs", formatKsh(totalCosts), Color(0xFFFF6B6B))
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
fun ReportCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
fun StatRow(label: String, value: String, valueColor: Color) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = TextSecondary, fontSize = 13.sp)
        Text(value, color = valueColor, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}