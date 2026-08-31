package com.example.my_mpesa_tracker.ui.dashboard

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.my_mpesa_tracker.data.model.MpesaTransaction
import com.example.my_mpesa_tracker.util.AdaptiveBudgetEngine
import com.example.my_mpesa_tracker.util.AdaptiveSuggestion
import com.example.my_mpesa_tracker.util.MerchantCategoryEngine
import com.example.my_mpesa_tracker.util.MerchantSubcategory
import androidx.core.content.edit


// ── Budgetable categories ────────────────────────────────────────────────
// Excludes categories that don't make sense as a spending cap: transfers,
// savings/investment (you don't want to LIMIT saving), cash withdrawal,
// overdraft (minimise entirely, not "budget"), and the uncategorised bucket.

val BUDGETABLE_SUBCATEGORIES = listOf(
    MerchantSubcategory.FOOD_DINING,
    MerchantSubcategory.GROCERIES,
    MerchantSubcategory.TRANSPORT,
    MerchantSubcategory.UTILITIES,
    MerchantSubcategory.HEALTH,
    MerchantSubcategory.EDUCATION,
    MerchantSubcategory.ENTERTAINMENT,
    MerchantSubcategory.BETTING,
    MerchantSubcategory.SHOPPING,
    MerchantSubcategory.RENT_HOUSING,
    MerchantSubcategory.BUSINESS_SERVICES,
    MerchantSubcategory.AIRTIME_DATA,
    MerchantSubcategory.BANKING,
    MerchantSubcategory.INSURANCE,
    MerchantSubcategory.LOANS_CREDIT
)

enum class BudgetMode { FIXED, ADAPTIVE }

data class EffectiveBudget(
    val amount: Double,
    val isAdaptive: Boolean,
    val suggestion: AdaptiveSuggestion?
)

// ── Storage ──────────────────────────────────────────────────────────────

object BudgetManager {
    private const val PREFS = "pesalyzer_budget"
    private const val MONTHLY_KEY = "monthly_budget"

    fun getMonthlyBudget(context: Context): Double {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getFloat(MONTHLY_KEY, 0f).toDouble()
    }

    fun setMonthlyBudget(context: Context, amount: Double) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit { putFloat(MONTHLY_KEY, amount.toFloat()) }
    }

    fun getFixedCategoryBudget(context: Context, subcategory: MerchantSubcategory): Double {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getFloat("subcat_${subcategory.name}", 0f).toDouble()
    }

    fun setFixedCategoryBudget(context: Context, subcategory: MerchantSubcategory, amount: Double) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit { putFloat("subcat_${subcategory.name}", amount.toFloat()) }
    }

    fun getCategoryBudgetMode(context: Context, subcategory: MerchantSubcategory): BudgetMode {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("mode_${subcategory.name}", BudgetMode.FIXED.name) ?: BudgetMode.FIXED.name
        return try { BudgetMode.valueOf(raw) } catch (_: Exception) { BudgetMode.FIXED }
    }

    fun setCategoryBudgetMode(context: Context, subcategory: MerchantSubcategory, mode: BudgetMode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit { putString("mode_${subcategory.name}", mode.name) }
    }

    /** Resolves the effective budget for a category regardless of mode.
     * Returns null if no fixed value is set AND (adaptive mode isn't on,
     * or there isn't enough history yet for a suggestion) — meaning
     * "no budget configured for this category," matching the existing
     * silent-skip pattern used everywhere else. */
    fun resolveEffectiveBudget(
        context: Context,
        subcategory: MerchantSubcategory,
        transactions: List<MpesaTransaction>
    ): EffectiveBudget? {
        return when (getCategoryBudgetMode(context, subcategory)) {
            BudgetMode.FIXED -> {
                val fixed = getFixedCategoryBudget(context, subcategory)
                if (fixed > 0) EffectiveBudget(fixed, isAdaptive = false, suggestion = null) else null
            }
            BudgetMode.ADAPTIVE -> {
                AdaptiveBudgetEngine.computeSuggestion(context, subcategory, transactions)
                    ?.let { EffectiveBudget(it.suggestedAmount, isAdaptive = true, suggestion = it) }
            }
        }
    }
}

