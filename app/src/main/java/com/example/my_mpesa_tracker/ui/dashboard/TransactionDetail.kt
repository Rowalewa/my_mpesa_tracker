package com.example.my_mpesa_tracker.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.my_mpesa_tracker.data.model.MpesaTransaction
import com.example.my_mpesa_tracker.util.label

@Composable
fun TransactionDetailDialog(tx: MpesaTransaction, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardDark,
        title = {
            Text(tx.counterparty, color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailRow("Type", tx.type.label())
                DetailRow("Amount", formatKsh(tx.amount))
                DetailRow("Transaction cost", formatKsh(tx.transactionCost))
                DetailRow("Direction", if (tx.isDebit) "Sent / Paid" else "Received")
                DetailRow("Balance after", formatKsh(tx.balanceAfter))
                DetailRow("Date", formatTime(tx.timestamp))
                DetailRow("M-Pesa Code", tx.mpesaCode)
                SubcategoryDetailRow(tx = tx)
                var showNoteDialog by remember { mutableStateOf(false) }
                val context = LocalContext.current
                val note = NoteStorage.getNote(context, tx.mpesaCode)

                if (note.isNotBlank()) {
                    DetailRow("Note", note)
                }
                TextButton(onClick = { showNoteDialog = true }) {
                    Text(if (note.isBlank()) "+ Add note" else "Edit note", color = MpesaGreen, fontSize = 13.sp)
                }

                if (showNoteDialog) {
                    TransactionNoteDialog(tx = tx, onDismiss = { showNoteDialog = false })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = MpesaGreen)
            }
        }
    )
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = TextSecondary, fontSize = 13.sp)
        Text(value, color = androidx.compose.ui.graphics.Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}
