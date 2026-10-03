package com.example.weight.data.scale

import com.example.weight.data.record.*
import org.junit.Assert.*
import org.junit.Test

class BodyCompositionRecalculatorTest {
    private val input = CompositionInputs(
        sexMale = true, age = 24, heightCm = 185, weightKg = 101.9,
        measuredAt = 1_700_000_000_000L, waistCm = 100.0, waistMeasuredAt = 1_700_000_000_000L,
        impedanceOhm = 536.0, biaMethod = BiaMethod.FOOT_TO_FOOT, sourceId = "scale-A",
    )
    private fun record(c: BodyComposition) = Record.create(input.weightKg, "keep log", input.measuredAt, c)

    @Test fun legacyRecordWithoutHistoricalProfileCannotBeRecalculated() {
        val r = record(BodyComposition(fatRatio = 43.8, impedance = 536, fatMethod = "sun2003"))
        assertFalse(BodyCompositionRecalculator.isEligible(r))
        assertNull(BodyCompositionRecalculator.recalculateRecord(r))
        assertEquals(43.8, BodyCompositionJson.decode(r.bodyComposition)!!.fatRatio, 0.0)
    }

    @Test fun recalculationUsesOnlyArchivedWaistAndProfile() {
        val r = record(BodyComposition(fatRatio = 32.9, impedance = 536, inputs = input))
        val updated = BodyCompositionRecalculator.recalculateRecord(r)!!
        val c = BodyCompositionJson.decode(updated.bodyComposition)!!
        assertEquals(27.0, c.fatRatio, 1e-9)
        assertEquals("rfm", c.fatMethod)
        assertEquals(input, c.inputs)
        assertEquals(2, c.algorithmVersion)
        assertEquals(c.fatRatio, updated.fatRatio, 0.0)
        assertEquals(c.waterRatio, updated.waterRatio, 0.0)
        assertEquals(c.muscleRatio, updated.muscleRatio, 0.0)
        assertEquals(r.weight, updated.weight, 0.0)
        assertEquals(r.timestamp, updated.timestamp)
        assertEquals(r.log, updated.log)
    }

    @Test fun staleArchivedWaistIsNotPromotedToCurrent() {
        val saved = input.copy(waistMeasuredAt = input.measuredAt - 86_400_000L)
        val c = BodyCompositionJson.decode(BodyCompositionRecalculator.recalculateRecord(
            record(BodyComposition(inputs = saved, impedance = 536)),
        )!!.bodyComposition)!!
        assertEquals("deurenberg", c.fatMethod)
    }

    @Test fun archivedScaleValuesCanBeReplayedWithoutImpedance() {
        val saved = input.copy(impedanceOhm = null, scaleFatRatio = 22.0, scaleWaterRatio = 50.0)
        val r = record(BodyComposition(inputs = saved, fatRatio = 22.0))
        assertTrue(BodyCompositionRecalculator.isEligible(r))
        val c = BodyCompositionJson.decode(BodyCompositionRecalculator.recalculateRecord(r)!!.bodyComposition)!!
        assertEquals("scale_reported", c.fatMethod)
        assertEquals(22.0, c.fatRatio, 0.0)
        assertEquals(50.0, c.waterRatio, 0.0)
    }

    @Test fun invalidOrMismatchedHistoricalInputsAreSkipped() {
        for (saved in listOf(input.copy(sexMale = null), input.copy(age = 0),
            input.copy(measuredAt = 0L), input.copy(weightKg = 80.0))) {
            // Bypass create's matching defense to exercise an imported broken record.
            val r = record(BodyComposition()).copy(bodyComposition = BodyCompositionJson.encode(BodyComposition(inputs = saved)))
            assertNull(BodyCompositionRecalculator.recalculateRecord(r))
        }
    }

    @Test fun corruptAndEmptyJsonAreSkipped() {
        for (json in listOf("", "not-json{")) {
            val r = record(BodyComposition()).copy(bodyComposition = json)
            assertFalse(BodyCompositionRecalculator.isEligible(r))
            assertNull(BodyCompositionRecalculator.recalculateRecord(r))
        }
    }

    @Test fun repeatedReplayIsStable() {
        val original = record(BodyFatCalculator.resolve(input)!!)
        assertEquals(original, BodyCompositionRecalculator.recalculateRecord(original))
    }
}
