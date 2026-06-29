package com.example.my_mpesa_tracker.ui.dashboard

import android.graphics.Paint
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.my_mpesa_tracker.data.db.DailyTotal
import com.example.my_mpesa_tracker.data.model.MpesaTransaction
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.time.Duration.Companion.milliseconds

// ── Colour tokens ─────────────────────────────────────────────────────────────
val ChartGreen       = Color(0xFF00C878)
val ChartRed         = Color(0xFFFF5252)
val ChartGreenFaint  = Color(0xFF00C878).copy(alpha = 0.15f)
val ChartRedFaint    = Color(0xFFFF5252).copy(alpha = 0.12f)
val ChartGrid        = Color(0xFFFFFFFF).copy(alpha = 0.06f)
val ChartLabel       = Color(0xFF8A96A8)
val ChartBg          = Color(0xFF0F1724)

// ── Shared components ─────────────────────────────────────────────────────────

@Composable
fun BalanceStat(label: String, value: String, color: Color) {
    Column {
        Text(label, color = ChartLabel, fontSize = 11.sp)
        Text(value, color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun ChartLegendItem(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            Modifier
                .size(width = 16.dp, height = 3.dp)
                .background(color, RoundedCornerShape(2.dp))
        )
        Text(label, color = ChartLabel, fontSize = 12.sp)
    }
}


// ── Bar Chart ─────────────────────────────────────────────────────────────────

@Composable
fun BeautifulBarChartCard(
    incomeAmount: Double,
    expenseAmount: Double,
    dateLabel: String = ""
) {
    val total = incomeAmount + expenseAmount
    val targetIncomeRatio  = if (total > 0) (incomeAmount  / total).toFloat() else 0.05f
    val targetExpenseRatio = if (total > 0) (expenseAmount / total).toFloat() else 0.05f

    val incomeRatio  by animateFloatAsState(targetIncomeRatio,  tween(700), label = "in")
    val expenseRatio by animateFloatAsState(targetExpenseRatio, tween(700), label = "out")

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = ChartBg)
    ) {
        Column(Modifier.padding(20.dp)) {

            // Title + date
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Cash Flow",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
                if (dateLabel.isNotBlank()) {
                    Text(dateLabel, color = ChartLabel, fontSize = 11.sp)
                }
            }

            Spacer(Modifier.height(24.dp))

            // Bars — expand to fill available width
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
            ) {
                val maxBarHeight = 120.dp
                val barWidth = (maxWidth - 64.dp) / 2  // responsive width

                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.Bottom
                ) {
                    // Cash In
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        Text(
                            formatKsh(incomeAmount),
                            color = ChartGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(6.dp))
                        Canvas(
                            modifier = Modifier
                                .height(maxBarHeight * incomeRatio)
                                .width(barWidth)
                        ) {
                            drawRoundRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(ChartGreen, Color(0xFF007A4A))
                                ),
                                size = size,
                                cornerRadius = CornerRadius(12f, 12f)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Cash In", color = ChartLabel, fontSize = 12.sp)
                        Text(
                            "${(targetIncomeRatio * 100).toInt()}%",
                            color = ChartGreen.copy(alpha = 0.7f),
                            fontSize = 11.sp
                        )
                    }

                    // Cash Out
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        Text(
                            formatKsh(expenseAmount),
                            color = ChartRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(6.dp))
                        Canvas(
                            modifier = Modifier
                                .height(maxBarHeight * expenseRatio)
                                .width(barWidth)
                        ) {
                            drawRoundRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(ChartRed, Color(0xFF8B0000))
                                ),
                                size = size,
                                cornerRadius = CornerRadius(12f, 12f)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Cash Out", color = ChartLabel, fontSize = 12.sp)
                        Text(
                            "${(targetExpenseRatio * 100).toInt()}%",
                            color = ChartRed.copy(alpha = 0.7f),
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // ── Divider + summary ──────────────────────────────────────────
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Net", color = ChartLabel, fontSize = 12.sp)
                val net = incomeAmount - expenseAmount
                Text(
                    formatKsh(net),
                    color = if (net >= 0) ChartGreen else ChartRed,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
// ── Interactive Balance Tracker ───────────────────────────────────────────────


@Composable
fun BalanceTrackerCard(transactions: List<MpesaTransaction>) {
    val sorted = remember(transactions) {
        transactions.sortedBy { it.timestamp }.filter { it.balanceAfter > 0 }
    }
    if (sorted.size < 2) return

    val balances = sorted.map { it.balanceAfter.toFloat() }
    val timestamps = sorted.map { it.timestamp }
    val maxBalance = balances.maxOrNull() ?: 1f
    val minBalance = balances.minOrNull() ?: 0f
    val range = (maxBalance - minBalance).coerceAtLeast(1f)
    val currentBalance = balances.last()
    val firstBalance = balances.first()
    val balanceChange = currentBalance - firstBalance
    val isUp = balanceChange >= 0
    val dateFmt = SimpleDateFormat("d MMM", LocalLocale.current.platformLocale)
    val fullFmt = SimpleDateFormat("d MMM, h:mm a", LocalLocale.current.platformLocale)

    // Crosshair state
    var touchX by remember { mutableStateOf<Float?>(null) }
    var selectedIndex by remember { mutableStateOf<Int?>(null) }

    // Auto-dismiss timeout logic: clears selection after 2 seconds of touch inactivity
    LaunchedEffect(selectedIndex) {
        if (selectedIndex != null) {
            delay(2000.milliseconds)
            selectedIndex = null
            touchX = null
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = ChartBg)
    ) {
        Column(Modifier.padding(20.dp)) {

            // Header — updates when point selected
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("M-Pesa Balance", color = ChartLabel, fontSize = 12.sp)
                    val displayBalance = selectedIndex?.let { balances[it] } ?: currentBalance
                    Text(
                        formatKsh(displayBalance.toDouble()),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp
                    )
                    selectedIndex?.let {
                        Text(
                            fullFmt.format(Date(timestamps[it])),
                            color = ChartLabel,
                            fontSize = 11.sp
                        )
                    }
                }
                if (selectedIndex == null) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "${if (isUp) "▲" else "▼"} ${formatKsh(kotlin.math.abs(balanceChange.toDouble()))}",
                            color = if (isUp) ChartGreen else ChartRed,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text("vs period start", color = ChartLabel, fontSize = 11.sp)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Chart with touch
            Box(
                modifier = Modifier.fillMaxWidth().height(120.dp)
            ) {
                val lineColor = if (isUp) ChartGreen else ChartRed

                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                touchX = offset.x
                                val w = size.width.toFloat()
                                val n = sorted.size
                                val step = if (n > 1) w / (n - 1) else w
                                selectedIndex = (offset.x / step).toInt().coerceIn(0, n - 1)
                            }
                        }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragEnd = { /* timer handles safe fade out */ },
                                onDrag = { change, _ ->
                                    touchX = change.position.x
                                    val w = size.width.toFloat()
                                    val n = sorted.size
                                    val step = if (n > 1) w / (n - 1) else w
                                    selectedIndex = (change.position.x / step).toInt().coerceIn(0, n - 1)
                                }
                            )
                        }
                ) {
                    val w = size.width
                    val h = size.height
                    val n = sorted.size
                    val step = if (n > 1) w / (n - 1) else w

                    // Grid lines and numeric Y-axis labels
                    for (i in 0..3) {
                        val y = h * i / 3
                        drawLine(
                            color = ChartGrid,
                            start = Offset(0f, y),
                            end = Offset(w, y),
                            strokeWidth = 1f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                        )

                        // Calculate exact mathematical currency value corresponding to this horizontal line position
                        val lineValue = minBalance + (range * (h - y) / (h * 0.85f))
                        val labelText = if (lineValue >= 1000) "${"%.0f".format(lineValue / 1000)}k"
                        else "%.0f".format(lineValue)

                        drawContext.canvas.nativeCanvas.drawText(
                            labelText, 8f, y + 10f,
                            Paint().apply {
                                color = android.graphics.Color.argb(120, 138, 150, 168)
                                textSize = 24f
                                isAntiAlias = true
                            }
                        )
                    }

                    // Fill
                    val fillPath = Path()
                    balances.forEachIndexed { i, v ->
                        val x = i * step
                        val y = h - ((v - minBalance) / range) * (h * 0.85f)
                        if (i == 0) fillPath.moveTo(x, y) else fillPath.lineTo(x, y)
                    }
                    fillPath.lineTo((n - 1) * step, h)
                    fillPath.lineTo(0f, h)
                    fillPath.close()
                    drawPath(fillPath, Brush.verticalGradient(
                        colors = listOf(lineColor.copy(alpha = 0.25f), Color.Transparent)
                    ))

                    // Line
                    val linePath = Path()
                    balances.forEachIndexed { i, v ->
                        val x = i * step
                        val y = h - ((v - minBalance) / range) * (h * 0.85f)
                        if (i == 0) linePath.moveTo(x, y) else linePath.lineTo(x, y)
                    }
                    drawPath(linePath, lineColor, style = Stroke(width = 2.5f))

                    // Dots
                    balances.forEachIndexed { i, v ->
                        val x = i * step
                        val y = h - ((v - minBalance) / range) * (h * 0.85f)
                        drawCircle(ChartBg, radius = 5f, center = Offset(x, y))
                        drawCircle(lineColor, radius = 3.5f, center = Offset(x, y))
                    }

                    // Crosshair
                    selectedIndex?.let { idx ->
                        val x = idx * step
                        val y = h - ((balances[idx] - minBalance) / range) * (h * 0.85f)

                        // Vertical line
                        drawLine(
                            color = Color.White.copy(alpha = 0.4f),
                            start = Offset(x, 0f),
                            end = Offset(x, h),
                            strokeWidth = 1.5f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f)
                        )

                        // Highlighted dot
                        drawCircle(ChartBg, radius = 8f, center = Offset(x, y))
                        drawCircle(lineColor, radius = 6f, center = Offset(x, y))
                        drawCircle(Color.White, radius = 3f, center = Offset(x, y))
                    }
                }
            }

            // X-axis
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(dateFmt.format(Date(timestamps.first())), color = ChartLabel, fontSize = 10.sp)
                Text("Balance over time", color = ChartLabel, fontSize = 10.sp, textAlign = TextAlign.Center)
                Text(dateFmt.format(Date(timestamps.last())), color = ChartLabel, fontSize = 10.sp)
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                BalanceStat("High", formatKsh(maxBalance.toDouble()), ChartGreen)
                BalanceStat("Low", formatKsh(minBalance.toDouble()), ChartRed)
                BalanceStat("Tx Count", sorted.size.toString(), ChartLabel)
            }
        }
    }
}

