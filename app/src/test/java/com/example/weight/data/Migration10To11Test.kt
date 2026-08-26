package com.example.weight.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 迁移 10→11 回归：Record 冗余三列（fatRatio/muscleRatio/waterRatio），
 * 历史成分 JSON 经 json_extract 回填；空串与坏 JSON 行守卫为 0.0 不崩溃。
 *
 * 不用 MigrationTestHelper（其依赖 instrumented assets 通道，unit test 变体不可用）：
 * 手工按 10.json 的 createSql 建 v10 库，走 SupportSQLiteOpenHelper 的 onUpgrade
 * 触发 MIGRATION_10_11.migrate——与 Room 运行时触发路径同一条。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class Migration10To11Test {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        context.getDatabasePath(DB_NAME).let { it.parentFile?.mkdirs(); it.delete() }
    }

    @Test
    fun `历史成分JSON回填三列_空串与坏JSON行保持零值`() {
        createV10Database()
        migrateThroughRoomPath(10, 11).use { db ->
            db.query("SELECT fatRatio, muscleRatio, waterRatio FROM Record WHERE timestamp = $TS_SCALED").use {
                it.moveToFirst()
                assertEquals(22.5, it.getDouble(0), 1e-9)
                assertEquals(42.3, it.getDouble(1), 1e-9)
                assertEquals(55.1, it.getDouble(2), 1e-9)
            }
            db.query("SELECT fatRatio FROM Record WHERE timestamp = $TS_MANUAL").use {
                it.moveToFirst()
                assertEquals(0.0, it.getDouble(0), 1e-9)
            }
            db.query("SELECT waterRatio FROM Record WHERE timestamp = $TS_BROKEN").use {
                it.moveToFirst()
                assertEquals(0.0, it.getDouble(0), 1e-9)
            }
            db.query("SELECT COUNT(*) FROM Record WHERE fatRatio + muscleRatio + waterRatio > 0").use {
                it.moveToFirst()
                assertEquals(1, it.getInt(0))
            }
        }
    }

    /** 按 10.json 的 createSql 手工建 v10 库，插入三种代表性行 */
    private fun createV10Database() {
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(DB_NAME), null).use { db ->
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `Record` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`weight` REAL NOT NULL, `log` TEXT NOT NULL DEFAULT '', `timestamp` INTEGER NOT NULL, " +
                    "`bodyComposition` TEXT NOT NULL DEFAULT '')"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_Record_timestamp` ON `Record` (`timestamp`)")
            db.execSQL(
                "INSERT INTO Record (weight, log, timestamp, bodyComposition) VALUES (70.5, '', $TS_SCALED, ?)",
                arrayOf("""{"fatRatio":22.5,"waterRatio":55.1,"muscleRatio":42.3,"impedance":500}"""),
            )
            db.execSQL("INSERT INTO Record (weight, log, timestamp, bodyComposition) VALUES (71.0, '', $TS_MANUAL, '')")
            db.execSQL(
                "INSERT INTO Record (weight, log, timestamp, bodyComposition) VALUES (71.2, '', $TS_BROKEN, ?)",
                arrayOf("{broken json"),
            )
            db.version = 10
        }
    }

    /** 经 SupportSQLiteOpenHelper 打开更高版本号触发 onUpgrade → MIGRATION_10_11.migrate（Room 同路径） */
    private fun migrateThroughRoomPath(from: Int, to: Int): SupportSQLiteDatabase {
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(DB_NAME)
                .callback(object : SupportSQLiteOpenHelper.Callback(to) {
                    override fun onCreate(db: SupportSQLiteDatabase) = Unit

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                        // 只验证 10→11 这一段：1→10 由既有 AutoMigration 在真机上覆盖
                        check(oldVersion == from) { "期望从 v$from 升级，实际 v$oldVersion" }
                        MIGRATION_10_11.migrate(db)
                    }
                })
                .build()
        )
        return helper.writableDatabase
    }

    companion object {
        private const val DB_NAME = "migration-10-11-test.db"
        private const val TS_SCALED = 1_700_000_000_000L
        private const val TS_MANUAL = 1_700_000_001_000L
        private const val TS_BROKEN = 1_700_000_002_000L
    }
}
