package com.example.weight.data

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.weight.data.record.RecordDao
import com.example.weight.data.record.Record

@Database(version = 3, entities = [Record::class], exportSchema = true, autoMigrations = [AutoMigration(from = 1, to = 2),AutoMigration(from = 2, to = 3)])
abstract class AppDataBase : RoomDatabase() {
    abstract fun recordDao(): RecordDao
}