package com.example.my_mpesa_tracker.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.my_mpesa_tracker.util.MerchantCategoryEngine
import com.example.my_mpesa_tracker.util.MerchantCategoryManager
import com.example.my_mpesa_tracker.util.MerchantSubcategory

// ── Subcategory Breakdown Card ──────────────────────────────────────────────

@Composable
fun SubcategoryBreakdownCard(transactions: List<MpesaTransaction>) {
    val context = LocalContext.current
    var refreshTrigger by remember { mutableIntStateOf(0) }

    val breakdown = remember(transactions, refreshTrigger) {
        MerchantCategoryEngine.computeBreakdown(context, transactions)
    }

    if (breakdown.isEmpty()) return
    val total = breakdown.sumOf { it.second }.coerceAtLeast(1.0)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = ChartBg)
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                "Where Your Money Goes",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )
            Text(
                "Spending categories.",
                color = ChartLabel,
                fontSize = 11.sp
            )

            Spacer(Modifier.height(16.dp))

            breakdown.forEach { (subcategory, amount) ->
                val fraction = (amount / total).toFloat()
                SubcategoryRow(subcategory, amount, fraction)
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
fun SubcategoryRow(subcategory: MerchantSubcategory, amount: Double, fraction: Float) {
    Column {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(subcategory.emoji, fontSize = 15.sp)
                Text(subcategory.label, color = Color.White, fontSize = 13.sp)
            }
            Text(
                formatKsh(amount),
                color = ChartRed,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth().height(4.dp),
            color = MpesaGreen,
            trackColor = Color.White.copy(alpha = 0.08f)
        )
    }
}

// ── Subcategory Picker (used inside Transaction Detail Dialog) ─────────────

@Composable
fun SubcategoryPickerDialog(
    currentSubcategory: MerchantSubcategory,
    onDismiss: () -> Unit,
    onSelect: (MerchantSubcategory) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardDark,
        title = { Text("Choose Category", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(MerchantSubcategory.entries.filter {
                    it != MerchantSubcategory.PERSONAL_TRANSFER &&
//                            it != MerchantSubcategory.AIRTIME_DATA &&
                            it != MerchantSubcategory.CASH_WITHDRAWAL &&
                            it != MerchantSubcategory.SAVINGS_INVESTMENT &&
                            it != MerchantSubcategory.OVERDRAFT &&
                            it != MerchantSubcategory.CHARITY_GIVING
                }) { subcategory ->
                    val isSelected = subcategory == currentSubcategory
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(subcategory) }
                            .background(
                                if (isSelected) MpesaGreen.copy(alpha = 0.12f) else Color.Transparent,
                                RoundedCornerShape(8.dp)
                            )
                            .padding(vertical = 10.dp, horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(subcategory.emoji, fontSize = 16.sp)
                            Text(subcategory.label, color = Color.White, fontSize = 13.sp)
                        }
                        if (isSelected) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = MpesaGreen, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
        }
    )
}

// ── Reusable row for showing/editing subcategory inside TransactionDetailDialog ──

@Composable
fun SubcategoryDetailRow(tx: MpesaTransaction) {
    val context = LocalContext.current
    var refreshTrigger by remember { mutableIntStateOf(0) }
    var showPicker by remember { mutableStateOf(false) }

    val eligible = tx.isDebit && (
            tx.type.name == "BUY_GOODS" || tx.type.name == "PAY_BILL" ||
                    tx.type.name == "POCHI_LA_BIASHARA" || tx.type.name == "LIPA_MDOGO_MDOGO"
            )
    if (!eligible) return

    val subcategory = remember(refreshTrigger) {
        MerchantCategoryEngine.categorize(context, tx)
    }

    Row(
        Modifier.fillMaxWidth().clickable { showPicker = true },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Category", color = TextSecondary, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${subcategory.emoji} ${subcategory.label}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Icon(Icons.Default.Edit, contentDescription = "Edit category", tint = MpesaGreen, modifier = Modifier.size(14.dp))
        }
    }

    if (showPicker) {
        SubcategoryPickerDialog(
            currentSubcategory = subcategory,
            onDismiss = { showPicker = false },
            onSelect = { newCategory ->
                MerchantCategoryManager.setOverride(context, tx.counterparty, newCategory)
                refreshTrigger++
                showPicker = false
            }
        )
    }
}
