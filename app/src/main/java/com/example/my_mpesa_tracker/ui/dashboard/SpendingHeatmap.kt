package com.example.my_mpesa_tracker.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.my_mpesa_tracker.data.model.MpesaTransaction
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

data class DayCell(
    val date: LocalDate,
    val spent: Double,
    val received: Double,
    val count: Int
) {
    val net: Double get() = received - spent
    val activity: Double get() = spent + received
}

@Composable
fun SpendingHeatmapCard(
    transactions: List<MpesaTransaction>,
    onDaySelected: (LocalDate) -> Unit = {}
) {
    if (transactions.isEmpty()) return
    val zone = ZoneId.systemDefault()

    val dayCells = remember(transactions) {
        transactions.groupBy {
            Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate()
        }.map { (date, txs) ->
            DayCell(
                date = date,
                spent = txs.filter { it.isDebit }.sumOf { it.amount + it.transactionCost },
                received = txs.filter { !it.isDebit }.sumOf { it.amount },
                count = txs.size
            )
        }
    }

    if (dayCells.size < 2) return

    val minDate = dayCells.minOf { it.date }
    val maxDate = dayCells.maxOf { it.date }
    val cellMap = dayCells.associateBy { it.date }

    val fullRange = remember(minDate, maxDate) {
        generateSequence(minDate) { it.plusDays(1) }
            .takeWhile { !it.isAfter(maxDate) }
            .map { date -> cellMap[date] ?: DayCell(date, 0.0, 0.0, 0) }
            .toList()
    }

    val maxActivity = fullRange.maxOfOrNull { it.activity }?.coerceAtLeast(1.0) ?: 1.0

    // Pad front so first column starts on Sunday
    val firstDow = minDate.dayOfWeek.value % 7 // Sunday=0 ... Saturday=6
    val padded: List<DayCell?> = List(firstDow) { null } + fullRange
    val weeks: List<List<DayCell?>> = padded.chunked(7).map { week ->
        if (week.size < 7) week + List(7 - week.size) { null } else week
    }

    var selectedDay by remember { mutableStateOf<DayCell?>(null) }
    val cellSize = 14.dp
    val cellGap = 3.dp

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = ChartBg)
    ) {
        Column(Modifier.padding(20.dp)) {

            Text(
                "Activity Calendar",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )
            Text(
                "${minDate.format(DateTimeFormatter.ofPattern("d MMM"))} – ${maxDate.format(DateTimeFormatter.ofPattern("d MMM yyyy"))}",
                color = ChartLabel,
                fontSize = 11.sp
            )

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.Top
            ) {
                // Day-of-week labels
                Column(
                    modifier = Modifier.padding(end = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(cellGap)
                ) {
                    listOf("", "Mon", "", "Wed", "", "Fri", "").forEach { label ->
                        Box(
                            modifier = Modifier.height(cellSize),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(label, color = ChartLabel, fontSize = 9.sp)
                        }
                    }
                }

                // Week columns
                var lastMonth = -1
                weeks.forEach { week ->
                    val firstRealCell = week.firstOrNull { it != null }
                    val monthLabel = firstRealCell?.let {
                        val m = it.date.monthValue
                        if (m != lastMonth) {
                            lastMonth = m
                            it.date.month.getDisplayName(TextStyle.SHORT, Locale.US)
                        } else null
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier.height(14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (monthLabel != null) {
                                Text(monthLabel, color = ChartLabel, fontSize = 9.sp)
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(cellGap)) {
                            week.forEach { cell ->
                                val color = cellColor(cell, maxActivity)
                                Box(
                                    modifier = Modifier
                                        .size(cellSize)
                                        .background(color, RoundedCornerShape(3.dp))
                                        .then(
                                            if (cell != null && cell.count > 0)
                                                Modifier.clickable { selectedDay = cell }
                                            else Modifier
                                        )
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(cellGap))
                }
            }

            Spacer(Modifier.height(16.dp))

            // Legend
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("Less", color = ChartLabel, fontSize = 10.sp)
                listOf(0.15f, 0.4f, 0.65f, 1f).forEach { alpha ->
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(ChartRed.copy(alpha = alpha), RoundedCornerShape(2.dp))
                    )
                }
                Text("More spend", color = ChartLabel, fontSize = 10.sp)
                Spacer(Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(ChartGreen, RoundedCornerShape(2.dp))
                )
                Text("Net positive day", color = ChartLabel, fontSize = 10.sp)
            }
        }
    }

    selectedDay?.let { day ->
        DayDetailDialog(
            day = day,
            onDismiss = { selectedDay = null },
            onViewTransactions = {
                onDaySelected(day.date)
                selectedDay = null
            }
        )
    }
}

private fun cellColor(cell: DayCell?, maxActivity: Double): Color {
    if (cell == null || cell.count == 0) return Color(0xFF1A2432)
    val intensity = (cell.activity / maxActivity).coerceIn(0.2, 1.0).toFloat()
    val base = if (cell.net >= 0) ChartGreen else ChartRed
    return base.copy(alpha = intensity)
}

@Composable
fun DayDetailDialog(
    day: DayCell,
    onDismiss: () -> Unit,
    onViewTransactions: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardDark,
        title = {
            Text(
                day.date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy")),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailRow("Spent", formatKsh(day.spent))
                DetailRow("Received", formatKsh(day.received))
                DetailRow(
                    "Net",
                    formatKsh(day.net)
                )
                DetailRow("Transactions", day.count.toString())
            }
        },
        confirmButton = {
            TextButton(onClick = onViewTransactions) {
                Text("View transactions", color = MpesaGreen)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = TextSecondary)
            }
        }
    )
}
