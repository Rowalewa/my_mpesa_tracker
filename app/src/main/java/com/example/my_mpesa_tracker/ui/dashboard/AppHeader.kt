package com.example.my_mpesa_tracker.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.my_mpesa_tracker.data.model.MpesaTransaction
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// ── App-wide header: title bar + period selector. Rendered once, above the ──
// ── tab content in MainActivity, so it's frozen and shared by every screen. ──

@Composable
fun Header(transactions: List<MpesaTransaction>) {
    var showBudgetSettings by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Pesalyzer", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 19.sp)
        IconButton(onClick = { showBudgetSettings = true }) {
            Icon(Icons.Default.Settings, contentDescription = "Budget", tint = MpesaGreen)
        }
    }

    if (showBudgetSettings) {
        BudgetSettingsDialog(
            transactions = transactions,
            onDismiss = { showBudgetSettings = false }
        )
    }
}

@Composable
fun PeriodSelector(selected: Period, onSelect: (Period) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(Period.entries) { period ->
            val isSelected = period == selected
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(period) },
                shape = RoundedCornerShape(50),
                label = {
                    Text(
                        period.label(),
                        fontSize = 13.sp,
                        color = if (isSelected) Color.White else TextSecondary
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MpesaGreen,
                    containerColor = CardDark
                )
            )
        }
    }
}

// ── Custom Date Range Dialog ──────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomDateRangeDialog(
    onDismiss: () -> Unit,
    onConfirm: (LocalDate, LocalDate) -> Unit
) {
    var isPickingFromPhase by remember { mutableStateOf(true) }

    val fromDatePickerState = rememberDatePickerState()
    val toDatePickerState = rememberDatePickerState()
    val formatter = remember { DateTimeFormatter.ofPattern("dd MMM yyyy") }

    val fromLocalDate = fromDatePickerState.selectedDateMillis?.let {
        Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
    }
    val toLocalDate = toDatePickerState.selectedDateMillis?.let {
        Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
    }

    val customDatePickerColors = DatePickerDefaults.colors(
        containerColor = CardDark,
        titleContentColor = Color.White,
        headlineContentColor = Color.White,
        weekdayContentColor = TextSecondary,
        subheadContentColor = TextSecondary,
        navigationContentColor = Color.White,
        yearContentColor = TextSecondary,
        disabledYearContentColor = TextSecondary.copy(alpha = 0.3f),
        selectedYearContentColor = Color.White,
        selectedYearContainerColor = MpesaGreen,
        dayContentColor = Color.White,
        disabledDayContentColor = TextSecondary.copy(alpha = 0.3f),
        selectedDayContentColor = Color.White,
        selectedDayContainerColor = MpesaGreen,
        todayContentColor = MpesaGreen,
        todayDateBorderColor = MpesaGreen
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = CardDark,
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .wrapContentHeight()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = if (isPickingFromPhase) "STEP 1: CHOOSE START DATE (FROM)" else "STEP 2: CHOOSE END DATE (TO)",
                    color = MpesaGreen,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                ) {
                    if (isPickingFromPhase) {
                        DatePicker(
                            state = fromDatePickerState,
                            showModeToggle = false,
                            colors = customDatePickerColors,
                            title = null,
                            headline = null,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        DatePicker(
                            state = toDatePickerState,
                            showModeToggle = false,
                            colors = customDatePickerColors,
                            title = null,
                            headline = null,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 1.dp)

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("From:", color = TextSecondary, fontSize = 11.sp)
                        Text(
                            text = fromLocalDate?.format(formatter) ?: "Not Selected",
                            color = if (fromLocalDate != null) Color.White else TextSecondary.copy(alpha = 0.5f),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("To:", color = TextSecondary, fontSize = 11.sp)
                        Text(
                            text = toLocalDate?.format(formatter) ?: "Not Selected",
                            color = if (toLocalDate != null) Color.White else TextSecondary.copy(alpha = 0.5f),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = TextSecondary)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    if (isPickingFromPhase) {
                        Button(
                            onClick = { isPickingFromPhase = false },
                            enabled = fromLocalDate != null,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MpesaGreen,
                                disabledContainerColor = Color.White.copy(alpha = 0.05f)
                            )
                        ) {
                            Text("Next", color = if (fromLocalDate != null) Color.White else TextSecondary.copy(alpha = 0.5f))
                        }
                    } else {
                        TextButton(onClick = { isPickingFromPhase = true }) {
                            Text("Back", color = MpesaGreen)
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        Button(
                            onClick = {
                                if (fromLocalDate != null && toLocalDate != null && !fromLocalDate.isAfter(
                                        toLocalDate
                                    )) {
                                    onConfirm(fromLocalDate, toLocalDate)
                                }
                            },
                            enabled = fromLocalDate != null && toLocalDate != null && !fromLocalDate.isAfter(toLocalDate),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MpesaGreen,
                                disabledContainerColor = Color.White.copy(alpha = 0.05f)
                            )
                        ) {
                            Text("Apply Range", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}
