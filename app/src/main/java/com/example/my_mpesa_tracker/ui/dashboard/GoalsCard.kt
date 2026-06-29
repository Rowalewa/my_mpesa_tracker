package com.example.my_mpesa_tracker.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.my_mpesa_tracker.util.GoalContribution
import com.example.my_mpesa_tracker.util.GoalManager
import com.example.my_mpesa_tracker.util.SavingsGoal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val GOAL_EMOJIS = listOf("🎯", "💰", "🏠", "🚗", "📱", "🎓", "✈️", "💍", "🏥", "📚")

@Composable
fun GoalsCard() {
    val context = LocalContext.current
    var refreshTrigger by remember { mutableIntStateOf(0) }
    val goals = remember(refreshTrigger) { GoalManager.getAllGoals(context) }
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedGoal by remember { mutableStateOf<SavingsGoal?>(null) }

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
                Text("Savings Goals", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                IconButton(onClick = { showAddDialog = true }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Add, contentDescription = "Add goal", tint = MpesaGreen)
                }
            }

            if (goals.isEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "No goals yet — set one to start tracking progress toward something specific.",
                    color = ChartLabel,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            } else {
                Spacer(Modifier.height(12.dp))
                goals.forEachIndexed { i, goal ->
                    GoalRow(goal = goal, onClick = { selectedGoal = goal })
                    if (i < goals.lastIndex) Spacer(Modifier.height(16.dp))
                }
            }
        }
    }

    if (showAddDialog) {
        AddGoalDialog(
            onDismiss = { showAddDialog = false },
            onSave = { name, emoji, amount, date ->
                GoalManager.createGoal(context, name, emoji, amount, date)
                refreshTrigger++
                showAddDialog = false
            }
        )
    }

    selectedGoal?.let { goal ->
        GoalDetailDialog(
            goal = goal,
            onDismiss = { selectedGoal = null },
            onContribute = { amount, note ->
                GoalManager.addContribution(context, goal.id, GoalContribution(amount, LocalDate.now(), note))
                refreshTrigger++
                selectedGoal = GoalManager.getAllGoals(context).find { it.id == goal.id }
            },
            onDelete = {
                GoalManager.deleteGoal(context, goal.id)
                refreshTrigger++
                selectedGoal = null
            }
        )
    }
}

@Composable
fun GoalRow(goal: SavingsGoal, onClick: () -> Unit) {
    val barColor = if (goal.isComplete) ChartGreen else MpesaGreen

    Column(Modifier.fillMaxWidth().clickable { onClick() }) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(goal.emoji, fontSize = 18.sp)
                Column {
                    Text(goal.name, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                    goal.targetDate?.let {
                        Text(
                            "Target: ${it.format(DateTimeFormatter.ofPattern("d MMM yyyy"))}",
                            color = ChartLabel,
                            fontSize = 11.sp
                        )
                    }
                }
            }
            Text(
                "${(goal.progress * 100).toInt()}%",
                color = barColor,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { goal.progress },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = barColor,
            trackColor = Color.White.copy(alpha = 0.08f)
        )
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "${formatKsh(goal.totalSaved)} of ${formatKsh(goal.targetAmount)}",
                color = ChartLabel,
                fontSize = 11.sp
            )
            goal.projectedCompletionDate?.let {
                Text(
                    "Est. ${it.format(DateTimeFormatter.ofPattern("MMM yyyy"))}",
                    color = ChartLabel,
                    fontSize = 11.sp
                )
            }
        }
    }
}

// ── Add Goal Dialog ────────────────────────────────────────────────────────

