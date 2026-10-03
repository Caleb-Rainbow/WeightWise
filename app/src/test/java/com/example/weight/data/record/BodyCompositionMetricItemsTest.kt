package com.example.weight.data.record

import com.example.weight.data.scale.BodyFatCalculator
import org.junit.Assert.*
import org.junit.Test

class BodyCompositionMetricItemsTest {
    private val input = CompositionInputs(true,30,170,70.0,1_700_000_000_000L,
        scaleFatRatio = 20.0, scaleWaterRatio = 50.0, scaleMuscleRatio = 40.0,
        scaleBoneMass = 3.0, impedanceOhm = 500.0)

    @Test fun supportedMeasurementsDisplayInOrderWithEstimatedLabels() {
        val c = BodyFatCalculator.resolve(input)!!
        assertEquals(listOf("fatRatio","waterRatio","muscleMass","ffm","boneMass","impedance"),
            c.metricItems().map { it.key })
        assertTrue(c.metricItems().any { it == MetricDisplay("muscleMass","秤端肌肉量","28 kg") })
        assertTrue(c.metricItems().any { it == MetricDisplay("ffm","去脂体重（估算）","56 kg") })
    }

    @Test fun legacyBrokenSunFatAndSyntheticValuesAreHiddenWithoutDeletingArchive() {
        for (method in listOf("sun2003", "fused_rfm_sun")) {
            val c = BodyComposition(fatRatio = 43.8, impedance = 536, waterRatio = 41.2,
                muscleMass = 54.2, boneMass = 3.2, ffm = 57.3, bodyScore = 100, bodyType = "标准型",
                proteinRatio = 10.0, visceralFatLevel = 7, fatMethod = method)
            val archived = BodyCompositionJson.encode(c)
            assertEquals(listOf("impedance"), c.metricItems().map { it.key })
            assertNull(c.rawValueOf("fatRatio"))
            assertNull(c.rawValueOf("bodyScore"))
            assertEquals(c, BodyCompositionJson.decode(archived))
        }
    }

    @Test fun oldScaleFatSurvivesButAppInventedDerivedFieldsDoNot() {
        val c = BodyComposition(fatRatio = 22.0, waterRatio = 58.0, muscleMass = 55.0,
            boneMass = 3.0, ffm = 58.0, fatMethod = "scale_reported")
        assertEquals(listOf("fatRatio"), c.metricItems().map { it.key })
        assertTrue(c.sourceDescription.contains("历史"))
    }

    @Test fun noMissingOrDeprecatedMeasurementsAreAddedToGrid() {
        val c = BodyFatCalculator.resolve(input.copy(
            scaleFatRatio = null, scaleWaterRatio = null, scaleMuscleRatio = null, scaleBoneMass = null,
        ))!!
        assertEquals(listOf("fatRatio","ffm","impedance"), c.metricItems().map { it.key })
        assertEquals(0, c.bodyScore)
        assertEquals("", c.bodyType)
    }

    @Test fun nonfiniteOrOutOfRangeMetricsCannotLeakToGrid() {
        val c = BodyComposition(fatRatio = Double.NaN, waterRatio = Double.POSITIVE_INFINITY,
            muscleMass = -2.0, boneMass = Double.NaN, impedance = 2)
        assertTrue(c.metricItems().isEmpty())
        assertNull(c.rawValueOf("fatRatio"))
    }

    @Test fun emptyCompositionHasNoMetrics() {
        assertFalse(BodyComposition().hasAny)
        assertTrue(BodyComposition().metricItems().isEmpty())
        assertNull(BodyComposition().rawValueOf("unknown"))
    }

    @Test fun unversionedRecordsCannotBeMistakenForNativeMeasurements() {
        val c = BodyComposition(fatRatio = 43.8,waterRatio = 41.2,muscleMass = 54.2,boneMass = 3.2,
            impedance = 536,ffm = 57.3)
        assertEquals(listOf(MetricDisplay("impedance","存档阻抗","536 Ω")),c.metricItems())
        assertNull(c.rawValueOf("fatRatio"))
        assertNull(c.rawValueOf("muscleMass"))
    }

    @Test fun declaringNewVersionWithoutInputsDoesNotBypassSourceValidation() {
        val c = BodyComposition(algorithmVersion = 2,fatRatio = 43.8,muscleMass = 54.2,ffm = 57.3)
        assertTrue(c.metricItems().isEmpty())
    }
}
