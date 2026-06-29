package com.example.my_mpesa_tracker.ui.dashboard

import android.app.Application
import android.content.Context
import android.provider.Telephony
import android.util.Log
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.my_mpesa_tracker.data.db.AppDatabase
import com.example.my_mpesa_tracker.data.db.DailyTotal
import com.example.my_mpesa_tracker.data.db.SubscriptionSummary
import com.example.my_mpesa_tracker.data.model.MpesaTransaction
import com.example.my_mpesa_tracker.data.model.TransactionType
import com.example.my_mpesa_tracker.util.BalanceGapDetector
import com.example.my_mpesa_tracker.util.MpesaSmsParser
import com.example.my_mpesa_tracker.util.SpendingStats
import com.example.my_mpesa_tracker.util.StatsEngine
import com.example.my_mpesa_tracker.util.label
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

enum class Period { TODAY, WEEK, MONTH, YEAR, CUSTOM }

data class TimeRange(
    val from: Long,
    val to: Long,
    val displayLabel: String
)

data class InsightData(
    val topReceiver: String = "",
    val topSender: String = "",
    val busiestDay: String = "",
    val biggestSpendDay: String = "",
    val highestInflowDay: String = "", // Added for inflow analysis
    val mostUsedCategory: String = "",
    val topByCategory: Map<String, String> = emptyMap()
)

