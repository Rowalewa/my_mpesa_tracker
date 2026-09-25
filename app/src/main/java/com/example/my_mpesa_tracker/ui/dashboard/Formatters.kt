package com.example.my_mpesa_tracker.ui.dashboard

import com.example.my_mpesa_tracker.data.model.TransactionType
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
