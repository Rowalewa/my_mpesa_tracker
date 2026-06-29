package com.example.my_mpesa_tracker.util

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID
import androidx.core.content.edit

data class GoalContribution(
    val amount: Double,
    val date: LocalDate,
    val note: String = ""
)

data class SavingsGoal(
    val id: String,
    val name: String,
    val emoji: String,
    val targetAmount: Double,
    val targetDate: LocalDate?,
    val createdDate: LocalDate,
    val contributions: List<GoalContribution> = emptyList()
) {
    val totalSaved: Double get() = contributions.sumOf { it.amount }
    val progress: Float get() = if (targetAmount > 0)
        (totalSaved / targetAmount).toFloat().coerceIn(0f, 1f) else 0f
    val isComplete: Boolean get() = totalSaved >= targetAmount

    val projectedCompletionDate: LocalDate? get() {
        if (contributions.size < 2 || isComplete) return null
        val daysSinceStart = ChronoUnit.DAYS.between(createdDate, LocalDate.now()).coerceAtLeast(1)
        val avgPerDay = totalSaved / daysSinceStart
        if (avgPerDay <= 0) return null
        val remaining = targetAmount - totalSaved
        val daysNeeded = (remaining / avgPerDay).toLong()
        return LocalDate.now().plusDays(daysNeeded)
    }

    val isOnTrack: Boolean? get() {
        val target = targetDate ?: return null
        val projected = projectedCompletionDate ?: return if (isComplete) true else null
        return !projected.isAfter(target)
    }
}

object GoalManager {
    private const val PREFS = "pesalyzer_goals"
    private const val KEY = "goals_json"

    fun getAllGoals(context: Context): List<SavingsGoal> {
        val json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, "[]") ?: "[]"
        return try { parseGoals(json) } catch (_: Exception) { emptyList() }
    }

    fun createGoal(context: Context, name: String, emoji: String, targetAmount: Double, targetDate: LocalDate?) {
        val goal = SavingsGoal(
            id = UUID.randomUUID().toString(),
            name = name,
            emoji = emoji,
            targetAmount = targetAmount,
            targetDate = targetDate,
            createdDate = LocalDate.now()
        )
        val goals = getAllGoals(context) + goal
        persist(context, goals)
    }

    fun deleteGoal(context: Context, goalId: String) {
        persist(context, getAllGoals(context).filterNot { it.id == goalId })
    }

    fun addContribution(context: Context, goalId: String, contribution: GoalContribution) {
        val goals = getAllGoals(context).map { g ->
            if (g.id == goalId) g.copy(contributions = g.contributions + contribution) else g
        }
        persist(context, goals)
    }

    private fun persist(context: Context, goals: List<SavingsGoal>) {
        val arr = JSONArray()
        goals.forEach { g ->
            val obj = JSONObject().apply {
                put("id", g.id)
                put("name", g.name)
                put("emoji", g.emoji)
                put("targetAmount", g.targetAmount)
                put("targetDate", g.targetDate?.toString() ?: "")
                put("createdDate", g.createdDate.toString())
                val contribArr = JSONArray()
                g.contributions.forEach { c ->
                    contribArr.put(JSONObject().apply {
                        put("amount", c.amount)
                        put("date", c.date.toString())
                        put("note", c.note)
                    })
                }
                put("contributions", contribArr)
            }
            arr.put(obj)
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit { putString(KEY, arr.toString()) }
    }

    private fun parseGoals(json: String): List<SavingsGoal> {
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            val contribArr = obj.getJSONArray("contributions")
            val contributions = (0 until contribArr.length()).map { j ->
                val cObj = contribArr.getJSONObject(j)
                GoalContribution(
                    amount = cObj.getDouble("amount"),
                    date = LocalDate.parse(cObj.getString("date")),
                    note = cObj.optString("note", "")
                )
            }
            val targetDateStr = obj.optString("targetDate", "")
            SavingsGoal(
                id = obj.getString("id"),
                name = obj.getString("name"),
                emoji = obj.getString("emoji"),
                targetAmount = obj.getDouble("targetAmount"),
                targetDate = if (targetDateStr.isBlank()) null else LocalDate.parse(targetDateStr),
                createdDate = LocalDate.parse(obj.getString("createdDate")),
                contributions = contributions
            )
        }
    }
}