// ── Budget Alert data ──────────────────────────────────────────────────

data class BudgetAlert(
    val label: String,
    val spent: Double,
    val budget: Double,
    val percentage: Double,
    val isExceeded: Boolean,
    val isAdaptive: Boolean
)

fun computeBudgetAlerts(
    context: Context,
    monthlyBudget: Double,
    totalSpent: Double,
    transactions: List<MpesaTransaction>
): List<BudgetAlert> {
    val alerts = mutableListOf<BudgetAlert>()

    if (monthlyBudget > 0) {
        val pct = (totalSpent / monthlyBudget) * 100
        if (pct >= 70) {
            alerts.add(BudgetAlert("Monthly Total", totalSpent, monthlyBudget, pct, pct >= 100, isAdaptive = false))
        }
    }

    val subcategorySpend = try {
        MerchantCategoryEngine.computeBreakdown(context, transactions).toMap()
    } catch (_: Exception) {
        emptyMap()
    }

    BUDGETABLE_SUBCATEGORIES.forEach { sub ->
        val effective = BudgetManager.resolveEffectiveBudget(context, sub, transactions) ?: return@forEach
        val spent = subcategorySpend[sub] ?: 0.0
        val pct = (spent / effective.amount) * 100
        if (pct >= 70) {
            alerts.add(BudgetAlert(sub.label, spent, effective.amount, pct, pct >= 100, effective.isAdaptive))
        }
    }

    return alerts.sortedByDescending { it.percentage }
}

// ── Budget Alerts Card ─────────────────────────────────────────────────

@Composable
fun BudgetAlertsCard(alerts: List<BudgetAlert>) {
    if (alerts.isEmpty()) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Budget Alerts", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            alerts.forEach { alert -> BudgetAlertRow(alert) }
        }
    }
}

