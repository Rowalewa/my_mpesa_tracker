package com.example.my_mpesa_tracker.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.my_mpesa_tracker.data.model.MpesaTransaction
import com.example.my_mpesa_tracker.util.AnomalyDetector
import com.example.my_mpesa_tracker.util.AnomalyResult

/**
 * Shows unusual transactions detected against the person's own spending
 * patterns. Renders nothing if there's not enough data or nothing unusual
 * was found — a quiet feature, only speaks up when it matters.
 */
@Composable
fun AnomalyAlertsCard(
    transactions: List<MpesaTransaction>,
    onTransactionClick: (MpesaTransaction) -> Unit
) {
    val context = LocalContext.current
    val anomalies = remember(transactions) {
        AnomalyDetector.detectAnomalies(context, transactions)
    }

    if (anomalies.isEmpty()) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = ChartBg)
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("🔎", fontSize = 16.sp)
                Text(
                    "Worth a Second Look",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
            }
            Text(
                "Transactions that stand out from your usual patterns",
                color = ChartLabel,
                fontSize = 11.sp
            )

            Spacer(Modifier.height(14.dp))

            anomalies.forEachIndexed { i, result ->
                AnomalyRow(result, onClick = { onTransactionClick(result.transaction) })
                if (i < anomalies.lastIndex) Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
fun AnomalyRow(result: AnomalyResult, onClick: () -> Unit) {
    val tx = result.transaction
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                tx.counterparty,
                color = Color.White,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                maxLines = 1
            )
            Text(
                result.explanation,
                color = Color(0xFFFFA726),
                fontSize = 11.sp
            )
        }
        Text(
            formatKsh(tx.amount),
            color = ChartRed,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
    }
}
