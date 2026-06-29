package com.example.my_mpesa_tracker.util

import android.content.Context
import android.util.Log
import com.example.my_mpesa_tracker.data.model.MpesaTransaction
import com.example.my_mpesa_tracker.data.model.TransactionType
import androidx.core.content.edit

enum class MerchantSubcategory(val emoji: String, val label: String) {
    FOOD_DINING("🍔", "Food & Dining"),
    GROCERIES("🛒", "Groceries"),
    TRANSPORT("🚗", "Transport"),
    UTILITIES("💡", "Utilities"),
    HEALTH("🏥", "Health & Pharmacy"),
    EDUCATION("🎓", "Education"),
    ENTERTAINMENT("🎬", "Entertainment"),
    BETTING("🎲", "Betting & Gaming"),
    SHOPPING("👕", "Shopping"),
    RENT_HOUSING("🏠", "Rent & Housing"),
    BUSINESS_SERVICES("💼", "Business & Services"),
    PERSONAL_TRANSFER("🤝", "Personal Transfers"),
    SAVINGS_INVESTMENT("💰", "Savings & Investments"),
    AIRTIME_DATA("📱", "Airtime & Data"),
    CASH_WITHDRAWAL("🏧", "Cash Withdrawal"),
    OVERDRAFT("💸️", "Fuliza / Overdraft"),
    CHARITY_GIVING("❤️", "Charity"),
    BANKING("🏦", "Banking & Transfers"),
    INSURANCE("🛡️", "Insurance"),
    LOANS_CREDIT("💳", "Loans & Credit"),
    REVERSALS("🔃", "Reversals"),
    UNACCOUNTED_GAP("⚠️", "Unaccounted Gap/Adjustment"),
    OTHER("❓", "Other")

}

/**
 * Offline keyword dictionary for common Kenyan merchants.
 * Matching is done via substring search on the uppercased counterparty name.
 * No internet required — fully on-device.
 */
private val MERCHANT_KEYWORDS: Map<MerchantSubcategory, List<String>> = mapOf(
    MerchantSubcategory.FOOD_DINING to listOf(
        "JAVA", "KFC", "PIZZA", "CHICKEN", "RESTAURANT", "CAFE", "COFFEE",
        "BURGER", "GRILL", "EATERY", "BAKERY", "BISTRO", "CHOMA", "DOMINOS",
        "SUBWAY", "STEERS", "GALITOS", "KEBAB", "BREW", "KITCHEN"
    ),
    MerchantSubcategory.GROCERIES to listOf(
        "SUPERMARKET", "NAIVAS", "CARREFOUR", "QUICKMART", "CHANDARANA",
        "TUSKYS", "CLEANSHELF", "GREENSPOON", "MINIMARKET", "MART", "GROCER"
    ),
    MerchantSubcategory.TRANSPORT to listOf(
        "UBER", "BOLT", "SHELL", "TOTAL", "RUBIS", "OLA ENERGY", "OILIBYA",
        "PARKING", "MATATU", "NTSA", "PETROL", "FUEL", "STAGE", "TAXI",
        "LITTLE CAB", "SWVL"
    ),
    MerchantSubcategory.UTILITIES to listOf(
        "KPLC", "KENYA POWER", "NAIROBI WATER", "WATER COMPANY", "GARBAGE",
        "ELECTRICITY", "PREPAID", "TOKEN", "SEWERAGE"
    ),
    MerchantSubcategory.HEALTH to listOf(
        "PHARMACY", "HOSPITAL", "CLINIC", "CHEMIST", "NHIF", "SHA",
        "MEDICAL", "HEALTHCARE", "DENTAL", "OPTICIANS", "LAB"
    ),
    MerchantSubcategory.EDUCATION to listOf(
        "SCHOOL", "UNIVERSITY", "COLLEGE", "ACADEMY", "FEES", "TUITION",
        "INSTITUTE", "POLYTECHNIC"
    ),
    MerchantSubcategory.ENTERTAINMENT to listOf(
        "NETFLIX", "DSTV", "GOTV", "SHOWMAX", "CINEMA", "SPOTIFY",
        "MULTICHOICE", "IMAX", "PLAYNATION", "AMAZON PRIME"
    ),
    MerchantSubcategory.BETTING to listOf(
        "BETIKA", "SPORTPESA", "ODIBET", "BETWAY", "MOZZART", "1XBET",
        "BETLION", "BETPAWA", "MSPORT", "CASINO", "BETTING", "CALL OF DUTY"
    ),
    MerchantSubcategory.SHOPPING to listOf(
        "JUMIA", "FASHION", "BOUTIQUE", "SHOES", "CLOTHING", "TEXTILES",
        "ELECTRONICS", "PHONE SHOP", "GADGET"
    ),
    MerchantSubcategory.RENT_HOUSING to listOf(
        "RENT", "LANDLORD", "HOUSING", "APARTMENTS", "ESTATE MANAGEMENT"
    ),
    MerchantSubcategory.SAVINGS_INVESTMENT to listOf(
        "ETICA", "CHUMZ", "NDOVU", "HISA", "ICEA", "BRITAM",
        "CYTONN", "ZIMELE", "KUZA", "NABO", "FAIDA", "RISEVEST", "NAIROBI SECURITIES EXCHANGE"
    ),
    MerchantSubcategory.BANKING to listOf(
        "KCB", "EQUITY", "EQUITEL", "CO-OP", "COOPERATIVE", "ABSA", "BARCLAYS",
        "DTB", "DIAMOND TRUST", "STANDARD CHARTERED", "STANCHART", "NCBA",
        "FAMILY BANK", "I&M", "STANBIC", "SIDIAN", "HOUSING FINANCE", "HF GROUP",
        "NATIONAL BANK", "PRIME BANK", "GULF AFRICAN", "SBM BANK",
        "CONSOLIDATED BANK", "CREDIT BANK", "ECOBANK", "GTBANK", "GUARANTY TRUST",
        "VICTORIA COMMERCIAL", "PARAMOUNT BANK"
    ),
    MerchantSubcategory.INSURANCE to listOf(
        "JUBILEE", "CIC INSURANCE", "APA INSURANCE", "MADISON", "OLD MUTUAL",
        "RESOLUTION", "AAR", "GA INSURANCE", "HERITAGE", "UAP", "SANLAM",
        "KENINDIA", "PIONEER", "FIRST ASSURANCE", "TAKAFUL", "LIBERTY",
        "INSURANCE", "ASSURANCE", "BRITAM"
    ),
    MerchantSubcategory.LOANS_CREDIT to listOf(
        "TALA", "BRANCH", "ZENKA", "OKOA", "TIMIZA", "HUSTLER FUND",
        "STAWI", "MCOOP CASH", "M-SHWARI LOAN", "KCB MPESA LOAN"
    ),
    MerchantSubcategory.BUSINESS_SERVICES to listOf(
        "SASA PAY", "PESAPAL", "KOPO KOPO", "CELLULANT",
        "FLUTTERWAVE", "INTASEND", "DPO GROUP", "JAMBOPAY"
    ),
    MerchantSubcategory.AIRTIME_DATA to listOf(
        "Airtel Money Bundles", "TINGG", "DIRECT PAY"
    ),
    MerchantSubcategory.UNACCOUNTED_GAP to listOf(
        "UNACCOUNTED (GAP DETECTED)"
    )
)