@Composable
fun BudgetAlertRow(alert: BudgetAlert) {
    val color = when {
        alert.isExceeded -> Color(0xFFFF4444)
        alert.percentage >= 90 -> Color(0xFFFF8C00)
        else -> Color(0xFFFFD700)
    }
    val icon = when {
        alert.isExceeded -> "🔴"
        alert.percentage >= 90 -> "🟠"
        else -> "🟡"
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(icon, fontSize = 14.sp)
                Text(alert.label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                if (alert.isAdaptive) {
                    Box(
                        modifier = Modifier
                            .background(MpesaGreen.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text("adaptive", color = MpesaGreen, fontSize = 9.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
            Text("${"%.1f".format(alert.percentage)}%", color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        LinearProgressIndicator(
            progress = { (alert.percentage / 100).toFloat().coerceAtMost(1f) },
            modifier = Modifier.fillMaxWidth().height(4.dp),
            color = color,
            trackColor = Color.White.copy(alpha = 0.1f)
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Spent: ${formatKsh(alert.spent)}", color = TextSecondary, fontSize = 11.sp)
            Text("Budget: ${formatKsh(alert.budget)}", color = TextSecondary, fontSize = 11.sp)
        }
    }
}

// ── Settings: hub + subdialogs ──────────────────────────────────────────
// Instead of one long scrolling AlertDialog with everything crammed in,
// this is a short menu (the "hub"). Each row opens its own focused dialog.
// Cancel / outside-tap on a subdialog returns to the hub; dismissing the
// hub itself closes Settings entirely.

private enum class SettingsSection { SECURITY, MONTHLY_BUDGET, CATEGORY_BUDGETS }

@Composable
fun BudgetSettingsDialog(
    transactions: List<MpesaTransaction>,
    onDismiss: () -> Unit,
    onBudgetsChanged: () -> Unit = {}
) {
    var openSection by remember { mutableStateOf<SettingsSection?>(null) }

    when (openSection) {
        SettingsSection.SECURITY -> {
            SecuritySettingsDialog(onDismiss = { openSection = null })
        }
        SettingsSection.MONTHLY_BUDGET -> {
            MonthlyBudgetDialog(
                onDismiss = { openSection = null },
                onSaved = { openSection = null; onBudgetsChanged() }
            )
        }
        SettingsSection.CATEGORY_BUDGETS -> {
            CategoryBudgetsDialog(
                transactions = transactions,
                onDismiss = { openSection = null },
                onSaved = { openSection = null; onBudgetsChanged() }
            )
        }
        null -> {
            SettingsHubDialog(onDismiss = onDismiss, onOpenSection = { openSection = it })
        }
    }
}

@Composable
private fun SettingsHubDialog(onDismiss: () -> Unit, onOpenSection: (SettingsSection) -> Unit) {
    val context = LocalContext.current

    // Read-only snapshot for the subtitle text — each subdialog reads/writes
    // the real state itself, so this just needs to look right when the hub
    // (re)opens, not stay live while a subdialog is on screen.
    val isLockEnabled = remember { AppLockManager.isLockEnabled(context) }
    val monthlyBudget = remember { BudgetManager.getMonthlyBudget(context) }
    val configuredCount = remember {
        BUDGETABLE_SUBCATEGORIES.count { sub ->
            when (BudgetManager.getCategoryBudgetMode(context, sub)) {
                BudgetMode.FIXED -> BudgetManager.getFixedCategoryBudget(context, sub) > 0
                BudgetMode.ADAPTIVE -> true
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardDark,
        title = { Text("Settings", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SettingsMenuRow(
                    title = "Security",
                    subtitle = if (isLockEnabled) "App lock enabled" else "App lock disabled",
                    onClick = { onOpenSection(SettingsSection.SECURITY) }
                )
                SettingsMenuRow(
                    title = "Monthly Total",
                    subtitle = if (monthlyBudget > 0) formatKsh(monthlyBudget) else "Not set",
                    onClick = { onOpenSection(SettingsSection.MONTHLY_BUDGET) }
                )
                SettingsMenuRow(
                    title = "Per Category",
                    subtitle = "$configuredCount of ${BUDGETABLE_SUBCATEGORIES.size} configured",
                    onClick = { onOpenSection(SettingsSection.CATEGORY_BUDGETS) }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done", color = MpesaGreen) }
        }
    )
}

@Composable
private fun SettingsMenuRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .background(Color.White.copy(alpha = 0.03f))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = TextSecondary, fontSize = 11.sp)
        }
        Text("›", color = TextSecondary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

// ── Security subdialog ──────────────────────────────────────────────────

@Composable
private fun SecuritySettingsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var showPinSetup by remember { mutableStateOf(false) }
    var isLockEnabled by remember { mutableStateOf(AppLockManager.isLockEnabled(context)) }
    var isBiometricEnabled by remember { mutableStateOf(AppLockManager.isBiometricEnabled(context)) }

    if (showPinSetup) {
        PinSetupDialog(
            onDismiss = { showPinSetup = false },
            onPinSet = { pin ->
                AppLockManager.setPin(context, pin)
                isLockEnabled = true
                showPinSetup = false
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardDark,
        title = { Text("Security", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            if (isLockEnabled) "App Lock Enabled" else "App Lock Disabled",
                            color = Color.White,
                            fontSize = 14.sp
                        )
                        Text(
                            if (isLockEnabled) "Requires authorization on launch" else "Anyone can open the app",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }

                    if (isLockEnabled) {
                        TextButton(onClick = {
                            AppLockManager.disableLock(context)
                            isLockEnabled = false
                            isBiometricEnabled = false
                        }) {
                            Text("Disable", color = Color(0xFFFF6B6B), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        TextButton(onClick = { showPinSetup = true }) {
                            Text("Set PIN", color = MpesaGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (isLockEnabled && AppLockManager.isBiometricAvailable(context)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Use Fingerprint", color = Color.White, fontSize = 14.sp)
                            Text("Use biometric hardware signature", color = TextSecondary, fontSize = 11.sp)
                        }
                        Switch(
                            checked = isBiometricEnabled,
                            onCheckedChange = { checked ->
                                isBiometricEnabled = checked
                                AppLockManager.enableBiometric(context, checked)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MpesaGreen,
                                checkedTrackColor = MpesaGreen.copy(0.3f),
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = Color.White.copy(0.1f)
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done", color = MpesaGreen) }
        }
    )
}

// ── Monthly total subdialog ─────────────────────────────────────────────

@Composable
private fun MonthlyBudgetDialog(onDismiss: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    var monthlyBudget by remember {
        mutableStateOf(
            BudgetManager.getMonthlyBudget(context).let { if (it > 0) it.toLong().toString() else "" }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardDark,
        title = { Text("Monthly Total", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Overall spending cap for the month", color = TextSecondary, fontSize = 12.sp)
                OutlinedTextField(
                    value = monthlyBudget,
                    onValueChange = { monthlyBudget = it },
                    placeholder = { Text("e.g. 15000", color = TextSecondary.copy(0.5f)) },
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
            }
        },
        confirmButton = {
            TextButton(onClick = {
                monthlyBudget.toDoubleOrNull()?.let { BudgetManager.setMonthlyBudget(context, it) }
                onSaved()
            }) { Text("Save", color = MpesaGreen) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
        }
    )
}

// ── Per-category subdialog ──────────────────────────────────────────────

@Composable
private fun CategoryBudgetsDialog(
    transactions: List<MpesaTransaction>,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    var fixedValues by remember {
        mutableStateOf(
            BUDGETABLE_SUBCATEGORIES.associateWith { sub ->
                BudgetManager.getFixedCategoryBudget(context, sub).let { if (it > 0) it.toLong().toString() else "" }
            }
        )
    }
    var modes by remember {
        mutableStateOf(BUDGETABLE_SUBCATEGORIES.associateWith { BudgetManager.getCategoryBudgetMode(context, it) })
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardDark,
        title = { Text("Per Category", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                BUDGETABLE_SUBCATEGORIES.forEach { sub ->
                    val mode = modes[sub] ?: BudgetMode.FIXED
                    val isAdaptive = mode == BudgetMode.ADAPTIVE

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${sub.emoji} ${sub.label}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(if (isAdaptive) "Adaptive" else "Fixed", color = TextSecondary, fontSize = 10.sp)
                                Switch(
                                    checked = isAdaptive,
                                    onCheckedChange = { checked ->
                                        val newMode = if (checked) BudgetMode.ADAPTIVE else BudgetMode.FIXED
                                        modes = modes.toMutableMap().also { it[sub] = newMode }
                                        BudgetManager.setCategoryBudgetMode(context, sub, newMode)
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = MpesaGreen,
                                        checkedTrackColor = MpesaGreen.copy(alpha = 0.4f)
                                    ),
                                    modifier = Modifier.scale(0.75f)
                                )
                            }
                        }

                        Spacer(Modifier.height(6.dp))

                        if (isAdaptive) {
                            val suggestion = remember(transactions, sub) {
                                AdaptiveBudgetEngine.computeSuggestion(context, sub, transactions)
                            }
                            if (suggestion != null) {
                                Text(
                                    "Suggested: ${formatKsh(suggestion.suggestedAmount)}",
                                    color = MpesaGreen,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "Based on your last ${suggestion.monthsUsed} months: " +
                                            suggestion.recentMonthlyTotals.joinToString(", ") { formatKsh(it) },
                                    color = TextSecondary,
                                    fontSize = 10.sp
                                )
                            } else {
                                Text(
                                    "Not enough history yet — need at least 2 complete months in this category. Using Fixed for now.",
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        } else {
                            OutlinedTextField(
                                value = fixedValues[sub] ?: "",
                                onValueChange = { new ->
                                    fixedValues = fixedValues.toMutableMap().also { it[sub] = new }
                                },
                                placeholder = { Text("KES", color = TextSecondary.copy(0.4f), fontSize = 12.sp) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MpesaGreen,
                                    unfocusedBorderColor = Color.White.copy(0.2f),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    cursorColor = MpesaGreen
                                ),
                                textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                fixedValues.forEach { (sub, value) ->
                    val parsed = value.toDoubleOrNull() ?: 0.0
                    BudgetManager.setFixedCategoryBudget(context, sub, parsed)
                }
                onSaved()
            }) { Text("Save", color = MpesaGreen) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
        }
    )
}