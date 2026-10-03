package com.example.weight.data.record

import com.example.weight.data.scale.BodyFatCalculator
import org.junit.Assert.*
import org.junit.Test

class RecordCompositionEditingTest {
    private val input = CompositionInputs(true,30,170,70.0,1_700_000_000_000L,
        scaleFatRatio = 20.0,scaleWaterRatio = 50.0,scaleMuscleRatio = 40.0)
    private val c get() = BodyFatCalculator.resolve(input)!!
    private fun original() = Record.create(70.0,"before",input.measuredAt,c)

    @Test fun weightChangeClearsSnapshotAndAllThreeRedundantColumns() {
        val updated = original().withEditedDetails(70.1,"after",input.measuredAt)
        assertEquals("",updated.bodyComposition)
        assertEquals(0.0,updated.fatRatio,0.0)
        assertEquals(0.0,updated.muscleRatio,0.0)
        assertEquals(0.0,updated.waterRatio,0.0)
        assertEquals("after",updated.log)
        assertEquals(70.1,updated.weight,0.0)
    }

    @Test fun editingOnlyLogOrTimestampPreservesOriginalMeasurementInputs() {
        val old = original()
        val updated = old.withEditedDetails(70.0,"after",input.measuredAt+1000L)
        assertEquals(old.bodyComposition,updated.bodyComposition)
        assertEquals(input,BodyCompositionJson.decode(updated.bodyComposition)!!.inputs)
    }

    @Test fun createCannotAttachMeasurementToDifferentWeightEvenWithinOldTolerance() {
        val updated = Record.create(70.1,"",input.measuredAt,c)
        assertEquals("",updated.bodyComposition)
        assertEquals(0.0,updated.fatRatio,0.0)
    }

    @Test fun unchangedWeightKeepsFullPrecisionAndRedundantColumnsInSync() {
        val r = original()
        val parsed = BodyCompositionJson.decode(r.bodyComposition)!!
        assertEquals(parsed.fatRatio,r.fatRatio,0.0)
        assertEquals(parsed.waterRatio,r.waterRatio,0.0)
        assertEquals(parsed.muscleRatio,r.muscleRatio,0.0)
    }
}