data class DashboardUiState(
    val stats: SpendingStats = SpendingStats(0.0,0.0,0.0,0.0,0.0,0.0,0,null,null, emptyMap()),
    val allTransactions: List<MpesaTransaction> = emptyList(),
    val filteredTransactions: List<MpesaTransaction> = emptyList(),
    val dailyChart: List<DailyTotal> = emptyList(),
    val selectedPeriod: Period = Period.MONTH,
    val dateLabel: String = "",
    val searchQuery: String = "",
    val selectedCategory: TransactionType? = null,
    val insights: InsightData = InsightData(),
    val isLoading: Boolean = true,
    val customFrom: LocalDate? = null,
    val customTo: LocalDate? = null,
    val isImporting: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = AppDatabase.getInstance(app).transactionDao()

    private val _period = MutableStateFlow(Period.MONTH)
    private val _searchQuery = MutableStateFlow("")
    private val _selectedCategory = MutableStateFlow<TransactionType?>(null)
    private val _customRange = MutableStateFlow<Pair<LocalDate, LocalDate>?>(null)
    private val _historyLoading = MutableStateFlow(false)

    val uiState: StateFlow<DashboardUiState> = combine(
        _period, _searchQuery, _selectedCategory, _customRange, _historyLoading
    ) { period, query, category, custom, historyLoading ->
        Tuple5(period, query, category, custom, historyLoading)
    }.flatMapLatest { tuple ->
        val (period, query, category, custom, historyLoading) = tuple

        val range = when (period) {
            Period.CUSTOM -> custom?.let { (from, to) ->
                TimeRange(
                    from.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                    to.atTime(LocalTime.MAX).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                    "${from.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US))} – ${to.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US))}"
                )
            } ?: TimeRangeHelper.thisMonth()
            Period.TODAY -> TimeRangeHelper.today()
            Period.WEEK  -> TimeRangeHelper.thisWeek()
            Period.MONTH -> TimeRangeHelper.thisMonth()
            Period.YEAR  -> TimeRangeHelper.thisYear()
        }

        combine(
            dao.transactionsInRange(range.from, range.to),
            dao.dailyTotals(range.from, range.to)
        ) { txList, daily ->
            val filtered = txList.filter { tx ->
                val matchesQuery = query.isBlank() ||
                        tx.counterparty.contains(query, ignoreCase = true) ||
                        tx.mpesaCode.contains(query, ignoreCase = true) ||
                        tx.amount.toString().contains(query)
                val matchesCategory = category == null || tx.type == category
                matchesQuery && matchesCategory
            }

            DashboardUiState(
                stats = StatsEngine.compute(filtered),
                allTransactions = txList,
                filteredTransactions = filtered,
                dailyChart = daily,
                selectedPeriod = period,
                dateLabel = range.displayLabel,
                searchQuery = query,
                selectedCategory = category,
                insights = computeInsights(txList),
                isLoading = historyLoading,
                isImporting = historyLoading,
                customFrom = custom?.first,
                customTo = custom?.second
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardUiState()
    )

    fun refresh() {
        _period.value = _period.value // re-triggers the flow
        rescanForGaps()
    }
    fun selectPeriod(period: Period) { _period.value = period }
    fun setSearch(query: String) { _searchQuery.value = query }
    fun setCategory(type: TransactionType?) { _selectedCategory.value = type }
    fun setCustomRange(from: LocalDate, to: LocalDate) {
        _customRange.value = Pair(from, to)
        _period.value = Period.CUSTOM
    }

    // ── Safe Core Coroutine Entry Wrapper ────────────────────────────────────
    fun syncMpesaSms(force: Boolean = false) {
        val sharedPrefs = getApplication<Application>().getSharedPreferences("mpesa_tracker_prefs", Context.MODE_PRIVATE)
        val isHistoryFetched = sharedPrefs.getBoolean("is_history_fetched", false)

        if (force || !isHistoryFetched) {
            viewModelScope.launch {
                _historyLoading.value = true
                val successfullyParsed = fetchSmsHistory()

                // Only mark as completed if the system query actually executed without failing security checks
                if (successfullyParsed) {
                    sharedPrefs.edit { putBoolean("is_history_fetched", true) }
                }
                if (successfullyParsed) {
                    BalanceGapDetector.detectAndFillGaps(getApplication(), dao)
                }
                _historyLoading.value = false
            }
        } else {
            if (_historyLoading.value) {
                _historyLoading.value = false
            }
        }
    }
    fun rescanForGaps() {
        viewModelScope.launch(Dispatchers.IO) {
            BalanceGapDetector.detectAndFillGaps(getApplication(), dao)
        }
    }

    fun repairCorruptedEntries() {
        val prefs = getApplication<Application>().getSharedPreferences("mpesa_tracker_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("data_repair_v2_done", false)) return

        viewModelScope.launch(Dispatchers.IO) {
            dao.removeDuplicateTransactions()
            dao.deleteZeroBalanceEntries()
            prefs.edit { putBoolean("data_repair_v2_done", true) }
            syncMpesaSms(force = true)
        }
    }

//    fun repairTimestampAndGapArtifacts() {
//        val prefs = getApplication<Application>().getSharedPreferences("mpesa_tracker_prefs", Context.MODE_PRIVATE)
//        if (prefs.getBoolean("timestamp_gap_repair_v1_done", false)) return
//        viewModelScope.launch(Dispatchers.IO) {
//            dao.deleteAllTransactions()
//            prefs.edit {
//                putBoolean("is_history_fetched", false)
//                    .putBoolean("timestamp_gap_repair_v1_done", true)
//            }
//            syncMpesaSms(force = true)
//            BalanceGapDetector.detectAndFillGaps(getApplication(), dao)
//        }
//    }

    // Returns True if query was safely processed, False if permission or platform errors dropped it
    private suspend fun fetchSmsHistory(): Boolean = withContext(Dispatchers.IO) {
        val context = getApplication<Application>().applicationContext
        val transactionsToInsert = mutableListOf<MpesaTransaction>()
        val uri = "content://sms/inbox".toUri()

        val projection = arrayOf(
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.SUBSCRIPTION_ID   // ADDED
        )

        val selection = "${Telephony.Sms.ADDRESS} = ?"
        val selectionArgs = arrayOf("MPESA")
        val sortOrder = "${Telephony.Sms.DATE} ASC"

        try {
            context.contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
                val addressIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
                val subIdIndex = cursor.getColumnIndex(Telephony.Sms.SUBSCRIPTION_ID) // may be -1 if column missing on some OEMs

                // Per-subscription balance tracking, instead of one
                // single running variable. This is what lets historical
                // import correctly separate two interleaved SIM lines.
                val lastTrackedBalanceBySub = mutableMapOf<Int, Double>()

                while (cursor.moveToNext()) {
                    val sender = cursor.getString(addressIndex) ?: "MPESA"
                    val body = cursor.getString(bodyIndex) ?: continue
                    val systemTimestamp = cursor.getLong(dateIndex)
                    val subscriptionId = if (subIdIndex >= 0) cursor.getInt(subIdIndex) else -1

                    val isZiidiRelated = body.contains("ZIIDI", ignoreCase = true)
//                    if (isZiidiRelated) Log.d("ZiidiDebug", "RAW >>> $body")

                    val previousBalance = lastTrackedBalanceBySub[subscriptionId]

                    val transaction = MpesaSmsParser.parse(
                        sender = sender,
                        body = body,
                        receivedAt = systemTimestamp,
                        previousBalance = previousBalance,
                        subscriptionId = subscriptionId
                    )

//                    if (isZiidiRelated) Log.d("ZiidiDebug", "PARSED >>> code=${transaction?.mpesaCode} type=${transaction?.type}")

                    if (transaction != null) {
                        // amount+direction check, not just code — a rare
                        // Safaricom code collision across two real
                        // messages is possible (confirmed in testing),
                        // so bare code matching alone isn't safe.
                        if (dao.countByCodeAndDetails(
                                transaction.mpesaCode, transaction.amount, transaction.isDebit
                            ) == 0L
                        ) {
                            transactionsToInsert.add(transaction)
                        }
                        lastTrackedBalanceBySub[subscriptionId] = transaction.balanceAfter
                    }
                }
                Log.d("SmsImport", "Rows returned: ${cursor.count}")
            }

            if (transactionsToInsert.isNotEmpty()) {
                dao.insertAll(transactionsToInsert)
            }
            true
        } catch (e: SecurityException) {
            Log.e("DashboardViewModel", "SMS Database access denied - permissions not active yet.", e)
            false
        } catch (e: Exception) {
            Log.e("DashboardViewModel", "Unexpected exception reading SMS history", e)
            false
        }
    }

    fun getSimDiagnostics(onResult: (List<SubscriptionSummary>) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val summary = try {
                dao.getSubscriptionSummary()
            } catch (_: Exception) {
                emptyList()
            }
            withContext(Dispatchers.Main) {
                onResult(summary)
            }
        }
    }

    fun purgeStaleSubscription(subscriptionId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                dao.deleteBySubscription(subscriptionId)
            } catch (e: Exception) {
                Log.e("DashboardViewModel", "Failed to purge subscription $subscriptionId", e)
            }
        }
    }

    fun resyncForSimTagging() {
        val prefs = getApplication<Application>().getSharedPreferences("mpesa_tracker_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("sim_tagging_repair_done", false)) return
        viewModelScope.launch(Dispatchers.IO) {
            dao.removeDuplicateTransactions()
            prefs.edit { putBoolean("sim_tagging_repair_done", true) }
            syncMpesaSms(force = true)
        }
    }

    private fun computeInsights(txList: List<MpesaTransaction>): InsightData {
        if (txList.isEmpty()) return InsightData()

        val debits = txList.filter { it.isDebit }
        val credits = txList.filter { !it.isDebit }

        // 1. Outflow Insights
        val topSender = debits.groupBy { it.counterparty }
            .maxByOrNull { it.value.sumOf { t -> t.amount } }?.key ?: ""

        // 2. Inflow Insights (Who sends you the most money)
        val topReceiver = credits.groupBy { it.counterparty }
            .maxByOrNull { it.value.sumOf { t -> t.amount } }?.key ?: ""

        // 3. Frequency Insights
        val dayNames = listOf("Sun","Mon","Tue","Wed","Thu","Fri","Sat")
        val busiestDay = txList.groupBy {
            val cal = java.util.Calendar.getInstance()
            cal.timeInMillis = it.timestamp
            cal.get(java.util.Calendar.DAY_OF_WEEK) - 1
        }.maxByOrNull { it.value.size }?.key?.let { dayNames[it] } ?: ""

        // Fixed: Standardized formatter to explicitly include the Year suffix
        val sdf = java.text.SimpleDateFormat("d MMM yyyy", Locale.US)

        // 4. Highest Outflow Day
        val biggestSpendDay = debits.groupBy {
            val cal = java.util.Calendar.getInstance()
            cal.timeInMillis = it.timestamp
            "${cal.get(java.util.Calendar.YEAR)}-${cal.get(java.util.Calendar.DAY_OF_YEAR)}"
        }.maxByOrNull { it.value.sumOf { t -> t.amount } }
            ?.value?.firstOrNull()
            ?.let { sdf.format(java.util.Date(it.timestamp)) } ?: ""

        // 5. Highest Inflow Day (New Inflow Analytics Hook)
        val highestInflowDay = credits.groupBy {
            val cal = java.util.Calendar.getInstance()
            cal.timeInMillis = it.timestamp
            "${cal.get(java.util.Calendar.YEAR)}-${cal.get(java.util.Calendar.DAY_OF_YEAR)}"
        }.maxByOrNull { it.value.sumOf { t -> t.amount } }
            ?.value?.firstOrNull()
            ?.let { sdf.format(java.util.Date(it.timestamp)) } ?: ""

        // 6. Categorical Insights
        val mostUsedCategory = debits.groupBy { it.type }
            .maxByOrNull { it.value.size }?.key?.label() ?: ""

        val topByCategory = TransactionType.entries
            .associateWith { type ->
                txList.filter { it.type == type && it.isDebit }
                    .groupBy { it.counterparty }
                    .maxByOrNull { it.value.size }?.key ?: ""
            }
            .filter { it.value.isNotEmpty() }
            .mapKeys { it.key.label() }

        return InsightData(
            topReceiver = topReceiver,
            topSender = topSender,
            busiestDay = busiestDay,
            biggestSpendDay = biggestSpendDay,
            highestInflowDay = highestInflowDay, // Passed cleanly to your state stream
            mostUsedCategory = mostUsedCategory,
            topByCategory = topByCategory
        )
    }
}

