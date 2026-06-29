package com.example.my_mpesa_tracker.util

import com.example.my_mpesa_tracker.data.model.MpesaTransaction
import com.example.my_mpesa_tracker.data.model.TransactionType

/**
 * Computes what Pesalyzer can HONESTLY see about net worth from the
 * M-Pesa SMS trail alone. Three distinct confidence tiers, never
 * blended into one falsely-precise number:
 *
 *  - LIVE: M-Pesa cash balance (accurate, real-time)
 *  - ESTIMATED: Ziidi (both deposits and withdrawals/interest payouts
 *    are visible via SMS, so a running estimate is reasonably trustworthy)
 *  - CONTRIBUTION-ONLY: other platforms (Etica, Cytonn, Faida, etc.) —
 *    M-Pesa only sees money going IN. Returns, losses, and redemptions
 *    that land in a bank account instead of back through M-Pesa are
 *    invisible. This is cumulative amount sent, NOT current value.
 *
 * Fuliza is reported as informational usage only, not a claimed
 * precise outstanding balance — the SMS format for a reliable running
 * balance hasn't been verified against real data the way everything
 * else in this app has been, so it isn't presented with false confidence.
 */

data class NetWorthBreakdown(
    val mpesaCash: Double,
    val ziidiEstimated: Double,
    val otherInvestmentsContributed: Map<String, Double>,
    val fulizaUsedRecently: Double
)

private val INVESTMENT_LABELS = setOf(
    "Etica Investments", "Chumz Investments", "Ndovu Investments", "Hisa Investments",
    "ICEA Lion Investments", "Britam Investments", "Cytonn Investments", "Zimele Investments",
    "Kuza Investments", "Nabo Investments", "Faida Investment Bank Ltd", "RISEVEST"
)

object NetWorthEngine {

    fun compute(transactions: List<MpesaTransaction>): NetWorthBreakdown {
        val mpesaCash = transactions.maxByOrNull { it.timestamp }?.balanceAfter ?: 0.0

        val ziidiTx = transactions.filter { it.type == TransactionType.ZIIDI }
        val ziidiEstimated = ziidiTx
            .sumOf { if (it.isDebit) it.amount else -it.amount }
            .coerceAtLeast(0.0)

        val otherInvestments = transactions
            .filter { it.type == TransactionType.PAY_BILL && it.isDebit && it.counterparty in INVESTMENT_LABELS }
            .groupBy { it.counterparty }
            .mapValues { (_, txs) -> txs.sumOf { it.amount + it.transactionCost } }

        val fulizaRecent = transactions
            .filter { it.type == TransactionType.FULIZA && it.isDebit }
            .sumOf { it.amount }

        return NetWorthBreakdown(
            mpesaCash = mpesaCash,
            ziidiEstimated = ziidiEstimated,
            otherInvestmentsContributed = otherInvestments,
            fulizaUsedRecently = fulizaRecent
        )
    }
}