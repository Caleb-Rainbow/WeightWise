package com.example.weight.data.record

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class Record(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val weight: Double,
    val timestamp: Long
)

data class DailyMinWeight(
    val minWeight: Double,
    val recordDay: String, // Format: YYYY-MM-DD
    val timestamp: Long
)

