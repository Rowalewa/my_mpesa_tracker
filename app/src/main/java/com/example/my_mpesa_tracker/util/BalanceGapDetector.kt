package com.example.my_mpesa_tracker.util

import android.content.Context
import android.util.Log
import com.example.my_mpesa_tracker.data.db.TransactionDao
import com.example.my_mpesa_tracker.data.model.MpesaTransaction
import com.example.my_mpesa_tracker.data.model.TransactionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Detects transactions that exist in reality but have no SMS record —
 * deleted, never received, or arrived through a channel this app
 * can't see. This is structurally undetectable at the parsing level
 * (there's no message to parse), but its CONSEQUENCE is detectable:
 * the balance chain won't reconcile at that point.
 *
 * Method — proven against real exported data before being wired in
 * here: group transactions by exact timestamp (SMS has only minute
 * precision, so same-minute transactions are batched together rather
 * than compared individually, avoiding false positives from genuine
 * same-minute ordering ambiguity like a purchase + its Ziidi sweep).
 * Walk the batches in order; wherever actual balance doesn't match
 * expected balance, the difference IS the missing transaction's
 * effect — close it with a clearly labelled placeholder.
 *
 * Runs entirely per-subscription (per-SIM), never mixing chains.
 * Self-healing: once a gap is filled, subsequent runs see a
 * consistent chain at that point and don't re-flag it.
 */

private const val TAG = "BalanceGapDetector"
private const val GAP_TOLERANCE = 5.0 // KES — ignore noise below this

object BalanceGapDetector {

    suspend fun detectAndFillGaps(context: Context, dao: TransactionDao) = withContext(Dispatchers.IO) {
        try {
            val subscriptions = dao.getDistinctSubscriptions()
            subscriptions.forEach { subId ->
                try {
                    detectForSubscription(dao, subId)
                } catch (e: Exception) {
                    Log.e(TAG, "Gap detection failed for subscription $subId, skipping", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gap detection failed entirely, skipping this run", e)
        }
    }

    private suspend fun detectForSubscription(dao: TransactionDao, subId: Int) {
        val transactions = dao.getAllForSubscriptionOrdered(subId).filter { it.type != TransactionType.UNACCOUNTED_ADJUSTMENT }
        if (transactions.size < 2) return

        val batches = transactions.groupBy { it.timestamp }.toSortedMap()
        val timestamps = batches.keys.toList()

        var prevBalance: Double? = null

        for (i in timestamps.indices) {
            val ts = timestamps[i]
            val batch = batches[ts]!!
            val batchNet = batch.sumOf { effectOf(it) }

            // Default for a single-item batch — the only case where DB
            // order is meaningful, since there's nothing to reorder.
            var batchEndBalance = batch.last().balanceAfter

            if (prevBalance != null) {
                val expected = prevBalance + batchNet

                if (batch.size > 1) {
                    // Same-minute batches have no real order from SMS alone.
                    // batchNet is order-independent, so whichever member's
                    // OWN reported balance equals the fully-reconciled batch
                    // end IS the true final transaction — regardless of
                    // import order. This is what was producing the
                    // GAP-<sub>-<ts> credit/debit pairs: a purchase + its
                    // Ziidi sweep landing in the same minute, with
                    // batch.last() picking whichever one the DB happened to
                    // return last, not whichever actually happened last.
                    batch.minByOrNull { kotlin.math.abs(it.balanceAfter - expected) }
                        ?.let { candidate ->
                            if (kotlin.math.abs(candidate.balanceAfter - expected) <= GAP_TOLERANCE) {
                                batchEndBalance = candidate.balanceAfter
                            }
                        }
                }

                val gap = batchEndBalance - expected
                if (kotlin.math.abs(gap) > GAP_TOLERANCE) {
                    insertGapFiller(dao, subId, gapAmount = gap, prevBalance = prevBalance, beforeTimestamp = ts)
                }
            }

            prevBalance = batchEndBalance
        }
    }

    private fun effectOf(tx: MpesaTransaction): Double =
        if (tx.isDebit) -(tx.amount + tx.transactionCost) else tx.amount

    private suspend fun insertGapFiller(
        dao: TransactionDao,
        subId: Int,
        gapAmount: Double,
        prevBalance: Double,
        beforeTimestamp: Long
    ) {
        val syntheticTimestamp = beforeTimestamp - 1000 // sorts just before the batch that revealed the gap
        val syntheticCode = "GAP-$subId-$syntheticTimestamp"

        // Belt-and-suspenders idempotency check — the algorithm is
        // already self-healing (a filled gap won't be seen as a gap
        // again), but this avoids any duplicate insert on edge-case
        // re-entrancy.
        val existing = dao.getAllForSubscriptionOrdered(subId)
        if (existing.any { it.mpesaCode == syntheticCode }) return

        val isDebit = gapAmount < 0
        val amount = kotlin.math.abs(gapAmount)

        val filler = MpesaTransaction(
            mpesaCode = syntheticCode,
            amount = amount,
            type = TransactionType.UNACCOUNTED_ADJUSTMENT,
            counterparty = "Unaccounted (gap detected)",
            balanceAfter = prevBalance + gapAmount,
            transactionCost = 0.0,
            timestamp = syntheticTimestamp,
            rawSms = "System-inferred: balance chain did not reconcile between the transaction before and after this point. " +
                    "No matching SMS was found — likely deleted, never received, or a duplicate SIM/line was involved. " +
                    "Amount and direction are computed from the balance difference; if you later identify the real " +
                    "source, add a note here to record it.",
            isDebit = isDebit,
            subscriptionId = subId
        )

        try {
            dao.insert(filler)
            Log.i(TAG, "Gap filled: ${if (isDebit) "-" else "+"}$amount at $syntheticTimestamp for sub $subId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to insert gap filler", e)
        }
    }
}
