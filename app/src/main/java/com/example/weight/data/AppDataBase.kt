package com.example.weight.data

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.weight.data.exercise.DailyPlan
import com.example.weight.data.exercise.ExerciseCompletion
import com.example.weight.data.exercise.ExercisePlanDao
import com.example.weight.data.exercise.Journey
import com.example.weight.data.exercise.JourneyDao
import com.example.weight.data.exercise.Phase
import com.example.weight.data.record.RecordDao
import com.example.weight.data.record.Record

@Database(version = 6, entities = [Record::class, DailyPlan::class, ExerciseCompletion::class, Journey::class, Phase::class], exportSchema = true, autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4), AutoMigration(from = 4, to = 5), AutoMigration(from = 5, to = 6)])
abstract class AppDataBase : RoomDatabase() {
    abstract fun recordDao(): RecordDao
    abstract fun exercisePlanDao(): ExercisePlanDao
    abstract fun journeyDao(): JourneyDao
}