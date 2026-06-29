package com.example.my_mpesa_tracker.util

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import androidx.core.content.edit

/**
 * For the things Pesalyzer genuinely can't see: bank balances outside
 * M-Pesa, property, other loans. Clearly separated from auto-tracked
 * figures everywhere it's displayed — never silently merged in as if
 * it were derived data.
 */

enum class ManualEntryType { ASSET, LIABILITY }

data class ManualNetWorthEntry(
    val id: String,
    val name: String,
    val amount: Double,
    val type: ManualEntryType
)

object ManualNetWorthManager {
    private const val PREFS = "pesalyzer_networth"
    private const val KEY = "manual_entries"

    fun getAllEntries(context: Context): List<ManualNetWorthEntry> {
        val json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, "[]") ?: "[]"
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                ManualNetWorthEntry(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    amount = obj.getDouble("amount"),
                    type = ManualEntryType.valueOf(obj.getString("type"))
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun addEntry(context: Context, name: String, amount: Double, type: ManualEntryType) {
        val entries = getAllEntries(context) + ManualNetWorthEntry(UUID.randomUUID().toString(), name, amount, type)
        persist(context, entries)
    }

    fun deleteEntry(context: Context, id: String) {
        persist(context, getAllEntries(context).filterNot { it.id == id })
    }

    private fun persist(context: Context, entries: List<ManualNetWorthEntry>) {
        val arr = JSONArray()
        entries.forEach { e ->
            arr.put(JSONObject().apply {
                put("id", e.id)
                put("name", e.name)
                put("amount", e.amount)
                put("type", e.type.name)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit { putString(KEY, arr.toString()) }
    }
}