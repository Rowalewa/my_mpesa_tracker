package com.example.my_mpesa_tracker.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.my_mpesa_tracker.data.model.MpesaTransaction
import com.example.my_mpesa_tracker.util.Confidence
import com.example.my_mpesa_tracker.util.SpendForecastEngine

/**
 * Only meaningful when viewing the current month — pass this month's
 * transactions (already SIM-filtered by the caller, same as
 * BalanceTrackerCard). Renders nothing if there isn't enough data yet
 * to project from.
 */
@Composable
fun ForecastCard(thisMonthTransactions: List<MpesaTransaction>) {
    val context = LocalContext.current
    val forecast = remember(thisMonthTransactions) {
        SpendForecastEngine.forecastMonthEnd(context, thisMonthTransactions)
    }

    if (forecast == null) return

    val confidenceColor = when (forecast.confidence) {
        Confidence.HIGH -> ChartGreen
        Confidence.MEDIUM -> Color(0xFFFFA726)
        Confidence.LOW -> ChartLabel
    }
    val confidenceLabel = when (forecast.confidence) {
        Confidence.HIGH -> "High confidence"
        Confidence.MEDIUM -> "Medium confidence"
        Confidence.LOW -> "Early estimate"
    }

    val dayProgress = forecast.daysElapsed.toFloat() / forecast.daysInMonth.toFloat()
    val spendProgress = if (forecast.projectedTotal > 0)
        (forecast.monthToDateSpent / forecast.projectedTotal).toFloat().coerceIn(0f, 1f)
    else 0f

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = ChartBg)
    ) {
        Column(Modifier.padding(20.dp)) {

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Month-End Forecast", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(confidenceLabel, color = confidenceColor, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }

            Spacer(Modifier.height(14.dp))

            Text("Projected total spend", color = ChartLabel, fontSize = 12.sp)
            Text(
                formatKsh(forecast.projectedTotal),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp
            )

            Spacer(Modifier.height(16.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Spent so far", color = ChartLabel, fontSize = 11.sp)
                    Text(formatKsh(forecast.monthToDateSpent), color = ChartRed, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Projected remaining", color = ChartLabel, fontSize = 11.sp)
                    Text(formatKsh(forecast.projectedRemaining), color = ChartLabel, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(16.dp))

            // Day progress vs spend progress — a quick visual read on
            // whether spending is running ahead of or behind the calendar.
            Text(
                "Day ${forecast.daysElapsed} of ${forecast.daysInMonth}",
                color = ChartLabel,
                fontSize = 11.sp
            )
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { dayProgress },
                modifier = Modifier.fillMaxWidth().height(4.dp),
                color = ChartLabel,
                trackColor = Color.White.copy(alpha = 0.06f)
            )

            Spacer(Modifier.height(8.dp))

            Text(
                "${(spendProgress * 100).toInt()}% of projected spend used",
                color = ChartLabel,
                fontSize = 11.sp
            )
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { spendProgress },
                modifier = Modifier.fillMaxWidth().height(4.dp),
                color = if (spendProgress > dayProgress) ChartRed else ChartGreen,
                trackColor = Color.White.copy(alpha = 0.06f)
            )

            if (forecast.confidence == Confidence.LOW) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Early in the month — this estimate will sharpen as more days pass.",
                    color = ChartLabel,
                    fontSize = 11.sp
                )
            }
        }
    }
}
