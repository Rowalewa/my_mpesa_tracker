package com.example.my_mpesa_tracker.util

import com.example.my_mpesa_tracker.data.model.MpesaTransaction
import com.example.my_mpesa_tracker.data.model.TransactionType
import java.text.SimpleDateFormat
import java.util.*

/**
 * Parses M-Pesa SMS messages into structured Transaction objects.
 */
object MpesaSmsParser {

    private const val MPESA_SENDER = "MPESA"

    // Regex patterns
    private val CODE_REGEX = Regex("""^([A-Z0-9]{10})\s+Confirmed""", RegexOption.IGNORE_CASE)
    private val AMOUNT_REGEX = Regex("""Ksh([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)
    private val COST_REGEX = Regex("""Transaction Cost[,\s]*Ksh\.?([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)
    // Updated Regex to handle double spaces, hidden newlines, and all three prefix variations
    // Replace BALANCE_REGEX with a more tolerant version:
    private val BALANCE_REGEX = Regex("""(?:New\s+(?:M-PESA\s+)?(?:account\s+)?balance\s+is|M-PESA\s+balance\s+is)\s*Ksh\.?([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)
    private val DATE_REGEX = Regex("""on (\d{1,2}/\d{1,2}/\d{2,4})(?:\s+at)?\s+(\d{1,2}:\d{2}\s?[AP]M)""", RegexOption.IGNORE_CASE)

    // Type detection patterns

    // Pattern updated to cleanly capture the name right up to the M-Pesa date format boundary
    // Matches "Your original transaction [CODE] in favour of [MERCHANT] has been reversed"
//    private val REVERSAL_V2_REGEX = Regex("""original transaction\s+([A-Z0-9]{10})(?:\s+in favour of\s+(.+?))?\s+has been reversed""", RegexOption.IGNORE_CASE)
//    private val REVERSAL_ORIGINAL_CODE_REGEX = Regex("""Reversal of transaction\s+([A-Z0-9]{10})""", RegexOption.IGNORE_CASE)
    private val POCHI_PATTERN = Regex("""sent to (.+?)\s+on\s+\d{1,2}/\d{1,2}/\d{2,4}""", RegexOption.IGNORE_CASE)
    // Send Money — Robust support for both unmasked numbers and legacy masked asterisks over variant spacing
    private val SEND_PATTERN = Regex("""sent to (.+?)\s+[\d\*]{10,12}""", RegexOption.IGNORE_CASE)
    // Receive
    private val P2P_RECEIVE_PATTERN = Regex("""received Ksh[\d,.]+\s+from\s+(.+?)\s+[\d\*]{10,12}""", RegexOption.IGNORE_CASE)
    private val GENERIC_RECEIVE_PATTERN = Regex("""received Ksh[\d,.]+\s+from\s+(.+?)(?=\s+on\s+\d|$)""", RegexOption.IGNORE_CASE)
    // Buy Goods and Services
    private val BUY_GOODS_PATTERN = Regex(
        """paid to (.+?)\.\s+(?:New|on)""",
        RegexOption.IGNORE_CASE
    )
    // Paybill
    private val PAY_BILL_PATTERN = Regex("""sent to (.+?) for""", RegexOption.IGNORE_CASE)
    // Upgraded: Matches both "Withdraw" and "Withdrawn" and extracts the agent code + shop profile cleanly
    private val WITHDRAW_PATTERN = Regex("""Withdraw(?:n)?\s+Ksh[\d,.]+\s+from\s+(.+)$""", RegexOption.IGNORE_CASE)
    private val DEPOSIT_PATTERN = Regex("""deposited to your M-PESA""", RegexOption.IGNORE_CASE)
    private val AIRTIME_PATTERN = Regex("""You bought Ksh[\d,.]+\s+of\s+Airtime""", RegexOption.IGNORE_CASE)
//    private val SAFARICOM_DATA_BUNDLES_PATTERN = Regex("""Ksh[\d,.]+\s+sent\s+to\s+(.+?)\s+for\s+account\s+SAFARICOM\s+DATA\s+BUNDLES""", RegexOption.IGNORE_CASE)


    /**
     * Returns a Transaction if the SMS is a valid M-Pesa message, null otherwise.
     */

    fun parse(
        sender: String,
        body: String,
        receivedAt: Long,
        previousBalance: Double? = null,
        subscriptionId: Int = -1
    ): MpesaTransaction? {
        if (!sender.equals(MPESA_SENDER, ignoreCase = true)) return null
        if (!body.contains("Confirmed", ignoreCase = true)) return null

        // Add near the top of parse(), right after the sender/Confirmed checks:
        if (body.contains("account balance was", ignoreCase = true)) return null

        // Filter to intercept and drop M-Shwari balance inquiries
        if (body.contains("M-Shwari Deposit Account Balance", ignoreCase = true)) return null

        val code = CODE_REGEX.find(body)?.groupValues?.get(1) ?: return null

        // Default to 0.0 instead of returning null so we can evaluate the message properly
        val amount = parseAmount(AMOUNT_REGEX, body) ?: 0.0
        val balance = parseAmount(BALANCE_REGEX, body) ?: 0.0
        val cost = parseAmount(COST_REGEX, body) ?: 0.0

        // THE GHOST FILTER: Drop the message entirely if no money moved (e.g., Balance Inquiries).
        // This prevents phantom transactions from hitting the database or the StatsEngine.
        if (amount == 0.0 && cost == 0.0) return null

        val timestamp = parseTimestamp(body) ?: receivedAt

        val (type, counterparty, isDebit) = detectType(body, balance, previousBalance)

        return MpesaTransaction(
            mpesaCode = code,
            amount = amount,
            type = type,
            counterparty = counterparty,
            balanceAfter = balance,
            transactionCost = cost,
            timestamp = timestamp,
            rawSms = body,
            isDebit = isDebit,
            subscriptionId = subscriptionId
        )
    }

    private fun parseAmount(regex: Regex, body: String): Double? {
        return regex.find(body)?.groupValues?.get(1)
            ?.replace(",", "")
            ?.toDoubleOrNull()
    }

    private fun parseTimestamp(body: String): Long? {
        val match = DATE_REGEX.find(body) ?: return null
        val dateStr = "${match.groupValues[1]} ${match.groupValues[2]}"
        return try {
            val sdf = SimpleDateFormat("d/M/yy hh:mm a", Locale.US)
            sdf.parse(dateStr)?.time
        } catch (_: Exception) {
            null
        }
    }

    private val ZIIDI_DEPOSIT_PATTERN = Regex("""sent to ZIIDI""", RegexOption.IGNORE_CASE)
    private val ZIIDI_WITHDRAWAL_PATTERN = Regex("""received Ksh[\d,.]+\s+from ZIIDI""", RegexOption.IGNORE_CASE)

    private fun detectZiidiDirection(text: String, currentBalance: Double, previousBalance: Double?): Boolean {
        return when {
            ZIIDI_DEPOSIT_PATTERN.containsMatchIn(text) -> true     // money leaving M-PESA into Ziidi
            ZIIDI_WITHDRAWAL_PATTERN.containsMatchIn(text) -> false // money coming back from Ziidi
            // Neither wording matched — something new. Fall back rather than
            // guess wrong silently; worth a log line so it surfaces if it
            // ever actually fires.
            else -> previousBalance?.let { currentBalance < it } ?: true
        }
    }

    private val M_SHWARI_DEPOSIT_PATTERN = Regex("""transferred\s+to\s+M-Shwari""", RegexOption.IGNORE_CASE)
    private val M_SHWARI_WITHDRAWAL_PATTERN = Regex("""transferred\s+from\s+M-Shwari""", RegexOption.IGNORE_CASE)
    private fun detectMshwariDirection(text: String, currentBalance: Double, previousBalance: Double?): Boolean {
        return when {
            M_SHWARI_DEPOSIT_PATTERN.containsMatchIn(text) -> true     // money leaving M-PESA into M-Shwari
            M_SHWARI_WITHDRAWAL_PATTERN.containsMatchIn(text) -> false // money coming back from M-Shwari
            // Neither wording matched — something new. Fall back rather than
            // guess wrong silently; worth a log line so it surfaces if it
            // ever actually fires.
            else -> previousBalance?.let { currentBalance < it } ?: true
        }
    }

    private fun detectType(body: String, currentBalance: Double, previousBalance: Double?): Triple<TransactionType, String, Boolean> {

        val balanceWentDown = if (previousBalance != null) currentBalance < previousBalance else true

        // 1. UPDATED ANCHOR: Uses the robust regex to find exactly where the balance text starts,
        // accounting for all Safaricom variations and weird spacing.
        // 1. UPDATED ANCHOR: Uses the robust regex to find exactly where the balance text starts
        val balanceAnchor = Regex(
            """(?:New\s+M-PESA\s+(?:account\s+)?balance\s+is|New\s+balance\s+is|M-Shwari\s+balance\s+is|M-PESA\s+balance\s+is|M-PESA\s+Account\s*:)""",
            RegexOption.IGNORE_CASE
        ).find(body)

        // 2. Slice exactly where that match begins to remove promotional text and ads at the end.
        val transactionCore = if (balanceAnchor != null) {
            body.substring(0, balanceAnchor.range.first)
        } else {
            // Fallback just in case the regex misses completely
            body.substringBefore("New M-PESA")
        }

        // ── Reversal Interception ────────────────────────────────────────
        if (transactionCore.contains("revers", ignoreCase = true)) {
            val mpesaCodes = Regex("""\b([A-Z0-9]{10})\b""").findAll(transactionCore)
                .map { it.groupValues[1] }
                .toList()

            val originalCode = mpesaCodes.getOrNull(1)

            val beneficiary = Regex("""in favour of\s+(.+?)(?=\s+has been|\s+on\s+\d|$)""", RegexOption.IGNORE_CASE)
                .find(transactionCore)?.groupValues?.get(1)?.trim()

            val label = when {
                originalCode != null && beneficiary != null -> "Reversal ($originalCode) - $beneficiary"
                originalCode != null -> "Reversal ($originalCode)"
                else -> "Transaction Reversal"
            }

            val isDebit = transactionCore.contains("debited", ignoreCase = true)

            return Triple(TransactionType.REVERSAL, label, isDebit)
        }


        // ── Wallet Interceptions ─────────────────────────────────────────
        if (transactionCore.contains("ZIIDI", ignoreCase = true)) {
            val isDebit = detectZiidiDirection(transactionCore, currentBalance, previousBalance)
            return Triple(TransactionType.ZIIDI, "Ziidi Wallet", isDebit)
        }

        if (transactionCore.contains("MALI", ignoreCase = true)) {
            val isDebit = previousBalance?.let { currentBalance < it } ?: true
            return Triple(TransactionType.MALI, "Mali Wallet", isDebit)
        }

        if (transactionCore.contains("M-SHWARI", ignoreCase = true)) {
            val isDebit = detectMshwariDirection(transactionCore, currentBalance, previousBalance)  //?.let { currentBalance < it } ?: true
            return Triple(TransactionType.M_SHWARI, "M-Shwari Wallet", isDebit)
        }

        // Move this block to run BEFORE the investmentMap loop:
        if (body.contains("received", ignoreCase = true)) {
            val name = P2P_RECEIVE_PATTERN.find(body)?.groupValues?.get(1)?.trim()
                ?: GENERIC_RECEIVE_PATTERN.find(body)?.groupValues?.get(1)?.trim()
                ?: "Unknown Sender"
            return Triple(TransactionType.RECEIVE_MONEY, name, false)
        }
// THEN the investmentMap loop, only reached for genuine outgoing sends
        // ── Investment / Savings paybills ────────────────────────────────
        val investmentMap = mapOf(
            "Etica"     to "Etica Investments",
            "Chumz"     to "Chumz Investments",
            "Ndovu"     to "Ndovu Investments",
            "Hisa"      to "Hisa Investments",
            "ICEA"      to "ICEA Lion Investments",
            "Britam"    to "Britam Investments",
            "Cytonn"    to "Cytonn Investments",
            "Zimele"    to "Zimele Investments",
            "Kuza"      to "Kuza Investments",
            "Nabo"      to "Nabo Investments",
            "Faida"     to "Faida Investment Bank Ltd",
            "Risevest"  to "RISEVEST"
        )
        for ((keyword, label) in investmentMap) {
            if (transactionCore.contains(keyword, ignoreCase = true))
                return Triple(TransactionType.PAY_BILL, label, true)
        }

        // ── Incoming ─────────────────────────────────────────────────────
//        if (body.contains("received", ignoreCase = true)) {
//            var name = P2P_RECEIVE_PATTERN.find(body)?.groupValues?.get(1)?.trim()
//            if (name == null) {
//                name = GENERIC_RECEIVE_PATTERN.find(body)?.groupValues?.get(1)?.trim()
//            }
//            val cleanName = name?.replace(Regex("""\s+"""), " ") ?: "Unknown Sender"
//            return Triple(TransactionType.RECEIVE_MONEY, cleanName, false)
//        }

        if (DEPOSIT_PATTERN.containsMatchIn(body)) {
            return Triple(TransactionType.DEPOSIT, "Agent Deposit", false)
        }

        // ── Outgoing ─────────────────────────────────────────────────────
        if (transactionCore.contains("M-PESA CARD", ignoreCase = true)) {
            val accountMatch = Regex("""for account\s+(.+)""", RegexOption.IGNORE_CASE).find(transactionCore)
            val rawAccount = accountMatch?.groupValues?.get(1)?.trim() ?: "Global Card Merchant"

            val cleanMerchant = rawAccount
                .split(Regex("""\s{2,}"""))
                .first()
                .trim()

            return Triple(TransactionType.GLOBAL_PAY, cleanMerchant, true)
        }

        if (transactionCore.contains("SAFARICOM DATA BUNDLES", ignoreCase = true)) {
            return Triple(TransactionType.SAFARICOM_DATA_BUNDLES, "Safaricom Data Bundles", true)
        }

        if (AIRTIME_PATTERN.containsMatchIn(body) || transactionCore.contains("of airtime", ignoreCase = true)) {
            return Triple(TransactionType.AIRTIME, "Airtime", true)
        }

        if (body.contains("for account", ignoreCase = true)) {
            val name = PAY_BILL_PATTERN.find(body)?.groupValues?.get(1)?.trim() ?: "Paybill"
            val cleanName = name.replace(Regex("""\s+"""), " ")
            return Triple(TransactionType.PAY_BILL, cleanName, true)
        }

        if (body.contains("paid to", ignoreCase = true) && !AIRTIME_PATTERN.containsMatchIn(body)) {
            val name = BUY_GOODS_PATTERN.find(body)?.groupValues?.get(1)?.trim() ?: "Merchant:"
            val cleanName = name.replace(Regex("""\s+"""), " ")
            return Triple(TransactionType.BUY_GOODS, cleanName, true)
        }

        if (SEND_PATTERN.containsMatchIn(body)) {
            val name = SEND_PATTERN.find(body)?.groupValues?.get(1)?.trim() ?: "Unknown"
            val cleanName = name.replace(Regex("""\s+"""), " ")
            return Triple(TransactionType.SEND_MONEY, cleanName, true)
        }

        if (POCHI_PATTERN.containsMatchIn(body)){
            val name = POCHI_PATTERN.find(body)?.groupValues?.get(1)?.trim() ?: "Unknown"
            val cleanName = name.replace(Regex("""\s+"""), " ")
            return Triple(TransactionType.POCHI_LA_BIASHARA, cleanName, true)
        }

        val withdrawMatch = WITHDRAW_PATTERN.find(transactionCore)
        if (withdrawMatch != null) {
            val agentInfo = withdrawMatch.groupValues[1].trim()
            val cleanAgent = agentInfo.replace(Regex("""\s+"""), " ")
            return Triple(TransactionType.WITHDRAW, cleanAgent, true)
        }

        return Triple(TransactionType.UNKNOWN, "Other Transaction", balanceWentDown)
    }
}