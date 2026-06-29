package com.example.my_mpesa_tracker.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.my_mpesa_tracker.data.model.MpesaTransaction
import com.example.my_mpesa_tracker.util.ManualEntryType
import com.example.my_mpesa_tracker.util.ManualNetWorthEntry
import com.example.my_mpesa_tracker.util.ManualNetWorthManager
import com.example.my_mpesa_tracker.util.NetWorthEngine

@Composable
fun NetWorthCard(transactions: List<MpesaTransaction>) {
    val context = LocalContext.current
    var refreshTrigger by remember { mutableIntStateOf(0) }
    var showAddDialog by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }

    val breakdown = remember(transactions) { NetWorthEngine.compute(transactions) }
    val manualEntries = remember(refreshTrigger) { ManualNetWorthManager.getAllEntries(context) }

    val manualAssets = manualEntries.filter { it.type == ManualEntryType.ASSET }.sumOf { it.amount }
    val manualLiabilities = manualEntries.filter { it.type == ManualEntryType.LIABILITY }.sumOf { it.amount }
    val otherInvestmentsTotal = breakdown.otherInvestmentsContributed.values.sum()

    val netWorth = breakdown.mpesaCash + breakdown.ziidiEstimated + otherInvestmentsTotal + manualAssets - manualLiabilities

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
                Text("Net Worth", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                IconButton(onClick = { showAddDialog = true }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Add, contentDescription = "Add manual entry", tint = MpesaGreen)
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(formatKsh(netWorth), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 28.sp)
            Text(
                "Cash + estimated savings + contributions + manual entries",
                color = ChartLabel,
                fontSize = 11.sp
            )

            Spacer(Modifier.height(14.dp))

            TextButton(onClick = { expanded = !expanded }, contentPadding = PaddingValues(0.dp)) {
                Text(if (expanded) "Hide breakdown ▲" else "Show breakdown ▼", color = MpesaGreen, fontSize = 12.sp)
            }

            if (expanded) {
                Spacer(Modifier.height(8.dp))

                NetWorthRow("M-Pesa Cash", breakdown.mpesaCash, "Live", ChartGreen)
                NetWorthRow("Ziidi Wallet", breakdown.ziidiEstimated, "Estimated", Color(0xFFFFA726))

                breakdown.otherInvestmentsContributed.forEach { (label, amount) ->
                    NetWorthRow(label, amount, "Contributed via M-Pesa", ChartLabel)
                }

                if (breakdown.otherInvestmentsContributed.isNotEmpty()) {
                    Text(
                        "Contributed figures are what's gone in via M-Pesa — not current value. Returns, losses, and withdrawals to a bank account aren't visible here.",
                        color = ChartLabel,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                    )
                }

                if (breakdown.fulizaUsedRecently > 0) {
                    NetWorthRow(
                        "Fuliza used this period",
                        breakdown.fulizaUsedRecently,
                        "Informational — not a running balance",
                        Color(0xFFFF6B6B)
                    )
                }

                if (manualEntries.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                    Spacer(Modifier.height(8.dp))
                    Text("Manually tracked", color = ChartLabel, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    manualEntries.forEach { entry ->
                        ManualEntryRow(entry) {
                            ManualNetWorthManager.deleteEntry(context, entry.id)
                            refreshTrigger++
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddManualEntryDialog(
            onDismiss = { showAddDialog = false },
            onSave = { name, amount, type ->
                ManualNetWorthManager.addEntry(context, name, amount, type)
                refreshTrigger++
                showAddDialog = false
            }
        )
    }
}

@Composable
fun NetWorthRow(label: String, amount: Double, tag: String, tagColor: Color) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(label, color = Color.White, fontSize = 13.sp)
            Text(tag, color = tagColor, fontSize = 10.sp)
        }
        Text(formatKsh(amount), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun ManualEntryRow(entry: ManualNetWorthEntry, onDelete: () -> Unit) {
    val color = if (entry.type == ManualEntryType.ASSET) ChartGreen else ChartRed
    val sign = if (entry.type == ManualEntryType.ASSET) "+" else "-"
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(entry.name, color = Color.White, fontSize = 13.sp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("$sign${formatKsh(entry.amount)}", color = color, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            IconButton(onClick = onDelete, modifier = Modifier.size(22.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "Remove", tint = ChartLabel, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
fun AddManualEntryDialog(onDismiss: () -> Unit, onSave: (String, Double, ManualEntryType) -> Unit) {
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(ManualEntryType.ASSET) }
    var error by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardDark,
        title = { Text("Add Manual Entry", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = type == ManualEntryType.ASSET,
                        onClick = { type = ManualEntryType.ASSET },
                        label = { Text("Asset", fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ChartGreen,
                            containerColor = Color.White.copy(0.05f)
                        )
                    )
                    FilterChip(
                        selected = type == ManualEntryType.LIABILITY,
                        onClick = { type = ManualEntryType.LIABILITY },
                        label = { Text("Liability", fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ChartRed,
                            containerColor = Color.White.copy(0.05f)
                        )
                    )
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("e.g. Equity Bank savings, Car loan", color = TextSecondary.copy(0.5f)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MpesaGreen,
                        unfocusedBorderColor = Color.White.copy(0.2f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = MpesaGreen
                    )
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    placeholder = { Text("e.g. 20000", color = TextSecondary.copy(0.5f)) },
                    prefix = { Text("KES ", color = TextSecondary) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MpesaGreen,
                        unfocusedBorderColor = Color.White.copy(0.2f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = MpesaGreen
                    )
                )
                if (error.isNotBlank()) Text(error, color = Color(0xFFFF4444), fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val parsed = amount.toDoubleOrNull()
                when {
                    name.isBlank() -> error = "Enter a name"
                    parsed == null || parsed <= 0 -> error = "Enter a valid amount"
                    else -> onSave(name.trim(), parsed, type)
                }
            }) { Text("Add", color = MpesaGreen) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
        }
    )
}