package com.example.my_mpesa_tracker.util

import com.example.my_mpesa_tracker.data.model.MpesaTransaction
import java.util.Calendar

data class SpendingStats(
    val totalSpent: Double,
    val totalReceived: Double,
    val netFlow: Double,           // received - spent
    val maxTransaction: Double,
    val minTransaction: Double,
    val avgTransaction: Double,
    val transactionCount: Int,
    val largestTransaction: MpesaTransaction?,
    val smallestTransaction: MpesaTransaction?,
    val byCategory: Map<String, Double>  // type label -> total
)

data class TimeRange(val from: Long, val to: Long)

object TimeRangeHelper {

    fun today(): TimeRange {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        val end = start + 86_400_000L - 1
        return TimeRange(start, end)
    }

    fun thisWeek(): TimeRange {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        val end = start + 7 * 86_400_000L - 1
        return TimeRange(start, end)
    }

    fun thisMonth(): TimeRange {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        cal.add(Calendar.MONTH, 1)
        val end = cal.timeInMillis - 1
        return TimeRange(start, end)
    }

    fun thisYear(): TimeRange {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_YEAR, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        cal.add(Calendar.YEAR, 1)
        val end = cal.timeInMillis - 1
        return TimeRange(start, end)
    }

    fun lastNDays(n: Int): TimeRange {
        val now = System.currentTimeMillis()
        return TimeRange(now - n * 86_400_000L, now)
    }
}

/**
 * Pure computation — takes a list of transactions, returns stats.
 * All DB-reactive queries are in the ViewModel via Flow.
 * This handles the derived calculations.
 */
object StatsEngine {

    fun compute(transactions: List<MpesaTransaction>): SpendingStats {
        if (transactions.isEmpty()) return empty()

        // Self-transfer pairs: same M-Pesa code, one debit leg + one credit leg, matching
        // amount — money moved between your own lines, not real spend or income.
        val transferCodes = transactions
            .groupBy { it.mpesaCode }
            .filterValues { group ->
                group.size == 2 &&
                        group.any { it.isDebit } && group.any { !it.isDebit } &&
                        kotlin.math.abs(group[0].amount - group[1].amount) < 0.01
            }
            .keys

        val realTransactions = transactions.filterNot { it.mpesaCode in transferCodes }
        val debits = realTransactions.filter { it.isDebit }
        val credits = realTransactions.filter { !it.isDebit }

        val totalSpent = debits.sumOf { it.amount + it.transactionCost }
        val totalReceived = credits.sumOf { it.amount }

        val maxTx = debits.maxByOrNull { it.amount }
        val minTx = debits.minByOrNull { it.amount }
        val avgTx = if (debits.isNotEmpty()) totalSpent / debits.size else 0.0

        val byCategory = debits
            .groupBy { it.type.label() }
            .mapValues { (_, txList) -> txList.sumOf { it.amount } }

        return SpendingStats(
            totalSpent = totalSpent,
            totalReceived = totalReceived,
            netFlow = totalReceived - totalSpent,
            maxTransaction = maxTx?.amount ?: 0.0,
            minTransaction = minTx?.amount ?: 0.0,
            avgTransaction = avgTx,
            transactionCount = realTransactions.size,
            largestTransaction = maxTx,
            smallestTransaction = minTx,
            byCategory = byCategory
        )
    }

    private fun empty() = SpendingStats(
        totalSpent = 0.0, totalReceived = 0.0, netFlow = 0.0,
        maxTransaction = 0.0, minTransaction = 0.0, avgTransaction = 0.0,
        transactionCount = 0, largestTransaction = null, smallestTransaction = null,
        byCategory = emptyMap()
    )
}

fun com.example.my_mpesa_tracker.data.model.TransactionType.label(): String = when (this) {
    com.example.my_mpesa_tracker.data.model.TransactionType.SEND_MONEY -> "Send Money"
    com.example.my_mpesa_tracker.data.model.TransactionType.RECEIVE_MONEY -> "Receive"
    com.example.my_mpesa_tracker.data.model.TransactionType.BUY_GOODS -> "Buy Goods"
    com.example.my_mpesa_tracker.data.model.TransactionType.PAY_BILL -> "Pay Bill"
    com.example.my_mpesa_tracker.data.model.TransactionType.WITHDRAW -> "Withdraw"
    com.example.my_mpesa_tracker.data.model.TransactionType.DEPOSIT -> "Deposit"
    com.example.my_mpesa_tracker.data.model.TransactionType.AIRTIME -> "Airtime"
    com.example.my_mpesa_tracker.data.model.TransactionType.SAFARICOM_DATA_BUNDLES -> "Safaricom Data Bundles"
    com.example.my_mpesa_tracker.data.model.TransactionType.ZIIDI -> "Ziidi"
    com.example.my_mpesa_tracker.data.model.TransactionType.MALI -> "Mali"
    com.example.my_mpesa_tracker.data.model.TransactionType.M_SHWARI -> "M-Shwari"
    com.example.my_mpesa_tracker.data.model.TransactionType.POCHI_LA_BIASHARA -> "Pochi la Biashara"
    com.example.my_mpesa_tracker.data.model.TransactionType.FULIZA            -> "Fuliza"
    com.example.my_mpesa_tracker.data.model.TransactionType.REVERSAL          -> "Reversal"
    com.example.my_mpesa_tracker.data.model.TransactionType.KCB_MPESA         -> "KCB M-Pesa"
    com.example.my_mpesa_tracker.data.model.TransactionType.GLOBAL_PAY        -> "Global Pay"
    com.example.my_mpesa_tracker.data.model.TransactionType.LIPA_MDOGO_MDOGO  -> "Lipa Mdogo Mdogo"
    com.example.my_mpesa_tracker.data.model.TransactionType.CHARITY           -> "Charity"
    com.example.my_mpesa_tracker.data.model.TransactionType.UNACCOUNTED_ADJUSTMENT -> "Unaccounted Adjustment"
    com.example.my_mpesa_tracker.data.model.TransactionType.UNKNOWN -> "Other"
}
