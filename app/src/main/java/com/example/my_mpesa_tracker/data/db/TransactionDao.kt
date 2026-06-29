package com.example.my_mpesa_tracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.my_mpesa_tracker.data.model.MpesaTransaction
import com.example.my_mpesa_tracker.data.model.TransactionType
import kotlinx.coroutines.flow.Flow

data class SubscriptionSummary(
    val subscriptionId: Int,
    val count: Int,
    val minTs: Long,
    val maxTs: Long
)


@Dao
interface TransactionDao {
//    @Query("DELETE FROM transactions")
//    suspend fun deleteAllTransactions()
    @Query("SELECT * FROM transactions WHERE subscriptionId = :subId ORDER BY timestamp ASC")
    suspend fun getAllForSubscriptionOrdered(subId: Int): List<MpesaTransaction>

    @Query("SELECT DISTINCT subscriptionId FROM transactions")
    suspend fun getDistinctSubscriptions(): List<Int>
    @Query("SELECT * FROM transactions WHERE subscriptionId = :subId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastTransactionForSubscription(subId: Int): MpesaTransaction?

    @Query("""
    SELECT subscriptionId, COUNT(*) as count, MIN(timestamp) as minTs, MAX(timestamp) as maxTs
    FROM transactions
    GROUP BY subscriptionId
    ORDER BY maxTs DESC
""")
    suspend fun getSubscriptionSummary(): List<SubscriptionSummary>

    @Query("DELETE FROM transactions WHERE subscriptionId = :subId")
    suspend fun deleteBySubscription(subId: Int): Int

    @Query("SELECT COUNT(*) FROM transactions WHERE mpesaCode = :code AND amount = :amount AND isDebit = :isDebit")
    suspend fun countByCodeAndDetails(code: String, amount: Double, isDebit: Boolean): Long

    @Query("DELETE FROM transactions WHERE id NOT IN (SELECT MIN(id) FROM transactions GROUP BY mpesaCode)")
    suspend fun removeDuplicateTransactions(): Int

    @Query("DELETE FROM transactions WHERE balanceAfter = 0.0")
    suspend fun deleteZeroBalanceEntries(): Int

    // ── Insert Live / Single Transactions (Changed to REPLACE to allow parser updates) ──
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: MpesaTransaction): Long

    // ── High-performance batch insert for historical sweeps (Changed to REPLACE) ──
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(transactions: List<MpesaTransaction>)

    // ── Live feeds (Flow = real-time UI updates) ─────────────────────────
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastTransaction(): MpesaTransaction?

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun allTransactions(): Flow<List<MpesaTransaction>>

    @Query("SELECT * FROM transactions WHERE timestamp >= :from AND timestamp <= :to ORDER BY timestamp DESC")
    fun transactionsInRange(from: Long, to: Long): Flow<List<MpesaTransaction>>

    // ── Stats queries ────────────────────────────────────────────────────
    @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE isDebit = 1 AND timestamp >= :from AND timestamp <= :to")
    fun totalDebit(from: Long, to: Long): Flow<Double>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE isDebit = 0 AND timestamp >= :from AND timestamp <= :to")
    fun totalCredit(from: Long, to: Long): Flow<Double>

    @Query("SELECT COALESCE(MAX(amount), 0) FROM transactions WHERE isDebit = 1 AND timestamp >= :from AND timestamp <= :to")
    fun maxDebit(from: Long, to: Long): Flow<Double>

    @Query("SELECT COALESCE(MIN(amount), 0) FROM transactions WHERE isDebit = 1 AND timestamp >= :from AND timestamp <= :to")
    fun minDebit(from: Long, to: Long): Flow<Double>

    @Query("SELECT COALESCE(AVG(amount), 0) FROM transactions WHERE isDebit = 1 AND timestamp >= :from AND timestamp <= :to")
    fun avgDebit(from: Long, to: Long): Flow<Double>

    @Query("SELECT COUNT(*) FROM transactions WHERE timestamp >= :from AND timestamp <= :to")
    fun countInRange(from: Long, to: Long): Flow<Int>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE type = :type AND timestamp >= :from AND timestamp <= :to")
    fun totalByType(type: TransactionType, from: Long, to: Long): Flow<Double>

    // Daily totals for chart (group by day)
    @Query("""
        SELECT 
            (timestamp / 86400000) * 86400000 AS day,
            SUM(CASE WHEN isDebit = 1 THEN amount ELSE 0 END) AS spent,
            SUM(CASE WHEN isDebit = 0 THEN amount ELSE 0 END) AS received
        FROM transactions
        WHERE timestamp >= :from AND timestamp <= :to
        GROUP BY day
        ORDER BY day ASC
    """)
    fun dailyTotals(from: Long, to: Long): Flow<List<DailyTotal>>

    @Query("SELECT COUNT(*) FROM transactions WHERE mpesaCode = :code")
    suspend fun countByCode(code: String): Long
}

data class DailyTotal(
    val day: Long,
    val spent: Double,
    val received: Double
)