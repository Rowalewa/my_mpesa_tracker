package com.example.my_mpesa_tracker.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.my_mpesa_tracker.data.model.MpesaTransaction
import com.example.my_mpesa_tracker.data.model.TransactionType
import com.example.my_mpesa_tracker.util.SpendingStats
import com.example.my_mpesa_tracker.util.filterByIncludedSims
import com.example.my_mpesa_tracker.util.label
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

val MpesaGreen = Color(0xFF00A550)
val MpesaGreenDark = Color(0xFF007A3C)
val SurfaceDark = Color(0xFF1A1A2E)
val CardDark = Color(0xFF16213E)
val TextSecondary = Color(0xFFB0B8C8)

@Composable
fun DashboardScreen(vm: DashboardViewModel = viewModel()) {
    val state by vm.uiState.collectAsState()
    var showDatePicker by remember { mutableStateOf(false) }
    var showTransactionDetail by remember { mutableStateOf<MpesaTransaction?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        vm.syncMpesaSms()
    }

    if (state.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = MpesaGreen)
                Spacer(Modifier.height(12.dp))
                Text("Loading M-Pesalyzer...", color = TextSecondary, fontSize = 13.sp)
            }
        }
        return
    }

    // Transaction detail dialog
    showTransactionDetail?.let { tx ->
        TransactionDetailDialog(tx = tx, onDismiss = { showTransactionDetail = null })
    }

    // Custom date picker dialog
    if (showDatePicker) {
        CustomDateRangeDialog(
            onDismiss = { showDatePicker = false },
            onConfirm = { from, to ->
                vm.setCustomRange(from, to)
                showDatePicker = false
            }
        )
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            isRefreshing = true
            vm.refresh()
            scope.launch {
                delay(800.milliseconds)
                isRefreshing = false
            }
        } ,
        modifier = Modifier.fillMaxSize()
    ){
        LazyColumn(
            modifier = Modifier.fillMaxSize().background(SurfaceDark),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Header(transactions = state.allTransactions) }

            item { SimDiagnosticsCard(vm = vm) }

            item {
                PeriodSelector(
                    selected = state.selectedPeriod,
                    onSelect = { if (it == Period.CUSTOM) showDatePicker = true else vm.selectPeriod(it) }
                )
            }

            // Show importing banner if history is being loaded
            if (state.isImporting) {
                item { ImportingHistoryState() }
            }

            if (state.dateLabel.isNotEmpty()) {
                item {
                    Text(
                        text = state.dateLabel,
                        color = MpesaGreen,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        fontFamily = FontFamily.Serif,
                        fontStyle = FontStyle.Italic
                    )
                }
            }

            item { NetFlowCard(state.stats) }

            if (state.selectedPeriod == Period.MONTH) {
                item { ForecastCard(thisMonthTransactions = state.allTransactions.filterByIncludedSims(LocalContext.current)) }
            }

            item { FinancialHealthCard(transactions = state.allTransactions, dailyTotals = state.dailyChart) }

            item { NetWorthCard(transactions = state.allTransactions.filterByIncludedSims(LocalContext.current)) }

            item {
                DetectedGapsCard(
                    transactions = state.allTransactions,
                    onTransactionClick = { tx -> showTransactionDetail = tx }
                )
            }

            item {
                SpendingHeatmapCard(
                    transactions = state.allTransactions,
                    onDaySelected = { date -> vm.setCustomRange(date, date) }
                )
            }

            item { GoalsCard() }

            item { SubcategoryBreakdownCard(transactions = state.allTransactions) }

            item {
                AnomalyAlertsCard(
                    transactions = state.allTransactions,
                    onTransactionClick = { tx -> showTransactionDetail = tx }
                )
            }

            // Line graph
            if (state.dailyChart.size > 1) {
                item { LineChartCard(state.dailyChart) }
            }

            item {
                BeautifulBarChartCard(
                    incomeAmount = state.stats.totalReceived,
                    expenseAmount = state.stats.totalSpent,
                    dateLabel = state.dateLabel
                )
            }

            item { StatsGrid(state.stats) }

            // Insights
            item { InsightsCard(state.insights) }

            item { CategoryBreakdown(state.stats.byCategory) }

            // Balance tracker
            item { BalanceTrackerCard(transactions = state.allTransactions.filterToCurrentSim()) }

// Budget alerts
            item {
                val context = LocalContext.current
                val alerts = remember(state.allTransactions) {
                    computeBudgetAlerts(
                        context = context,
                        monthlyBudget = BudgetManager.getMonthlyBudget(context),
                        totalSpent = state.stats.totalSpent,
                        transactions = state.allTransactions
                    )
                }
                BudgetAlertsCard(alerts = alerts)
            }

// Recurring transactions
            item { RecurringTransactionsCard(transactions = state.allTransactions) }

            if (state.selectedCategory == TransactionType.RECEIVE_MONEY) {
                item {
                    InflowSenderBreakdown(transactions = state.filteredTransactions)
                }
            }

            // Search + filter
            item {
                SearchBar(
                    query = state.searchQuery,
                    onQueryChange = vm::setSearch
                )
            }

            item {
                CategoryFilterRow(
                    selected = state.selectedCategory,
                    onSelect = vm::setCategory
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Transactions (${state.filteredTransactions.size})",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    if (state.searchQuery.isNotBlank() || state.selectedCategory != null) {
                        Text(
                            "Clear filters",
                            color = MpesaGreen,
                            fontSize = 13.sp,
                            modifier = Modifier.clickable {
                                vm.setSearch("")
                                vm.setCategory(null)
                            }
                        )
                    }
                }
            }

            if (state.filteredTransactions.isEmpty()) {
                item {
                    if (state.allTransactions.isEmpty()) {
                        EmptyTransactionsState()
                    } else if (state.searchQuery.isNotBlank()) {
                        EmptySearchState(query = state.searchQuery)
                    } else {
                        EmptyPeriodState(period = state.dateLabel)
                    }
                }
            } else {
                items(state.filteredTransactions) { tx ->
                    TransactionRow(tx = tx, onClick = { showTransactionDetail = tx })
                }
            }
        }
    }
}