// ── Interactive Line Chart (Dual — Cash In + Cash Out) ───────────────────────

@Composable
fun LineChartCard(dailyTotals: List<DailyTotal>) {
    if (dailyTotals.size < 2) return

    val spentValues = dailyTotals.map { it.spent.toFloat() }
    val receivedValues = dailyTotals.map { it.received.toFloat() }
    val maxVal = (spentValues + receivedValues).maxOrNull()?.coerceAtLeast(1f) ?: 1f
    val dateFmt = SimpleDateFormat("d MMM", LocalLocale.current.platformLocale)
    val labels = dailyTotals.map { dateFmt.format(Date(it.day)) }

    var selectedIndex by remember { mutableStateOf<Int?>(null) }

    // Auto-dismiss timeout logic: clears spending trend crosshair after 2 seconds of touch inactivity
    LaunchedEffect(selectedIndex) {
        if (selectedIndex != null) {
            delay(2000.milliseconds)
            selectedIndex = null
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = ChartBg)
    ) {
        Column(Modifier.padding(20.dp)) {

            // Title + selected point info
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Spending Trend",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
                if (selectedIndex != null) {
                    Text(labels[selectedIndex!!], color = ChartLabel, fontSize = 11.sp)
                } else {
                    Text(
                        "${labels.first()} – ${labels.last()}",
                        color = ChartLabel,
                        fontSize = 11.sp
                    )
                }
            }

            // Tooltip row when point selected
            selectedIndex?.let { idx ->
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    TooltipStat("📥 In", formatKsh(receivedValues[idx].toDouble()), ChartGreen)
                    TooltipStat("📤 Out", formatKsh(spentValues[idx].toDouble()), ChartRed)
                    val net = receivedValues[idx] - spentValues[idx]
                    TooltipStat("Net", formatKsh(net.toDouble()), if (net >= 0) ChartGreen else ChartRed)
                }
            }

            Spacer(Modifier.height(16.dp))

            // Chart
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            val w = size.width.toFloat()
                            val n = dailyTotals.size
                            val step = if (n > 1) w / (n - 1) else w
                            selectedIndex = (offset.x / step).toInt().coerceIn(0, n - 1)
                        }
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragEnd = { /* timer handles auto dismissal */ },
                            onDrag = { change, _ ->
                                val w = size.width.toFloat()
                                val n = dailyTotals.size
                                val step = if (n > 1) w / (n - 1) else w
                                selectedIndex = (change.position.x / step).toInt().coerceIn(0, n - 1)
                            }
                        )
                    }
            ) {
                val w = size.width
                val h = size.height
                val n = dailyTotals.size
                val step = if (n > 1) w / (n - 1) else w

                // Grid
                for (i in 0..3) {
                    val y = h * i / 3
                    drawLine(
                        color = ChartGrid,
                        start = Offset(0f, y),
                        end = Offset(w, y),
                        strokeWidth = 1f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                    )
                    // Y label
                    val value = maxVal * (3 - i) / 3
                    val labelText = if (value >= 1000) "${"%.0f".format(value / 1000)}k"
                    else "%.0f".format(value)
                    drawContext.canvas.nativeCanvas.drawText(
                        labelText, 8f, y + 10f,
                        Paint().apply {
                            color = android.graphics.Color.argb(120, 138, 150, 168)
                            textSize = 24f
                            isAntiAlias = true
                        }
                    )
                }

                // Fill areas
                fun fillArea(values: List<Float>, color: Color) {
                    val path = Path()
                    values.forEachIndexed { i, v ->
                        val x = i * step
                        val y = h - (v / maxVal) * (h * 0.85f)
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    path.lineTo((values.size - 1) * step, h)
                    path.lineTo(0f, h)
                    path.close()
                    drawPath(path, color)
                }
                fillArea(receivedValues, ChartGreenFaint)
                fillArea(spentValues, ChartRedFaint)

                // Lines
                fun drawChartLine(values: List<Float>, color: Color) {
                    val path = Path()
                    values.forEachIndexed { i, v ->
                        val x = i * step
                        val y = h - (v / maxVal) * (h * 0.85f)
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(path, color, style = Stroke(width = 2.5f))
                }
                drawChartLine(receivedValues, ChartGreen)
                drawChartLine(spentValues, ChartRed)

                // Dots
                fun drawDots(values: List<Float>, color: Color) {
                    values.forEachIndexed { i, v ->
                        val x = i * step
                        val y = h - (v / maxVal) * (h * 0.85f)
                        drawCircle(ChartBg, radius = 5f, center = Offset(x, y))
                        drawCircle(color, radius = 3.5f, center = Offset(x, y))
                    }
                }
                drawDots(receivedValues, ChartGreen)
                drawDots(spentValues, ChartRed)

                // Crosshair for selected index
                selectedIndex?.let { idx ->
                    val x = idx * step
                    val yIn = h - (receivedValues[idx] / maxVal) * (h * 0.85f)
                    val yOut = h - (spentValues[idx] / maxVal) * (h * 0.85f)

                    // Vertical line
                    drawLine(
                        color = Color.White.copy(alpha = 0.35f),
                        start = Offset(x, 0f),
                        end = Offset(x, h),
                        strokeWidth = 1.5f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f)
                    )

                    // Highlighted dots
                    drawCircle(ChartBg, radius = 8f, center = Offset(x, yIn))
                    drawCircle(ChartGreen, radius = 6f, center = Offset(x, yIn))
                    drawCircle(Color.White, radius = 3f, center = Offset(x, yIn))

                    drawCircle(ChartBg, radius = 8f, center = Offset(x, yOut))
                    drawCircle(ChartRed, radius = 6f, center = Offset(x, yOut))
                    drawCircle(Color.White, radius = 3f, center = Offset(x, yOut))
                }
            }

            // X-axis labels
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                val n = labels.size
                val step = if (n > 1) (n - 1) / (minOf(n, 5) - 1).coerceAtLeast(1) else 1
                val shown = (0 until n step step).map { labels[it] }
                shown.forEach { label ->
                    Text(label, color = ChartLabel, fontSize = 10.sp, textAlign = TextAlign.Center)
                }
            }

            // Legend
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                ChartLegendItem(ChartGreen, "Cash In")
                ChartLegendItem(ChartRed, "Cash Out")
            }
        }
    }
}

@Composable
fun TooltipStat(label: String, value: String, color: Color) {
    Column {
        Text(label, color = ChartLabel, fontSize = 11.sp)
        Text(value, color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}