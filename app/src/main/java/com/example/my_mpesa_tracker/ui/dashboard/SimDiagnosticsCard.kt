package com.example.my_mpesa_tracker.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.my_mpesa_tracker.data.db.SubscriptionSummary
import com.example.my_mpesa_tracker.util.SimPreferenceManager
import java.text.SimpleDateFormat
import java.util.Date

/**
 * Collapsed by default — a single tappable line, not a permanent
 * dashboard fixture. Only expands to show controls when the person
 * actively wants to review it. Delete is a small icon per row rather
 * than a full-width button, reducing accidental taps during normal
 * scrolling.
 */
@Composable
fun SimDiagnosticsCard(vm: DashboardViewModel) {
    val context = LocalContext.current
    var summary by remember { mutableStateOf<List<SubscriptionSummary>?>(null) }
    var refreshTrigger by remember { mutableIntStateOf(0) }
    var expanded by remember { mutableStateOf(false) }
    var confirmDeleteSubId by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(refreshTrigger) {
        vm.getSimDiagnostics { summary = it }
    }

    val groups = summary ?: return
    if (groups.size <= 1) return // nothing to configure, invisible by design

    var included by remember(groups) {
        mutableStateOf(
            if (SimPreferenceManager.hasBeenConfigured(context)) {
                SimPreferenceManager.getIncludedSubscriptions(context)
            } else {
                setOf(groups.maxByOrNull { it.maxTs }?.subscriptionId ?: -1)
            }
        )
    }

    val dateFmt = SimpleDateFormat("d MMM yyyy", LocalLocale.current.platformLocale)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark)
    ) {
        Column(Modifier.padding(if (expanded) 16.dp else 12.dp)) {

            // Collapsed banner — always visible, minimal footprint
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "📶 ${groups.size} SIM lines detected — tap to review",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
                Text(if (expanded) "▲" else "▼", color = TextSecondary, fontSize = 11.sp)
            }

            if (expanded) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Choose which line(s) feed your Balance Tracker and Forecast",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
                Spacer(Modifier.height(10.dp))

                groups.forEachIndexed { i, group ->
                    val label = if (group.subscriptionId == -1) "Unknown SIM (older data)" else "SIM line ${i + 1}"
                    val isIncluded = group.subscriptionId in included

                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text(
                                "${group.count} tx · ${dateFmt.format(Date(group.minTs))} – ${dateFmt.format(Date(group.maxTs))}",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = isIncluded,
                            onCheckedChange = { checked ->
                                included = if (checked) included + group.subscriptionId
                                else included - group.subscriptionId
                                SimPreferenceManager.setIncludedSubscriptions(context, included)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MpesaGreen,
                                checkedTrackColor = MpesaGreen.copy(alpha = 0.4f)
                            )
                        )
                        Spacer(Modifier.width(4.dp))
                        IconButton(
                            onClick = { confirmDeleteSubId = group.subscriptionId },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Default.DeleteOutline,
                                contentDescription = "Delete this line's data",
                                tint = TextSecondary.copy(alpha = 0.6f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    confirmDeleteSubId?.let { subId ->
        val group = groups.find { it.subscriptionId == subId }
        AlertDialog(
            onDismissRequest = { confirmDeleteSubId = null },
            containerColor = CardDark,
            title = { Text("Delete this line's data?", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "This permanently removes ${group?.count ?: 0} transactions belonging to this SIM line. This cannot be undone.",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.purgeStaleSubscription(subId)
                    included = included - subId
                    SimPreferenceManager.setIncludedSubscriptions(context, included)
                    confirmDeleteSubId = null
                    refreshTrigger++
                }) { Text("Delete", color = Color(0xFFFF4444)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteSubId = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}
