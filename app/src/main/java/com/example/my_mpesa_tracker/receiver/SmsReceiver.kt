package com.example.my_mpesa_tracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SubscriptionManager
import com.example.my_mpesa_tracker.data.db.AppDatabase
import com.example.my_mpesa_tracker.util.MpesaSmsParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Fires the instant an SMS arrives.
 *
 * Captures which SIM/subscription received the message and scopes
 * previousBalance lookups to that same subscription, so a dual-SIM
 * phone with two Safaricom lines never has their balance chains
 * cross-contaminate each other.
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        val receivedAt = System.currentTimeMillis()

        // Documented public API since Android 5.1 (API 22) for reading
        // which subscription an SMS_RECEIVED broadcast came from.
        val subscriptionId = intent.getIntExtra(
            SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, -1
        )

        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.getInstance(context)
            val dao = db.transactionDao()

            messages.forEach { sms ->
                val sender = sms.originatingAddress ?: return@forEach
                val body = sms.messageBody ?: return@forEach

                // Per-SIM balance chain — this is the fix. Previously
                // this queried the single most recent transaction
                // across ALL SIMs, which is exactly what let two
                // interleaved lines corrupt each other's isDebit
                // determination for Ziidi/Mali/M-Shwari.
                val lastTransaction = if (subscriptionId != -1) {
                    dao.getLastTransactionForSubscription(subscriptionId)
                } else {
                    dao.getLastTransaction()
                }
                val previousBalance = lastTransaction?.balanceAfter

                val transaction = MpesaSmsParser.parse(
                    sender = sender,
                    body = body,
                    receivedAt = receivedAt,
                    previousBalance = previousBalance,
                    subscriptionId = subscriptionId
                ) ?: return@forEach

                if (dao.countByCodeAndDetails(
                        transaction.mpesaCode, transaction.amount, transaction.isDebit
                    ) == 0L
                ) {
                    dao.insert(transaction)
                }
            }
        }
    }
}
