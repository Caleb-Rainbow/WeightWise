package com.example.weight.data

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.weight.data.exercise.DailyPlan
import com.example.weight.data.exercise.ExerciseCompletion
import com.example.weight.data.exercise.ExercisePlanDao
import com.example.weight.data.record.RecordDao
import com.example.weight.data.record.Record

@Database(version = 4, entities = [Record::class, DailyPlan::class, ExerciseCompletion::class], exportSchema = true, autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4)])
abstract class AppDataBase : RoomDatabase() {
    abstract fun recordDao(): RecordDao
    abstract fun exercisePlanDao(): ExercisePlanDao
}