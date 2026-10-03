package com.example.weight.data.record

import androidx.room.Room
import com.example.weight.data.AppDataBase
import com.example.weight.data.scale.BodyFatCalculator
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class CompositionRecordUpdateTest {
    private lateinit var db: AppDataBase
    private lateinit var dao: RecordDao
    private val input = CompositionInputs(true,30,170,70.0,1_700_000_000_000L,
        scaleFatRatio = 20.0,scaleWaterRatio = 50.0,scaleMuscleRatio = 40.0)
    private val result get() = BodyFatCalculator.resolve(input)!!

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),AppDataBase::class.java)
            .allowMainThreadQueries().build()
        dao = db.recordDao()
    }
    @After fun teardown() { db.close() }

    private suspend fun saved(): Record {
        val original = Record.create(70.0,"before",input.measuredAt,
            BodyComposition(fatRatio = 22.0,inputs = input))
        val id = dao.insert(original).toInt()
        return dao.findForUpdate(id)!!
    }

    @Test fun recalculationDoesNotOverwriteAConcurrentWeightEdit() = runBlocking {
        val old = saved()
        dao.update(old.withEditedDetails(71.0,"edited",old.timestamp))
        assertFalse(dao.updateCompositionIfUnchanged(old,result))
        val current = dao.findForUpdate(old.id)!!
        assertEquals(71.0,current.weight,0.0)
        assertEquals("",current.bodyComposition)
        assertEquals("edited",current.log)
    }

    @Test fun recalculationPreservesConcurrentLogEditAndWritesAllColumns() = runBlocking {
        val old = saved()
        dao.update(old.withEditedDetails(70.0,"new log",old.timestamp+1000))
        assertTrue(dao.updateCompositionIfUnchanged(old,result))
        val current = dao.findForUpdate(old.id)!!
        assertEquals("new log",current.log)
        assertEquals(old.timestamp+1000,current.timestamp)
        assertEquals(result,BodyCompositionJson.decode(current.bodyComposition))
        assertEquals(result.fatRatio,current.fatRatio,0.0)
        assertEquals(result.waterRatio,current.waterRatio,0.0)
        assertEquals(result.muscleRatio,current.muscleRatio,0.0)
        assertTrue(current.dirty)
    }

    @Test fun deletionDuringRecalculationIsNotResurrected() = runBlocking {
        val old = saved()
        dao.delete(old)
        assertFalse(dao.updateCompositionIfUnchanged(old,result))
        assertTrue(dao.findForUpdate(old.id)!!.deleted)
    }

    @Test fun anotherCompositionUpdateIsNotLost() = runBlocking {
        val old = saved()
        dao.update(old.copy(bodyComposition = BodyCompositionJson.encode(result),fatRatio = result.fatRatio))
        assertFalse(dao.updateCompositionIfUnchanged(old,result))
    }
}
