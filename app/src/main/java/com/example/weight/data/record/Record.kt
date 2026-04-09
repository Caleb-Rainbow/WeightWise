package com.example.weight.data.record

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class Record(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val weight: Double,
    @ColumnInfo(defaultValue = "")
    val log:String,
    val timestamp: Long
)

data class DailyMinWeight(
    val minWeight: Double,
    val recordDay: String, // Format: YYYY-MM-DD
    val timestamp: Long
)

