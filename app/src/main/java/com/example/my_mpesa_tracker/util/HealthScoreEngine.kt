package com.example.my_mpesa_tracker.util

import android.content.Context
import com.example.my_mpesa_tracker.data.db.DailyTotal
import com.example.my_mpesa_tracker.data.model.MpesaTransaction
import com.example.my_mpesa_tracker.data.model.TransactionType
import com.example.my_mpesa_tracker.ui.dashboard.BudgetManager
import kotlin.math.abs
import kotlin.math.sqrt

data class ScoreComponent(
    val label: String,
    val score: Double,      // 0-100
    val weight: Double,      // effective weight after redistribution
    val explanation: String
)

data class FinancialHealthScore(
    val overallScore: Int,       // 0-100
    val band: String,            // Excellent / Good / Fair / Needs Attention
    val bandColorHex: Long,       // ARGB long for Color()
    val components: List<ScoreComponent>,
    val hasEnoughData: Boolean
)

private val INVESTMENT_NAMES = setOf(
    "Etica", "Chumz", "Ndovu", "Hisa", "ICEA Lion",
    "Britam", "Cytonn", "Zimele", "Kuza", "Nabo", "Faida"
)

object HealthScoreEngine {

    fun compute(
        context: Context,
        transactions: List<MpesaTransaction>,
        dailyTotals: List<DailyTotal>
    ): FinancialHealthScore {

        if (transactions.size < 3) {
            return FinancialHealthScore(
                overallScore = 0,
                band = "Not enough data",
                bandColorHex = 0xFF8A96A8,
                components = emptyList(),
                hasEnoughData = false
            )
        }

        val debits = transactions.filter { it.isDebit }
        val credits = transactions.filter { !it.isDebit }
        val totalSpent = debits.sumOf { it.amount + it.transactionCost }
        val totalReceived = credits.sumOf { it.amount }

        // ── 1. Net flow consistency (25%) ─────────────────────────────
        val consistency = consistencyScore(dailyTotals)

        // ── 2. Savings behaviour (25%) ─────────────────────────────────
        val savingsAmount = debits.filter {
            it.type == TransactionType.ZIIDI ||
                    it.type == TransactionType.MALI ||
                    it.type == TransactionType.M_SHWARI ||
                    (it.type == TransactionType.PAY_BILL && it.counterparty in INVESTMENT_NAMES)
        }.sumOf { it.amount }
        val savingsBase = (totalReceived).coerceAtLeast(1.0)
        val savingsRate = savingsAmount / savingsBase
        val savingsScoreVal = (20 + (savingsRate * 800)).coerceIn(0.0, 100.0)

        // ── 3. Budget adherence (20%, excluded if no budget set) ───────
        val monthlyBudget = BudgetManager.getMonthlyBudget(context)
        val hasBudget = monthlyBudget > 0
        val budgetScoreVal = if (hasBudget) {
            val usage = totalSpent / monthlyBudget
            when {
                usage <= 0.7 -> 100.0
                usage <= 1.0 -> 100.0 - ((usage - 0.7) / 0.3) * 40.0
                usage <= 1.3 -> 60.0 - ((usage - 1.0) / 0.3) * 40.0
                else -> 20.0
            }.coerceIn(0.0, 100.0)
        } else 0.0

        // ── 4. Spend volatility (15%) ────────────────────────────────
        val volatility = spendVolatilityScore(dailyTotals)

        // ── 5. Debt / overdraft signal (15%) ────────────────────────
        val fulizaAmount = debits.filter { it.type == TransactionType.FULIZA }.sumOf { it.amount }
        val fulizaRatio = if (totalSpent > 0) fulizaAmount / totalSpent else 0.0
        val debtScoreVal = (100 - (fulizaRatio * 300)).coerceIn(0.0, 100.0)

        // ── Weighting — redistribute budget weight if excluded ────────
        val baseWeights = mapOf(
            "consistency" to 0.25,
            "savings" to 0.25,
            "budget" to 0.20,
            "volatility" to 0.15,
            "debt" to 0.15
        )

        val weights = if (hasBudget) baseWeights else {
            val excluded = baseWeights["budget"]!!
            val remaining = baseWeights.filterKeys { it != "budget" }
            val redistributed = remaining.mapValues { it.value + (it.value / (1 - excluded)) * excluded }
            redistributed
        }

        val components = mutableListOf(
            ScoreComponent(
                "Cash Flow Consistency",
                consistency,
                weights["consistency"]!!,
                "How steady your daily net flow is"
            ),
            ScoreComponent(
                "Savings & Investments Rate",
                savingsScoreVal,
                weights["savings"]!!,
                "Portion of income moved to Ziidi, Mali, M-Shwari, or investments"
            ),
            ScoreComponent(
                "Spend Stability",
                volatility,
                weights["volatility"]!!,
                "How predictable your day-to-day spending is"
            ),
            ScoreComponent(
                "Overdraft Discipline",
                debtScoreVal,
                weights["debt"]!!,
                "Reliance on Fuliza relative to total spend"
            )
        )

        if (hasBudget) {
            components.add(
                1,
                ScoreComponent(
                    "Budget Adherence",
                    budgetScoreVal,
                    weights["budget"]!!,
                    "How well you stayed within your monthly budget"
                )
            )
        }

        val overall = components.sumOf { it.score * it.weight }.coerceIn(0.0, 100.0)

        val (band, colorHex) = when {
            overall >= 80 -> "Excellent" to 0xFF00C878
            overall >= 60 -> "Good" to 0xFF4CAF50
            overall >= 40 -> "Fair" to 0xFFFFA726
            else -> "Needs Attention" to 0xFFFF5252
        }

        return FinancialHealthScore(
            overallScore = overall.toInt(),
            band = band,
            bandColorHex = colorHex,
            components = components,
            hasEnoughData = true
        )
    }

    private fun consistencyScore(dailyTotals: List<DailyTotal>): Double {
        if (dailyTotals.size < 2) return 50.0
        val netFlows = dailyTotals.map { it.received - it.spent }
        val positiveRatio = netFlows.count { it >= 0 }.toDouble() / netFlows.size
        val mean = netFlows.average()
        val variance = netFlows.map { (it - mean) * (it - mean) }.average()
        val stdDev = sqrt(variance)
        val avgAbsFlow = netFlows.map { abs(it) }.average().coerceAtLeast(1.0)
        val cv = (stdDev / avgAbsFlow).coerceIn(0.0, 3.0)
        val volatilityScore = (1 - (cv / 3.0)) * 100
        return (positiveRatio * 60 + volatilityScore * 0.4).coerceIn(0.0, 100.0)
    }

    private fun spendVolatilityScore(dailyTotals: List<DailyTotal>): Double {
        val spends = dailyTotals.map { it.spent }.filter { it > 0 }
        if (spends.size < 2) return 50.0
        val mean = spends.average()
        val stdDev = sqrt(spends.map { (it - mean) * (it - mean) }.average())
        val cv = (stdDev / mean.coerceAtLeast(1.0)).coerceIn(0.0, 2.0)
        return ((1 - cv / 2.0) * 100).coerceIn(0.0, 100.0)
    }
}
