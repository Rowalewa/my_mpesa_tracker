package com.example.my_mpesa_tracker.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.my_mpesa_tracker.data.model.MpesaTransaction
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

// ── Chart data — computed once from the report's own transactions, then drawn ──
// ── by Compose on screen and by PdfReportExporter in the PDF, so the numbers  ──
// ── in both can never drift apart.                                            ──

data class ChartSlice(val label: String, val value: Double, val fraction: Float, val color: Color)

data class ReportChartData(
    val slices: List<ChartSlice>,
    val totalOutflow: Double,
    /** Transaction fees as their own row, so category rows + fees reconcile to total spent. */
    val feeRow: TableRow?,
    val topRecipients: List<Pair<String, Double>>,
    /** Spending per weekday, Sunday first (matches the app's Sunday-start week). */
    val weekdaySpend: List<Double>
)

object ReportCharts {
    val palette = listOf(
        Color(0xFF00A550), Color(0xFF3B82F6), Color(0xFFF59E0B),
        Color(0xFF8B5CF6), Color(0xFFEF4444)
    )
    val otherColor = Color(0xFF9AA3B5)
    val feeColor = Color(0xFFEC4899)
    val weekdayShort = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    val weekdayFull = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")

    private const val MAX_CATEGORY_SLICES = 5
    private const val MAX_RECIPIENTS = 5

    fun build(report: SpendingReport, transactions: List<MpesaTransaction>): ReportChartData {
        val debits = transactions.filter { it.isDebit }

        // The app's own totalSpent is amount + transaction cost. Category rows only carry
        // amounts, so fees get their own slice — that way the donut adds to 100% and its
        // total matches the hero "Money out" figure instead of quietly falling short.
        val fees = debits.sumOf { it.transactionCost }
        val feeCount = debits.count { it.transactionCost > 0 }
        val rows = report.tableRows.filter { it.amount > 0 }
        val total = rows.sumOf { it.amount } + fees

        val slices = mutableListOf<ChartSlice>()
        if (total > 0) {
            rows.take(MAX_CATEGORY_SLICES).forEachIndexed { i, row ->
                slices += ChartSlice(row.category, row.amount, (row.amount / total).toFloat(), palette[i % palette.size])
            }
            val rest = rows.drop(MAX_CATEGORY_SLICES).sumOf { it.amount }
            if (rest > 0) slices += ChartSlice("Other", rest, (rest / total).toFloat(), otherColor)
            if (fees > 0) slices += ChartSlice("Transaction fees", fees, (fees / total).toFloat(), feeColor)
        }

        val feeRow = if (fees > 0 && total > 0) TableRow("Transaction fees", fees, feeCount, fees / total * 100) else null

        val recipients = debits
            .filter { it.counterparty.isNotBlank() }
            .groupBy { it.counterparty.trim() }
            .map { (name, txs) -> name to txs.sumOf { it.amount } }
            .sortedByDescending { it.second }
            .take(MAX_RECIPIENTS)

        val weekday = DoubleArray(7)
        val zone = ZoneId.systemDefault()
        debits.forEach { tx ->
            // Monday=1 … Sunday=7, so % 7 puts Sunday at index 0.
            val index = Instant.ofEpochMilli(tx.timestamp).atZone(zone).dayOfWeek.value % 7
            weekday[index] += tx.amount + tx.transactionCost
        }

        return ReportChartData(slices, total, feeRow, recipients, weekday.toList())
    }
}

fun compactAmount(value: Double): String = when {
    value >= 1_000_000 -> String.format(Locale.US, "%.1fM", value / 1_000_000)
    value >= 1_000 -> String.format(Locale.US, "%.1fk", value / 1_000)
    else -> String.format(Locale.US, "%.0f", value)
}

fun formatShare(fraction: Float): String {
    val pct = fraction * 100f
    return if (pct > 0f && pct < 0.1f) "<0.1%" else String.format(Locale.US, "%.1f%%", pct)
}

// ── On-screen cards ───────────────────────────────────────────────────────────

@Composable
fun CategoryDonutCard(data: ReportChartData) {
    if (data.slices.isEmpty()) return
    ReportCard(title = "Where the money went") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(128.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val strokeWidth = 20.dp.toPx()
                    val inset = strokeWidth / 2f
                    val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
                    var start = -90f
                    data.slices.forEach { slice ->
                        val sweep = slice.fraction * 360f
                        drawArc(
                            color = slice.color,
                            startAngle = start,
                            sweepAngle = (sweep - 1.5f).coerceAtLeast(0.5f),
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = arcSize,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                        )
                        start += sweep
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(compactAmount(data.totalOutflow), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("spent", color = TextSecondary, fontSize = 10.sp)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                data.slices.forEach { slice ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).background(slice.color, RoundedCornerShape(2.dp)))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            slice.label,
                            color = TextSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(formatShare(slice.fraction), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Composable
fun TopRecipientsCard(data: ReportChartData) {
    val max = data.topRecipients.maxOfOrNull { it.second } ?: return
    if (max <= 0.0) return
    ReportCard(title = "Top recipients") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            data.topRecipients.forEachIndexed { index, (name, amount) ->
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(20.dp).background(MpesaGreen.copy(alpha = 0.15f), RoundedCornerShape(6.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("${index + 1}", color = MpesaGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            name,
                            color = Color.White,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(formatKsh(amount), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                    Box(
                        Modifier.fillMaxWidth().height(6.dp)
                            .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(3.dp))
                    ) {
                        Box(
                            Modifier.fillMaxWidth((amount / max).toFloat().coerceIn(0.02f, 1f)).fillMaxHeight()
                                .background(MpesaGreen, RoundedCornerShape(3.dp))
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WeekdaySpendCard(data: ReportChartData) {
    val values = data.weekdaySpend
    val max = values.maxOrNull() ?: return
    if (max <= 0.0) return
    val peak = values.indexOf(max)

    ReportCard(title = "Spending by day of week") {
        Row(
            Modifier.fillMaxWidth().height(118.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            ReportCharts.weekdayShort.forEachIndexed { i, label ->
                val v = values[i]
                val barHeight = maxOf(2f, (72.0 * (v / max)).toFloat()).dp
                Column(
                    Modifier.weight(1f).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    if (v > 0) Text(compactAmount(v), color = TextSecondary, fontSize = 10.sp, maxLines = 1)
                    Spacer(Modifier.height(3.dp))
                    Box(
                        Modifier.width(22.dp).height(barHeight).background(
                            if (i == peak) MpesaGreen else MpesaGreen.copy(alpha = 0.35f),
                            RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)
                        )
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(label, color = if (i == peak) Color.White else TextSecondary, fontSize = 11.sp)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Highest on ${ReportCharts.weekdayFull[peak]}s — ${formatKsh(max)} across the period.",
            color = TextSecondary,
            fontSize = 12.sp
        )
    }
}