// ── Insights Card ────────────────────────────────────────────────────────────

@Composable
fun InsightsCard(insights: InsightData) {
    if (insights.topSender.isBlank() && insights.topReceiver.isBlank()) return
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Insights", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)

            if (insights.mostUsedCategory.isNotBlank())
                InsightRow("🏆", "Most used category", insights.mostUsedCategory)
            if (insights.topSender.isNotBlank())
                InsightRow("📤", "Most sent to", insights.topSender)
            if (insights.topReceiver.isNotBlank())
                InsightRow("📥", "Most received from", insights.topReceiver)
            if (insights.busiestDay.isNotBlank())
                InsightRow("📅", "Busiest day", insights.busiestDay)
            if (insights.biggestSpendDay.isNotBlank())
                InsightRow("💸", "Highest spend day", insights.biggestSpendDay)
            if (insights.highestInflowDay.isNotBlank())
                InsightRow("💰", "Highest income day", insights.highestInflowDay)

            if (insights.topByCategory.isNotEmpty()) {
                HorizontalDivider(
                    Modifier,
                    DividerDefaults.Thickness,
                    color = Color.White.copy(alpha = 0.08f)
                )
                Text("Top per category", color = TextSecondary, fontSize = 12.sp)
                insights.topByCategory.entries.forEach { (cat, name) ->
                    if (name.isNotBlank()) InsightRow("·", cat, name)
                }
            }
        }
    }
}

@Composable
fun InsightRow(icon: String, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = 14.sp)
            Text(label, color = TextSecondary, fontSize = 13.sp)
        }
        Text(value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.widthIn(max = 180.dp), maxLines = 1)
    }
}