@Composable
fun AddGoalDialog(onDismiss: () -> Unit, onSave: (String, String, Double, LocalDate?) -> Unit) {
    var name by remember { mutableStateOf("") }
    var selectedEmoji by remember { mutableStateOf(GOAL_EMOJIS.first()) }
    var amount by remember { mutableStateOf("") }
    var targetDate by remember { mutableStateOf<LocalDate?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardDark,
        title = { Text("New Savings Goal", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(GOAL_EMOJIS) { emoji ->
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    if (emoji == selectedEmoji) MpesaGreen.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f),
                                    CircleShape
                                )
                                .clickable { selectedEmoji = emoji },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(emoji, fontSize = 18.sp)
                        }
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("e.g. Laptop deposit", color = TextSecondary.copy(0.5f)) },
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

                Row(
                    Modifier.fillMaxWidth().clickable { showDatePicker = true },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Target date (optional)", color = TextSecondary, fontSize = 13.sp)
                    Text(
                        targetDate?.format(DateTimeFormatter.ofPattern("d MMM yyyy")) ?: "None",
                        color = if (targetDate != null) MpesaGreen else TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (error.isNotBlank()) {
                    Text(error, color = Color(0xFFFF4444), fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val parsedAmount = amount.toDoubleOrNull()
                when {
                    name.isBlank() -> error = "Enter a goal name"
                    parsedAmount == null || parsedAmount <= 0 -> error = "Enter a valid amount"
                    else -> onSave(name.trim(), selectedEmoji, parsedAmount, targetDate)
                }
            }) { Text("Create", color = MpesaGreen) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
        }
    )

    if (showDatePicker) {
        SingleDatePickerDialog(
            onDismiss = { showDatePicker = false },
            onConfirm = { date ->
                targetDate = date
                showDatePicker = false
            }
        )
    }
}

// ── Goal Detail Dialog ─────────────────────────────────────────────────────

@Composable
fun GoalDetailDialog(
    goal: SavingsGoal,
    onDismiss: () -> Unit,
    onContribute: (Double, String) -> Unit,
    onDelete: () -> Unit
) {
    var showAddContribution by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = CardDark,
            modifier = Modifier.fillMaxWidth(0.95f).heightIn(max = 560.dp)
        ) {
            Column(Modifier.padding(20.dp)) {

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(goal.emoji, fontSize = 22.sp)
                        Text(goal.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    }
                    IconButton(onClick = { confirmDelete = true }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFFF6B6B))
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Progress ring
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(90.dp), contentAlignment = Alignment.Center) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val stroke = 8.dp.toPx()
                            val diameter = size.minDimension - stroke
                            val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
                            val arcSize = Size(diameter, diameter)
                            drawArc(
                                color = Color.White.copy(alpha = 0.08f),
                                startAngle = -90f, sweepAngle = 360f, useCenter = false,
                                topLeft = topLeft, size = arcSize,
                                style = Stroke(width = stroke, cap = StrokeCap.Round)
                            )
                            drawArc(
                                color = if (goal.isComplete) ChartGreen else MpesaGreen,
                                startAngle = -90f, sweepAngle = goal.progress * 360f, useCenter = false,
                                topLeft = topLeft, size = arcSize,
                                style = Stroke(width = stroke, cap = StrokeCap.Round)
                            )
                        }
                        Text(
                            "${(goal.progress * 100).toInt()}%",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(formatKsh(goal.totalSaved), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("of ${formatKsh(goal.targetAmount)}", color = TextSecondary, fontSize = 12.sp)
                        Spacer(Modifier.height(6.dp))
                        goal.projectedCompletionDate?.let {
                            Text(
                                "Est. completion: ${it.format(DateTimeFormatter.ofPattern("d MMM yyyy"))}",
                                color = ChartLabel, fontSize = 11.sp
                            )
                        }
                        goal.isOnTrack?.let { onTrack ->
                            Text(
                                if (onTrack) "✓ On track" else "⚠ Behind schedule",
                                color = if (onTrack) ChartGreen else Color(0xFFFFA726),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                Button(
                    onClick = { showAddContribution = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MpesaGreen),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("+ Add Contribution", color = Color.White, fontWeight = FontWeight.SemiBold)
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                Spacer(Modifier.height(12.dp))

                Text("History", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))

                if (goal.contributions.isEmpty()) {
                    Text("No contributions logged yet.", color = ChartLabel, fontSize = 12.sp)
                } else {
                    androidx.compose.foundation.lazy.LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        items(goal.contributions.sortedByDescending { it.date }) { c ->
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(formatKsh(c.amount), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    if (c.note.isNotBlank()) {
                                        Text(c.note, color = ChartLabel, fontSize = 11.sp)
                                    }
                                }
                                Text(
                                    c.date.format(DateTimeFormatter.ofPattern("d MMM yyyy")),
                                    color = ChartLabel,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("Close", color = TextSecondary)
                }
            }
        }
    }

    if (showAddContribution) {
        AddContributionDialog(
            onDismiss = { showAddContribution = false },
            onSave = { amount, note ->
                onContribute(amount, note)
                showAddContribution = false
            }
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = CardDark,
            title = { Text("Delete goal?", color = Color.White, fontWeight = FontWeight.Bold) },
            text = { Text("This removes \"${goal.name}\" and its full contribution history. This cannot be undone.", color = TextSecondary, fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = onDelete) { Text("Delete", color = Color(0xFFFF4444)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel", color = TextSecondary) }
            }
        )
    }
}

@Composable
fun AddContributionDialog(onDismiss: () -> Unit, onSave: (Double, String) -> Unit) {
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardDark,
        title = { Text("Add Contribution", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    placeholder = { Text("e.g. 500", color = TextSecondary.copy(0.5f)) },
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
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    placeholder = { Text("Note (optional)", color = TextSecondary.copy(0.5f)) },
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
                if (error.isNotBlank()) Text(error, color = Color(0xFFFF4444), fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val parsed = amount.toDoubleOrNull()
                if (parsed == null || parsed <= 0) {
                    error = "Enter a valid amount"
                } else {
                    onSave(parsed, note.trim())
                }
            }) { Text("Save", color = MpesaGreen) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
        }
    )
}

// ── Reusable single date picker ───────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SingleDatePickerDialog(onDismiss: () -> Unit, onConfirm: (LocalDate) -> Unit) {
    val state = rememberDatePickerState()
    val selected = state.selectedDateMillis?.let {
        Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = CardDark,
            modifier = Modifier.fillMaxWidth(0.95f)
        ) {
            Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                DatePicker(
                    state = state,
                    showModeToggle = false,
                    title = null,
                    headline = null,
                    colors = DatePickerDefaults.colors(
                        containerColor = CardDark,
                        titleContentColor = Color.White,
                        headlineContentColor = Color.White,
                        weekdayContentColor = TextSecondary,
                        subheadContentColor = TextSecondary,
                        navigationContentColor = Color.White,
                        yearContentColor = TextSecondary,
                        selectedYearContentColor = Color.White,
                        selectedYearContainerColor = MpesaGreen,
                        dayContentColor = Color.White,
                        selectedDayContentColor = Color.White,
                        selectedDayContainerColor = MpesaGreen,
                        todayContentColor = MpesaGreen,
                        todayDateBorderColor = MpesaGreen
                    )
                )
                Row(
                    Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
                    TextButton(
                        onClick = { selected?.let(onConfirm) },
                        enabled = selected != null
                    ) { Text("Set", color = MpesaGreen) }
                }
            }
        }
    }
}
