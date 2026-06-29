package com.example.my_mpesa_tracker.util

import android.content.Context
import com.example.my_mpesa_tracker.data.model.MpesaTransaction
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/**
 * Suggests a realistic budget per subcategory from the person's own
 * spending history — never from population averages or guesses.
 *
 * Method: median of the last 2–6 COMPLETE calendar months' spend in
 * that category, plus a visible 10% buffer. Median rather than mean
 * so one unusual month (a big one-off purchase) doesn't skew the
 * suggestion. The in-progress current month is always excluded since
 * it's not a complete data point yet.
 *
 * No persisted running stats — recomputed fresh from the transaction
 * list every call, same durability principle used throughout the app.
 */

private const val ADAPTIVE_BUFFER = 0.10
private const val MIN_MONTHS_REQUIRED = 2
private const val MAX_MONTHS_CONSIDERED = 6

data class AdaptiveSuggestion(
    val suggestedAmount: Double,
    val monthsUsed: Int,
    val recentMonthlyTotals: List<Double> // oldest first, for transparency in UI
)

object AdaptiveBudgetEngine {

    fun computeSuggestion(
        context: Context,
        subcategory: MerchantSubcategory,
        transactions: List<MpesaTransaction>
    ): AdaptiveSuggestion? {
        val zone = ZoneId.systemDefault()
        val currentMonth = YearMonth.now()

        val monthly = transactions
            .filter { it.isDebit }
            .filter {
                try {
                    MerchantCategoryEngine.categorize(context, it) == subcategory
                } catch (_: Exception) {
                    false
                }
            }
            .groupBy { YearMonth.from(Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate()) }
            .filterKeys { it != currentMonth }
            .toSortedMap()
            .entries
            .toList()
            .takeLast(MAX_MONTHS_CONSIDERED)

        if (monthly.size < MIN_MONTHS_REQUIRED) return null

        val totals = monthly.map { (_, txs) -> txs.sumOf { it.amount + it.transactionCost } }
        val sorted = totals.sorted()
        val median = if (sorted.size % 2 == 0) {
            (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0
        } else {
            sorted[sorted.size / 2]
        }

        return AdaptiveSuggestion(
            suggestedAmount = median * (1 + ADAPTIVE_BUFFER),
            monthsUsed = monthly.size,
            recentMonthlyTotals = totals
        )
    }
}
