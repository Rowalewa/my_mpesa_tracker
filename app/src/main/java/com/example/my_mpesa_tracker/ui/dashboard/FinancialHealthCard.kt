package com.example.my_mpesa_tracker.ui.dashboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.my_mpesa_tracker.data.db.DailyTotal
import com.example.my_mpesa_tracker.data.model.MpesaTransaction
import com.example.my_mpesa_tracker.util.HealthScoreEngine
import com.example.my_mpesa_tracker.util.ScoreComponent

@Composable
fun FinancialHealthCard(
    transactions: List<MpesaTransaction>,
    dailyTotals: List<DailyTotal>
) {
    val context = LocalContext.current
    val health = remember(transactions, dailyTotals) {
        HealthScoreEngine.compute(context, transactions, dailyTotals)
    }

    if (!health.hasEnoughData) return

    val bandColor = Color(health.bandColorHex)
    val animatedScore by animateFloatAsState(
        targetValue = health.overallScore.toFloat(),
        animationSpec = tween(1000),
        label = "score"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = ChartBg)
    ) {
        Column(Modifier.padding(20.dp)) {

            Text(
                "Financial Health",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )

            Spacer(Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Circular gauge
                Box(
                    modifier = Modifier.size(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokeWidth = 10.dp.toPx()
                        val diameter = size.minDimension - strokeWidth
                        val topLeft = androidx.compose.ui.geometry.Offset(
                            (size.width - diameter) / 2,
                            (size.height - diameter) / 2
                        )
                        val arcSize = Size(diameter, diameter)

                        // Background track
                        drawArc(
                            color = Color.White.copy(alpha = 0.08f),
                            startAngle = -90f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                        )

                        // Progress arc
                        drawArc(
                            color = bandColor,
                            startAngle = -90f,
                            sweepAngle = (animatedScore / 100f) * 360f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "${animatedScore.toInt()}",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 28.sp
                        )
                        Text(
                            "/100",
                            color = ChartLabel,
                            fontSize = 11.sp
                        )
                    }
                }

                Spacer(Modifier.width(20.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        health.band,
                        color = bandColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        bandDescription(health.band),
                        color = ChartLabel,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // Component breakdown
            health.components.forEach { component ->
                ComponentRow(component)
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
fun ComponentRow(component: ScoreComponent) {
    val color = when {
        component.score >= 80 -> ChartGreen
        component.score >= 60 -> Color(0xFF4CAF50)
        component.score >= 40 -> Color(0xFFFFA726)
        else -> ChartRed
    }

    Column {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(component.label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Text(
                "${component.score.toInt()}",
                color = color,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(4.dp))
        androidx.compose.material3.LinearProgressIndicator(
            progress = { (component.score / 100).toFloat() },
            modifier = Modifier.fillMaxWidth().height(4.dp),
            color = color,
            trackColor = Color.White.copy(alpha = 0.08f)
        )
        Spacer(Modifier.height(2.dp))
        Text(
            component.explanation,
            color = ChartLabel,
            fontSize = 10.sp
        )
    }
}

fun bandDescription(band: String): String = when (band) {
    "Excellent" -> "Your finances are well managed and consistent."
    "Good" -> "Solid habits with a little room to tighten up."
    "Fair" -> "Some patterns worth watching more closely."
    else -> "A few areas need attention to stabilise your flow."
}