private val FLAT_KEYWORD_LOOKUP: List<Pair<String, MerchantSubcategory>> =
    MERCHANT_KEYWORDS.flatMap { (category, keywords) -> keywords.map { it to category } }
        .sortedByDescending { it.first.length } // longer keywords first to avoid partial mismatches

object MerchantCategoryManager {
    private const val PREFS = "pesalyzer_merchant_overrides"

    fun getOverride(context: Context, counterparty: String): MerchantSubcategory? {
        val key = normalize(counterparty)
        val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(key, null) ?: return null
        return try { MerchantSubcategory.valueOf(stored) } catch (_: Exception) { null }
    }

    fun setOverride(context: Context, counterparty: String, subcategory: MerchantSubcategory) {
        val key = normalize(counterparty)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit { putString(key, subcategory.name) }
    }

    private fun normalize(counterparty: String): String =
        counterparty.trim().uppercase()
}

object MerchantCategoryEngine {

    /**
     * Categorises a transaction into a real-world subcategory.
     * Checks: fixed type mappings → user override → keyword match → Other.
     */
    fun categorize(context: Context, tx: MpesaTransaction): MerchantSubcategory {
        // Fixed mappings by M-Pesa transaction type — no ambiguity, no need for matching
        when (tx.type) {
            TransactionType.SEND_MONEY -> return MerchantSubcategory.PERSONAL_TRANSFER
            TransactionType.AIRTIME -> return MerchantSubcategory.AIRTIME_DATA
            TransactionType.SAFARICOM_DATA_BUNDLES -> return MerchantSubcategory.AIRTIME_DATA
            TransactionType.WITHDRAW -> return MerchantSubcategory.CASH_WITHDRAWAL
            TransactionType.ZIIDI, TransactionType.MALI, TransactionType.M_SHWARI ->
                return MerchantSubcategory.SAVINGS_INVESTMENT
            TransactionType.FULIZA -> return MerchantSubcategory.OVERDRAFT
            TransactionType.CHARITY -> return MerchantSubcategory.CHARITY_GIVING
            TransactionType.GLOBAL_PAY -> return MerchantSubcategory.BUSINESS_SERVICES
            TransactionType.REVERSAL -> return MerchantSubcategory.REVERSALS
            TransactionType.UNACCOUNTED_ADJUSTMENT -> return MerchantSubcategory.UNACCOUNTED_GAP
            else -> { /* fall through to merchant matching */ }
        }

        // Only merchant-match transaction types where counterparty is a real business
        val eligibleForMatching = tx.type == TransactionType.BUY_GOODS ||
                tx.type == TransactionType.PAY_BILL ||
                tx.type == TransactionType.POCHI_LA_BIASHARA ||
                tx.type == TransactionType.LIPA_MDOGO_MDOGO

        if (!eligibleForMatching) {
            return MerchantSubcategory.OTHER
        }

        // 1. User override takes priority — this is the "learning" mechanism
        getOverride(context, tx.counterparty)?.let { return it }

        // 2. Keyword dictionary match
        val upperName = tx.counterparty.uppercase()
        FLAT_KEYWORD_LOOKUP.firstOrNull { (keyword, _) -> upperName.contains(keyword) }
            ?.let { return it.second }

        // 3. Fallback
        return MerchantSubcategory.OTHER
    }

    private fun getOverride(context: Context, counterparty: String): MerchantSubcategory? =
        MerchantCategoryManager.getOverride(context, counterparty)

    /**
     * Computes total spend per subcategory across a set of debit transactions.
     */
    fun computeBreakdown(
        context: Context,
        transactions: List<MpesaTransaction>
    ): List<Pair<MerchantSubcategory, Double>> {
        val debits = transactions.filter { it.isDebit }
        return debits
            .groupBy { categorize(context, it) }
            .mapValues { (_, txs) -> txs.sumOf { it.amount + it.transactionCost } }
            .toList()
            .sortedByDescending { it.second }
    }
}
