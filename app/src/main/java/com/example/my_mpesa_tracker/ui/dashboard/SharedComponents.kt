package com.example.my_mpesa_tracker.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.my_mpesa_tracker.data.model.MpesaTransaction
import com.example.my_mpesa_tracker.util.label

@Composable
fun TransactionRow(tx: MpesaTransaction, onClick: () -> Unit = {}) {
    val isDebit = tx.isDebit
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(40.dp).background(
                    if (isDebit) NegativeRed.copy(alpha = 0.15f) else MpesaGreen.copy(alpha = 0.15f),
                    RoundedCornerShape(10.dp)
                ),
                contentAlignment = Alignment.Center
            ) { Text(tx.type.emoji(), fontSize = 18.sp) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(tx.counterparty, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 14.sp, maxLines = 1)
                Text(tx.type.label(), color = TextSecondary, fontSize = 12.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${if (isDebit) "-" else "+"}${formatKsh(tx.amount)}",
                    color = if (isDebit) NegativeRed else PositiveGreen,
                    fontWeight = FontWeight.Bold, fontSize = 14.sp
                )
                Text(formatTime(tx.timestamp), color = TextSecondary, fontSize = 11.sp)
            }
        }
    }
}