// ── Search Bar ───────────────────────────────────────────────────────────────

@Composable
fun SearchBar(query: String, onQueryChange: (String) -> Unit) {
    var localQuery by remember { mutableStateOf(query) }
    val focusManager = LocalFocusManager.current

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        OutlinedTextField(
            value = localQuery,
            onValueChange = {
                localQuery = it
                onQueryChange(it)
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search by name, amount, code...", color = TextSecondary, fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary) },
            trailingIcon = {
                if (query.isNotBlank()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = TextSecondary)
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MpesaGreen,
                unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = MpesaGreen,
                focusedContainerColor = CardDark,
                unfocusedContainerColor = CardDark
            ),
            textStyle = LocalTextStyle.current.copy(
                textDirection = TextDirection.Ltr,
                textAlign = TextAlign.Start
            ),
            shape = RoundedCornerShape(12.dp)
        )
    }
}

// ── Category Filter ──────────────────────────────────────────────────────────

@Composable
fun CategoryFilterRow(selected: TransactionType?, onSelect: (TransactionType?) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            FilterChip(
                selected = selected == null,
                onClick = { onSelect(null) },
                label = { Text("All", fontSize = 12.sp, color = if (selected == null) Color.White else TextSecondary) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MpesaGreen,
                    containerColor = CardDark
                )
            )
        }
        items(TransactionType.entries) { type ->
            FilterChip(
                selected = selected == type,
                onClick = { onSelect(if (selected == type) null else type) },
                label = {
                    Text(
                        "${type.emoji()} ${type.label()}",
                        fontSize = 12.sp,
                        color = if (selected == type) Color.White else TextSecondary
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

// ── Transaction Detail Dialog ─────────────────────────────────────────────────

@Composable
fun TransactionDetailDialog(tx: MpesaTransaction, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardDark,
        title = {
            Text(tx.counterparty, color = Color.White, fontWeight = FontWeight.Bold)
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
        Text(value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
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

// ── Existing composables ────────────────────────────────────────────────────

@Composable
fun Header(transactions: List<MpesaTransaction>) {
    var showBudgetSettings by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text("Pesalyzer", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            Text("Live spending analysis", color = TextSecondary, fontSize = 13.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { showBudgetSettings = true }) {
                Icon(Icons.Default.Settings, contentDescription = "Budget", tint = MpesaGreen)
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MpesaGreen, RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("P", color = Color.White, fontWeight = FontWeight.Bold)
            }
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

@Composable
fun NetFlowCard(stats: SpendingStats) {
    val isPositive = stats.netFlow >= 0
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (isPositive) MpesaGreenDark else Color(0xFF8B0000))
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("Net Cash Flow", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            Text(formatKsh(stats.netFlow), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 32.sp)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                FlowItem("In", stats.totalReceived, Color(0xFF90EE90))
                FlowItem("Out", stats.totalSpent, Color(0xFFFFB3B3))
            }
        }
    }
}

@Composable
fun FlowItem(label: String, amount: Double, color: Color) {
    Column {
        Text(label, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
        Text(formatKsh(amount), color = color, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}

@Composable
fun StatsGrid(stats: SpendingStats) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Max", formatKsh(stats.maxTransaction), Modifier.weight(1f))
            StatCard("Min", formatKsh(stats.minTransaction), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Average", formatKsh(stats.avgTransaction), Modifier.weight(1f))
            StatCard("Transactions", stats.transactionCount.toString(), Modifier.weight(1f))
        }
    }
}

@Composable
fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier, shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(label, color = TextSecondary, fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
    }
}

@Composable
fun CategoryBreakdown(byCategory: Map<String, Double>) {
    if (byCategory.isEmpty()) return
    Card(
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Spending by Category", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.height(12.dp))
            val total = byCategory.values.sum()
            byCategory.entries.sortedByDescending { it.value }.forEach { (cat, amount) ->
                val pct = if (total > 0) (amount / total).toFloat() else 0f
                CategoryRow(cat, amount, pct)
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun CategoryRow(label: String, amount: Double, fraction: Float) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = TextSecondary, fontSize = 13.sp)
            Text(formatKsh(amount), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth().height(4.dp),
            color = MpesaGreen,
            trackColor = Color.White.copy(alpha = 0.1f)
        )
    }
}

@Composable
fun InflowSenderBreakdown(transactions: List<MpesaTransaction>) {
    // Dynamically calculate the breakdown by sender name
    val senderBreakdown = remember(transactions) {
        transactions
            .groupBy { it.counterparty }
            .mapValues { entry -> entry.value.sumOf { tx -> tx.amount } }
            .entries
            .sortedByDescending { it.value }
    }

    if (senderBreakdown.isEmpty()) return
    val totalInflow = remember(senderBreakdown) { senderBreakdown.sumOf { it.value } }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = "Inflow Breakdown by Sender",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            Spacer(Modifier.height(12.dp))

            senderBreakdown.forEach { (sender, amount) ->
                val fraction = if (totalInflow > 0) (amount / totalInflow).toFloat() else 0f

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(sender, color = TextSecondary, fontSize = 13.sp, maxLines = 1)
                        Text(
                            text = formatKsh(amount),
                            color = Color(0xFF90EE90), // Soft green for incoming values
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                        color = MpesaGreen,
                        trackColor = Color.White.copy(alpha = 0.1f)
                    )
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

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
                    if (isDebit) Color(0xFFFF6B6B).copy(alpha = 0.15f) else MpesaGreen.copy(alpha = 0.15f),
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
                    color = if (isDebit) Color(0xFFFF6B6B) else Color(0xFF4CAF50),
                    fontWeight = FontWeight.Bold, fontSize = 14.sp
                )
                Text(formatTime(tx.timestamp), color = TextSecondary, fontSize = 11.sp)
            }
        }
    }
}

// ── Helpers ──────────────────────────────────────────────────────────────────

fun formatKsh(amount: Double): String {
    val nf = NumberFormat.getNumberInstance(Locale.US)
    nf.maximumFractionDigits = 2
    nf.minimumFractionDigits = 0
    return "KES ${nf.format(amount)}"
}

fun formatTime(epoch: Long): String {
    val sdf = SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault())
    return sdf.format(Date(epoch))
}

fun Period.label(): String = when (this) {
    Period.TODAY  -> "Today"
    Period.WEEK   -> "Week"
    Period.MONTH  -> "Month"
    Period.YEAR   -> "Year"
    Period.CUSTOM -> "Custom"
}

fun TransactionType.emoji(): String = when (this) {
    TransactionType.SEND_MONEY        -> "📤"
    TransactionType.RECEIVE_MONEY     -> "📥"
    TransactionType.BUY_GOODS         -> "🛒"
    TransactionType.PAY_BILL          -> "📄"
    TransactionType.WITHDRAW          -> "🏧"
    TransactionType.DEPOSIT           -> "💰"
    TransactionType.AIRTIME           -> "📱"
    TransactionType.SAFARICOM_DATA_BUNDLES -> "🛜"
    TransactionType.ZIIDI             -> "📦"
    TransactionType.MALI              -> "📦"
    TransactionType.M_SHWARI          -> "📦"
    TransactionType.POCHI_LA_BIASHARA -> "🏪"
    TransactionType.FULIZA            -> "💸"
    TransactionType.REVERSAL          -> "↩️"
    TransactionType.KCB_MPESA         -> "🏦"
    TransactionType.GLOBAL_PAY        -> "💳"
    TransactionType.LIPA_MDOGO_MDOGO  -> "🛍️"
    TransactionType.CHARITY           -> "❤️"
    TransactionType.UNACCOUNTED_ADJUSTMENT -> "⚠️"
    TransactionType.UNKNOWN           -> "❓"
}