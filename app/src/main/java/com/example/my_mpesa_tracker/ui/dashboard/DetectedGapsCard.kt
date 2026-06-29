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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.my_mpesa_tracker.data.model.MpesaTransaction
import com.example.my_mpesa_tracker.data.model.TransactionType

/**
 * Shows placeholder entries created by BalanceGapDetector — real
 * money movements the balance chain proves happened, but with no
 * matching SMS. Only renders when at least one exists. Tapping opens
 * the normal TransactionDetailDialog, where a note can be added once
 * the real source is identified (as happened with the Postbank
 * example this feature was built to catch).
 */
@Composable
fun DetectedGapsCard(
    transactions: List<MpesaTransaction>,
    onTransactionClick: (MpesaTransaction) -> Unit
) {
    val gaps = remember(transactions) {
        transactions.filter { it.type == TransactionType.UNACCOUNTED_ADJUSTMENT }
            .sortedByDescending { it.timestamp }
    }

    if (gaps.isEmpty()) return

    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF3D2B1A))
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("⚠️", fontSize = 16.sp)
                    Column {
                        Text(
                            "${gaps.size} unaccounted ${if (gaps.size == 1) "gap" else "gaps"} detected",
                            color = Color(0xFFFFD180),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            "Balance chain didn't reconcile — likely missing SMS",
                            color = Color(0xFFFFD180).copy(alpha = 0.7f),
                            fontSize = 11.sp
                        )
                    }
                }
                Text(if (expanded) "▲" else "▼", color = Color(0xFFFFD180), fontSize = 12.sp)
            }

            if (expanded) {
                Spacer(Modifier.height(12.dp))
                gaps.forEach { tx ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onTransactionClick(tx) }
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                formatTime(tx.timestamp),
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                "Tap to add a note if you identify the source",
                                color = Color(0xFFFFD180).copy(alpha = 0.6f),
                                fontSize = 10.sp
                            )
                        }
                        Text(
                            "${if (tx.isDebit) "-" else "+"}${formatKsh(tx.amount)}",
                            color = if (tx.isDebit) Color(0xFFFF8A80) else Color(0xFFB9F6CA),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}
