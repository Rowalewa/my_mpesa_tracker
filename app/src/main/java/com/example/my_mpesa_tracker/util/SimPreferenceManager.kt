package com.example.my_mpesa_tracker.util

import android.content.Context
import com.example.my_mpesa_tracker.data.model.MpesaTransaction
import androidx.core.content.edit

/**
 * Stores the user's explicit choice of which SIM(s)/subscriptions to
 * include in balance-sensitive views (Balance Tracker, Forecast).
 *
 * Never auto-decides silently once a real choice exists — if the user
 * hasn't configured anything yet AND multiple SIMs are present, we
 * default to the most-recently-active one (a reasonable guess) but
 * surface the choice clearly via SimDiagnosticsCard so it's never
 * stuck that way without the person knowing they can change it.
 */
object SimPreferenceManager {
    private const val PREFS = "pesalyzer_sim_prefs"
    private const val KEY = "included_subscription_ids"
    private const val CONFIGURED_KEY = "sim_preference_configured"

    fun hasBeenConfigured(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(CONFIGURED_KEY, false)

    fun getIncludedSubscriptions(context: Context): Set<Int> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, "") ?: ""
        return raw.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
    }

    fun setIncludedSubscriptions(context: Context, ids: Set<Int>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit {
                putString(KEY, ids.joinToString(","))
                    .putBoolean(CONFIGURED_KEY, true)
            }
    }

    fun resetToAutoDefault(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit {
                putBoolean(CONFIGURED_KEY, false)
            }
    }
}

/**
 * Filters a transaction list for balance-sensitive views. Generalises
 * to any number of distinct SIMs found in the data — nothing here
 * assumes exactly one or exactly two.
 */
fun List<MpesaTransaction>.filterByIncludedSims(context: Context): List<MpesaTransaction> {
    if (isEmpty()) return this
    val distinctSims = map { it.subscriptionId }.toSet()
    if (distinctSims.size <= 1) return this // only one SIM present, nothing to filter

    return if (SimPreferenceManager.hasBeenConfigured(context)) {
        val included = SimPreferenceManager.getIncludedSubscriptions(context)
        if (included.isEmpty()) this else filter { it.subscriptionId in included }
    } else {
        // No explicit choice yet — default to whichever SIM has the
        // most recent activity. Surfaced and changeable via
        // SimDiagnosticsCard, never silently permanent.
        val currentSubId = maxByOrNull { it.timestamp }?.subscriptionId
        filter { it.subscriptionId == currentSubId }
    }
}
