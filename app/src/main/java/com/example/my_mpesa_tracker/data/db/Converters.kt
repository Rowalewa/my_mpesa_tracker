package com.example.my_mpesa_tracker.data.db

import androidx.room.TypeConverter
import com.example.my_mpesa_tracker.data.model.TransactionType

class Converters {
    @TypeConverter
    fun fromTransactionType(value: TransactionType): String {
        return value.name
    }

    @TypeConverter
    fun toTransactionType(value: String): TransactionType {
        return try {
            TransactionType.valueOf(value)
        } catch (_: Exception) {
            TransactionType.UNKNOWN
        }
    }
}
