package com.example.weight.data.sync

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.weight.data.record.Record
import com.example.weight.data.diet.DietRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import java.util.UUID

object ActiveAccount { @Volatile var id: Long = 0 }

@Entity
data class SyncAccount(@PrimaryKey val id: Int = 1, val userId: Long = 0, val name: String = "", val encryptedToken: String = "")

@Entity
data class SyncProfile(
    @PrimaryKey val ownerId: Long,
    val payload: String = "{}",
    val revision: Long = 0,
    val mutationId: String = UUID.randomUUID().toString(),
    val dirty: Boolean = true,
    val cursor: Long = 0,
    val lastSync: Long = 0,
)

@Entity
data class SyncConflict(@PrimaryKey(autoGenerate = true) val id: Long = 0, val ownerId: Long, val kind: String, val payload: String)

@Serializable
data class SyncChange(val kind: String, val id: String, val revision: Long = 0, val mutationId: String, val deleted: Boolean = false, val payload: JsonObject = JsonObject(emptyMap()))
@Serializable
data class SyncRequest(val cursor: Long, val changes: List<SyncChange>)
@Serializable
data class SyncResponse(val cursor: Long, val hasMore: Boolean, val changes: List<SyncChange>, val accepted: List<SyncChange>, val conflicts: List<SyncChange>)

@Dao
interface SyncDao {
    @Query("SELECT * FROM SyncAccount WHERE id = 1") suspend fun session(): SyncAccount?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun session(value: SyncAccount)
    @Query("SELECT * FROM SyncProfile WHERE ownerId = :owner") suspend fun profile(owner: Long): SyncProfile?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun profile(value: SyncProfile)
    @Query("SELECT * FROM Record WHERE ownerId = :owner AND dirty = 1 LIMIT 50") suspend fun weights(owner: Long): List<Record>
    @Query("SELECT * FROM DietRecord WHERE ownerId = :owner AND dirty = 1 LIMIT 4") suspend fun diets(owner: Long): List<DietRecord>
    @Query("SELECT * FROM Record WHERE ownerId = :owner AND syncId = :id LIMIT 1") suspend fun weight(owner: Long, id: String): Record?
    @Query("SELECT * FROM DietRecord WHERE ownerId = :owner AND syncId = :id LIMIT 1") suspend fun diet(owner: Long, id: String): DietRecord?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun weight(value: Record)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun diet(value: DietRecord)
    @Query("UPDATE Record SET ownerId = :owner WHERE ownerId = 0") suspend fun bindWeights(owner: Long)
    @Query("UPDATE DietRecord SET ownerId = :owner WHERE ownerId = 0") suspend fun bindDiets(owner: Long)
    @Query("SELECT (SELECT COUNT(*) FROM Record WHERE ownerId = :owner AND dirty = 1) + (SELECT COUNT(*) FROM DietRecord WHERE ownerId = :owner AND dirty = 1) + (SELECT COUNT(*) FROM SyncProfile WHERE ownerId = :owner AND dirty = 1)") fun pending(owner: Long): Flow<Int>
    @Query("SELECT * FROM SyncConflict WHERE ownerId = :owner ORDER BY id") fun conflicts(owner: Long): Flow<List<SyncConflict>>
    @Insert suspend fun conflict(value: SyncConflict)
    @Query("DELETE FROM SyncConflict WHERE id = :id AND ownerId = :owner") suspend fun removeConflict(id: Long, owner: Long)
}

val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        for (table in listOf("Record", "DietRecord")) {
            db.execSQL("ALTER TABLE `$table` ADD COLUMN ownerId INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `$table` ADD COLUMN syncId TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `$table` ADD COLUMN revision INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `$table` ADD COLUMN mutationId TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `$table` ADD COLUMN dirty INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE `$table` ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0")
            db.execSQL("UPDATE `$table` SET syncId = lower(hex(randomblob(16))), mutationId = lower(hex(randomblob(16)))")
        }
        db.execSQL("CREATE TABLE IF NOT EXISTS SyncAccount (id INTEGER NOT NULL PRIMARY KEY, userId INTEGER NOT NULL, name TEXT NOT NULL, encryptedToken TEXT NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS SyncProfile (ownerId INTEGER NOT NULL PRIMARY KEY, payload TEXT NOT NULL, revision INTEGER NOT NULL, mutationId TEXT NOT NULL, dirty INTEGER NOT NULL, cursor INTEGER NOT NULL, lastSync INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS SyncConflict (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, ownerId INTEGER NOT NULL, kind TEXT NOT NULL, payload TEXT NOT NULL)")
    }
}