private data class Tuple5<T1, T2, T3, T4, T5>(
    val v1: T1, val v2: T2, val v3: T3, val v4: T4, val v5: T5
)

object TimeRangeHelper {
    fun today(): TimeRange {
        val now = LocalDateTime.now()
        val start = now.toLocalDate().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val end = now.toLocalDate().atTime(LocalTime.MAX).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val label = now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.US))
        return TimeRange(start, end, label)
    }

    fun thisWeek(): TimeRange {
        val now = LocalDate.now()
        val firstDayOfWeek = now.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
        val start = firstDayOfWeek.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val end = firstDayOfWeek.plusDays(6).atTime(LocalTime.MAX).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val weekFields = WeekFields.of(Locale.getDefault())
        val weekOfMonth = now.get(weekFields.weekOfMonth())
        val monthLabel = now.format(DateTimeFormatter.ofPattern("MMMM", Locale.US))
        return TimeRange(start, end, "Week $weekOfMonth of $monthLabel")
    }

    fun thisMonth(): TimeRange {
        val now = LocalDate.now()
        val firstDay = now.with(TemporalAdjusters.firstDayOfMonth())
        val start = firstDay.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val end = now.with(TemporalAdjusters.lastDayOfMonth()).atTime(LocalTime.MAX).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return TimeRange(start, end, now.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)))
    }

    fun thisYear(): TimeRange {
        val now = LocalDate.now()
        val start = now.with(TemporalAdjusters.firstDayOfYear()).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val end = now.with(TemporalAdjusters.lastDayOfYear()).atTime(LocalTime.MAX).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return TimeRange(start, end, now.format(DateTimeFormatter.ofPattern("yyyy", Locale.US)))
    }
}

fun List<MpesaTransaction>.filterToCurrentSim(): List<MpesaTransaction> {
    if (isEmpty()) return this
    val currentSubId = maxByOrNull { it.timestamp }?.subscriptionId ?: return this
    return filter { it.subscriptionId == currentSubId }
}