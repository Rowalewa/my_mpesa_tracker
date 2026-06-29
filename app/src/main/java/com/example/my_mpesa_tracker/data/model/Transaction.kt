package com.example.my_mpesa_tracker.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class TransactionType {
    SEND_MONEY,
    RECEIVE_MONEY,
    BUY_GOODS,
    PAY_BILL,
    POCHI_LA_BIASHARA,
    WITHDRAW,
    DEPOSIT,
    AIRTIME,
    SAFARICOM_DATA_BUNDLES,
    ZIIDI,
    MALI,
    M_SHWARI,
    FULIZA,
    REVERSAL,
    KCB_MPESA,
    GLOBAL_PAY,
    LIPA_MDOGO_MDOGO,
    CHARITY,
    UNACCOUNTED_ADJUSTMENT,
    UNKNOWN
}

@Entity(tableName = "transactions")
data class MpesaTransaction(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val mpesaCode: String,           // e.g. QDK3X2YZ1A
    val amount: Double,
    val type: TransactionType,
    val counterparty: String,        // who you sent to / received from
    val balanceAfter: Double,
    val timestamp: Long,             // epoch millis
    val rawSms: String,              // original SMS for audit
    val isDebit: Boolean,            // true = money out, false = money in
    val transactionCost: Double = 0.0,
    val subscriptionId: Int = -1
)
