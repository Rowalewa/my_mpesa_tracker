package com.example.my_mpesa_tracker.util

import android.content.Context
import android.util.Log
import com.example.my_mpesa_tracker.data.model.MpesaTransaction
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * On-device anomaly detection using per-category log-normal statistics
 * fitted offline on realistic Kenyan M-Pesa spending patterns.
 *
 * Design principles (durability first):
 *  - Every failure path degrades to "detect nothing" — never crashes,
 *    never blocks the dashboard from rendering.
 *  - No persisted running statistics. Personal calibration is recomputed
 *    fresh from the transaction list every call, so there is nothing
 *    that can drift, corrupt, or need a migration later.
 *  - Population baseline always retains some influence (capped blend
 *    weight) so a user's own recent unusual burst can't fully convince
 *    the detector that outliers are normal.
 */

private const val TAG = "AnomalyDetector"
private const val CONFIG_ASSET = "anomaly_config.json"
private const val Z_SCORE_THRESHOLD = 3.0
private const val MIN_AMOUNT_TO_FLAG = 50.0
private const val MIN_TRANSACTIONS_FOR_DETECTION = 10
private const val MIN_PERSONAL_SAMPLES_FOR_BLEND = 5
private const val MAX_PERSONAL_BLEND_WEIGHT = 0.85
private const val MAX_RESULTS = 8

data class CategoryStats(val logMean: Double, val logStd: Double)

data class AnomalyResult(
    val transaction: MpesaTransaction,
    val zScore: Double,
    val explanation: String
)

private fun mlCategory(sub: MerchantSubcategory): String = when (sub) {
    MerchantSubcategory.FOOD_DINING -> "food"
    MerchantSubcategory.GROCERIES -> "groceries"
    MerchantSubcategory.TRANSPORT -> "transport"
    MerchantSubcategory.UTILITIES -> "utilities"
    MerchantSubcategory.PERSONAL_TRANSFER -> "send_money"
    else -> "other"
}

object AnomalyDetector {

    @Volatile private var cachedConfig: Map<String, CategoryStats>? = null
    @Volatile private var configLoadAttempted = false

    /**
     * Loads and parses the bundled population config once. If anything
     * goes wrong — missing asset, malformed JSON, unexpected shape —
     * this returns null and detection is silently skipped everywhere.
     */
    private fun loadConfig(context: Context): Map<String, CategoryStats>? {
        if (configLoadAttempted) return cachedConfig
        configLoadAttempted = true

        return try {
            val json = context.assets.open(CONFIG_ASSET).bufferedReader().use { it.readText() }
            val root = JSONObject(json)
            val perCategory = root.getJSONObject("per_category")
            val result = mutableMapOf<String, CategoryStats>()

            perCategory.keys().forEach { cat ->
                try {
                    val obj = perCategory.getJSONObject(cat)
                    val logMean = obj.getDouble("log_mean")
                    val logStd = obj.getDouble("log_std").coerceAtLeast(0.05)
                    result[cat] = CategoryStats(logMean, logStd)
                } catch (e: Exception) {
                    Log.w(TAG, "Skipping malformed category entry: $cat", e)
                }
            }
            cachedConfig = if (result.isNotEmpty()) result else null
            cachedConfig
        } catch (e: Exception) {
            Log.w(TAG, "Anomaly config unavailable — detection disabled this session", e)
            null
        }
    }

    /**
     * Detects unusual transactions within the given list. Pure and
     * side-effect-free: recomputes personal stats fresh each call from
     * whatever transactions are passed in, so results always reflect
     * current data with no stale or drifted state.
     */
    fun detectAnomalies(context: Context, transactions: List<MpesaTransaction>): List<AnomalyResult> {
        return try {
            detectAnomaliesInternal(context, transactions)
        } catch (e: Exception) {
            // Absolute last line of defence — anomaly detection must
            // never be able to break the dashboard.
            Log.e(TAG, "Anomaly detection failed unexpectedly, skipping", e)
            emptyList()
        }
    }

    private fun detectAnomaliesInternal(context: Context, transactions: List<MpesaTransaction>): List<AnomalyResult> {
        if (transactions.size < MIN_TRANSACTIONS_FOR_DETECTION) return emptyList()
        val populationStats = loadConfig(context) ?: return emptyList()

        val debits = transactions.filter { it.isDebit && it.amount >= MIN_AMOUNT_TO_FLAG }
        if (debits.isEmpty()) return emptyList()

        // Group by ML category using the same subcategory logic already
        // used for the "Where Your Money Goes" breakdown, so results stay
        // consistent with what the user sees elsewhere in the app.
        val grouped = debits.groupBy { tx ->
            try {
                mlCategory(MerchantCategoryEngine.categorize(context, tx))
            } catch (_: Exception) {
                "other"
            }
        }

        val results = mutableListOf<AnomalyResult>()

        grouped.forEach { (category, txs) ->
            val popStats = populationStats[category] ?: populationStats["other"] ?: return@forEach

            // Personal stats, computed fresh from this same batch — no
            // persisted state to go stale.
            val logAmounts = txs.map { ln(it.amount + 1.0) }
            val personalCount = logAmounts.size
            val (blendedMean, blendedStd) = if (personalCount >= MIN_PERSONAL_SAMPLES_FOR_BLEND) {
                val personalMean = logAmounts.average()
                val personalVariance = logAmounts.map { (it - personalMean) * (it - personalMean) }.average()
                val personalStd = sqrt(personalVariance).coerceAtLeast(0.05)

                val weight = (personalCount / 20.0).coerceAtMost(MAX_PERSONAL_BLEND_WEIGHT)
                val mean = weight * personalMean + (1 - weight) * popStats.logMean
                val std = weight * personalStd + (1 - weight) * popStats.logStd
                mean to std.coerceAtLeast(0.05)
            } else {
                popStats.logMean to popStats.logStd
            }

            txs.forEach { tx ->
                try {
                    val logAmt = ln(tx.amount + 1.0)
                    val z = abs((logAmt - blendedMean) / blendedStd)

                    if (z >= Z_SCORE_THRESHOLD) {
                        val typicalAmount = exp(blendedMean) - 1.0
                        val ratio = if (typicalAmount > 0) tx.amount / typicalAmount else 0.0
                        val categoryLabel = readableCategory(category)

                        val explanation = if (ratio >= 1.0) {
                            "About ${"%.1f".format(ratio)}x your typical $categoryLabel spend"
                        } else {
                            "Unusually low for your typical $categoryLabel spend"
                        }

                        results.add(AnomalyResult(tx, z, explanation))
                    }
                } catch (_: Exception) {
                    // Skip this single transaction, don't fail the batch
                }
            }
        }

        return results.sortedByDescending { it.zScore }.take(MAX_RESULTS)
    }

    private fun readableCategory(mlCat: String): String = when (mlCat) {
        "food" -> "food"
        "transport" -> "transport"
        "utilities" -> "utilities"
        "groceries" -> "groceries"
        "send_money" -> "transfer"
        else -> "overall"
    }
}
