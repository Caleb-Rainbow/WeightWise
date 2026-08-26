package com.example.weight.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** 11→12 自动迁移回归：新增 Health Connect 来源列与索引，同时保留既有数据。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class Migration11To12Test {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        context.getDatabasePath(DB_NAME).let { it.parentFile?.mkdirs(); it.delete() }
        createV11Database()
    }

    @After
    fun tearDown() {
        context.getDatabasePath(DB_NAME).delete()
    }

    @Test
    fun `迁移保留数据并为两个实体补来源列和索引`() {
        val room = Room.databaseBuilder(context, AppDataBase::class.java, DB_NAME)
            .allowMainThreadQueries()
            .build()
        try {
                val db = room.openHelper.writableDatabase
                db.query("SELECT weight, healthConnectId, healthConnectOrigin FROM Record").use {
                    assertTrue(it.moveToFirst())
                    assertEquals(70.5, it.getDouble(0), 0.0)
                    assertEquals("", it.getString(1))
                    assertEquals("", it.getString(2))
                }
                db.query("SELECT estimatedCalories, healthConnectId, healthConnectOrigin FROM DietRecord").use {
                    assertTrue(it.moveToFirst())
                    assertEquals(520, it.getInt(0))
                    assertEquals("", it.getString(1))
                    assertEquals("", it.getString(2))
                }
                assertTrue(indexNames(db, "Record").contains("index_Record_healthConnectId"))
                assertTrue(indexNames(db, "DietRecord").contains("index_DietRecord_healthConnectId"))
        } finally {
            room.close()
        }
    }

    private fun createV11Database() {
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(DB_NAME), null).use { db ->
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `Record` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`weight` REAL NOT NULL, `log` TEXT NOT NULL DEFAULT '', `timestamp` INTEGER NOT NULL, " +
                    "`bodyComposition` TEXT NOT NULL DEFAULT '', `fatRatio` REAL NOT NULL DEFAULT 0.0, " +
                    "`muscleRatio` REAL NOT NULL DEFAULT 0.0, `waterRatio` REAL NOT NULL DEFAULT 0.0)"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_Record_timestamp` ON `Record` (`timestamp`)")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `DietRecord` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`date` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `mealType` TEXT NOT NULL, " +
                    "`imageUri` TEXT NOT NULL DEFAULT '', `userInput` TEXT NOT NULL DEFAULT '', " +
                    "`recognizedFoodJson` TEXT NOT NULL, `estimatedCalories` INTEGER NOT NULL DEFAULT 0, " +
                    "`trafficLight` TEXT NOT NULL DEFAULT '')"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_DietRecord_date` ON `DietRecord` (`date`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_DietRecord_timestamp` ON `DietRecord` (`timestamp`)")
            db.execSQL("INSERT INTO Record (weight, timestamp) VALUES (70.5, 1700000000000)")
            db.execSQL(
                "INSERT INTO DietRecord (date, timestamp, mealType, recognizedFoodJson, estimatedCalories) " +
                    "VALUES ('2026-08-26', 1700000000000, 'LUNCH', '[]', 520)"
            )
            db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
            db.execSQL(
                "INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES " +
                    "(42, 'b44f39f5b9b86a8f7bc9b835c0fe9402')"
            )
            db.version = 11
        }
    }

    private fun indexNames(db: androidx.sqlite.db.SupportSQLiteDatabase, table: String): Set<String> =
        db.query("PRAGMA index_list(`$table`)").use { cursor ->
            buildSet {
                val nameColumn = cursor.getColumnIndexOrThrow("name")
                while (cursor.moveToNext()) add(cursor.getString(nameColumn))
            }
        }

    private companion object {
        const val DB_NAME = "migration-11-12-test.db"
    }
}
