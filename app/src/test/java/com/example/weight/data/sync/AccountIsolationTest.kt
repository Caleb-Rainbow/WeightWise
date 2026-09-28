package com.example.weight.data.sync

import android.app.Application
import androidx.room.Room
import com.example.weight.data.AppDataBase
import com.example.weight.data.record.Record
import com.example.weight.data.diet.DietRecord
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AccountIsolationTest {
    private lateinit var db: AppDataBase
    @Before fun setup() {
        ActiveAccount.id = 0
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDataBase::class.java).allowMainThreadQueries().build()
    }
    @After fun close() { ActiveAccount.id = 0; db.close() }

    @Test fun `account switch isolates queries stats export and pending writes`() = runTest {
        val records = db.recordDao(); val diets = db.dietRecordDao(); val sync = db.syncDao()
        records.insert(Record(weight = 72.0, log = "guest", timestamp = 1000))
        sync.bindWeights(10)
        sync.session(SyncAccount(userId = 10)); ActiveAccount.id = 10
        diets.insert(DietRecord(date = "2026-09-28", timestamp = 2000, mealType = "LUNCH", recognizedFoodJson = "[]", estimatedCalories = 500))
        assertEquals(1, records.getAllOnce().size)
        assertEquals(500, diets.getDailyCalories("2026-09-28"))
        sync.session(SyncAccount(userId = 20)); ActiveAccount.id = 20
        assertTrue(records.getAllOnce().isEmpty())
        assertTrue(records.getDedupKeys().isEmpty())
        assertEquals(0, records.getRecordCount().first())
        assertEquals(0, diets.getDailyCalories("2026-09-28"))
        assertTrue(diets.getAllOnce().isEmpty())
        assertEquals(2, sync.pending(10).first())
        assertEquals(0, sync.pending(20).first())
    }

    @Test fun `soft delete and undo retain identity and current server revision`() = runTest {
        val dao = db.dietRecordDao(); val sync = db.syncDao()
        val id = dao.insert(DietRecord(date="2026-09-28",timestamp=1,mealType="LUNCH",recognizedFoodJson="[]")).toInt()
        val original = dao.findForUpdate(id)!!
        sync.diet(original.copy(revision=7, dirty=false))
        dao.delete(original) // UI may still hold the record from before the last sync.
        assertTrue(dao.getAllOnce().isEmpty())
        val tombstone = sync.diet(0,original.syncId)!!
        assertTrue(tombstone.deleted); assertTrue(tombstone.dirty); assertEquals(7L,tombstone.revision)
        assertNotEquals(original.mutationId,tombstone.mutationId)
        dao.updateAll(listOf(original.copy(deleted=false)))
        val restored = dao.getAllOnce().single()
        assertEquals(original.syncId,restored.syncId);assertEquals(7L,restored.revision);assertFalse(restored.deleted)
    }

    @Test fun `editing a stale screen keeps its base version for conflict detection`() = runTest {
        val dao = db.recordDao(); val sync = db.syncDao()
        val id = dao.insert(Record(weight=70.0,log="original",timestamp=1)).toInt()
        val original = dao.findForUpdate(id)!!.copy(revision=7,dirty=false)
        sync.weight(original)
        // Another device changed the row while the edit sheet was open.
        sync.weight(original.copy(weight=71.0,revision=9,mutationId="remote-edit"))
        dao.update(original.copy(weight=72.0))
        val pending = dao.findForUpdate(id)!!
        assertEquals(7L,pending.revision)
        assertTrue(pending.dirty)
        assertEquals(72.0,pending.weight,0.0)
    }
}
