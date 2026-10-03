package com.example.weight.data.scale

import com.example.weight.data.record.BiaMethod
import com.example.weight.data.record.BodyCompositionJson
import com.example.weight.data.record.CompositionInputs
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.util.TimeZone

class BodyFatCalculatorTest {
    private val time = Instant.parse("2026-10-03T04:00:00Z").toEpochMilli()
    private fun input() = CompositionInputs(
        sexMale = true, age = 24, heightCm = 185, weightKg = 101.9,
        measuredAt = time, impedanceOhm = 536.0, biaMethod = BiaMethod.FOOT_TO_FOOT,
        sourceId = "scale-A",
    )

    @Test fun sunMaleIncludesResistanceTermFromPaperTable5() {
        val c = BodyFatCalculator.resolve(input().copy(biaMethod = BiaMethod.WRIST_TO_ANKLE_50KHZ))!!
        assertEquals(68.038197761194, c.ffm, 1e-9)
        assertEquals(33.230424179397, c.fatRatio, 1e-9)
        assertEquals("sun2003", c.fatMethod)
    }

    @Test fun sunFemaleIncludesResistanceTerm() {
        val c = BodyFatCalculator.resolve(input().copy(
            sexMale = false, age = 30, heightCm = 165, weightKg = 60.0,
            impedanceOhm = 500.0, biaMethod = BiaMethod.WRIST_TO_ANKLE_50KHZ,
        ))!!
        assertEquals(48.2405, c.ffm, 1e-9)
        assertEquals(19.5991666666667, c.fatRatio, 1e-9)
    }

    @Test fun footAndUnknownImpedanceNeverUseWristModel() {
        for (method in listOf(BiaMethod.FOOT_TO_FOOT, BiaMethod.UNKNOWN)) {
            val a = BodyFatCalculator.resolve(input().copy(biaMethod = method))!!
            val b = BodyFatCalculator.resolve(input().copy(biaMethod = method, impedanceOhm = 700.0))!!
            assertEquals("deurenberg", a.fatMethod)
            assertEquals(a.fatRatio, b.fatRatio, 0.0)
            assertEquals(0.0, a.skeletalMuscleMass, 0.0)
        }
    }

    @Test fun unknownGeometryIsTheDefaultForPublicEntry() {
        assertEquals("deurenberg", BodyFatCalculator.calculate(true,24,185,101.9,536.0)!!.fatMethod)
    }

    @Test fun scaleResultsTakePriorityAndNativeValuesArePreserved() {
        val c = BodyFatCalculator.resolve(input().copy(
            scaleFatRatio = 20.0, scaleWaterRatio = 50.0, scaleMuscleRatio = 40.0,
            scaleBoneMass = 3.0, waistCm = 100.0, waistMeasuredAt = time,
        ))!!
        assertEquals("scale_reported", c.fatMethod)
        assertEquals(20.0, c.fatRatio, 0.0)
        assertEquals(50.0, c.waterRatio, 0.0)
        assertEquals(40.76, c.muscleMass, 1e-9)
        assertEquals(3.0, c.boneMass, 0.0)
    }

    @Test fun currentWaistProducesRfmWithoutFusion() {
        val c = BodyFatCalculator.resolve(input().copy(waistCm = 100.0, waistMeasuredAt = time))!!
        assertEquals("rfm", c.fatMethod)
        assertEquals(27.0, c.fatRatio, 0.0)
        assertEquals(74.387, c.ffm, 1e-9)
        assertEquals(0.0, c.waterRatio, 0.0)
        assertEquals(0.0, c.muscleMass, 0.0)
    }

    @Test fun rfmUsesFemaleSexTerm() {
        val c = BodyFatCalculator.resolve(input().copy(
            sexMale = false, heightCm = 165, weightKg = 60.0, waistCm = 75.0, waistMeasuredAt = time,
        ))!!
        assertEquals(32.0, c.fatRatio, 1e-9)
    }

    @Test fun staleUndatedAndFutureWaistAreNotReused() {
        for (date in listOf(0L, time - 86_400_000L, time + 1L)) {
            val c = BodyFatCalculator.resolve(input().copy(waistCm = 100.0, waistMeasuredAt = date))!!
            assertEquals("deurenberg", c.fatMethod)
        }
    }

    @Test fun waistDayUsesBeijingRegardlessOfDeviceTimezone() {
        val old = TimeZone.getDefault()
        try {
            for (zone in listOf("UTC", "America/Los_Angeles", "Pacific/Kiritimati", "Asia/Shanghai")) {
                TimeZone.setDefault(TimeZone.getTimeZone(zone))
                val measured = Instant.parse("2026-10-02T16:01:00Z").toEpochMilli()
                assertEquals("deurenberg", BodyFatCalculator.resolve(input().copy(
                    measuredAt = measured, waistCm = 100.0, waistMeasuredAt = measured - 120_000,
                ))!!.fatMethod)
                assertEquals("rfm", BodyFatCalculator.resolve(input().copy(
                    measuredAt = measured, waistCm = 100.0, waistMeasuredAt = measured - 30_000,
                ))!!.fatMethod)
            }
        } finally { TimeZone.setDefault(old) }
    }

    @Test fun waistSettingRangeAndCalculatorRangeAgree() {
        val c = BodyFatCalculator.resolve(input().copy(
            heightCm = 100, weightKg = 30.0, waistCm = 40.0, waistMeasuredAt = time,
        ))!!
        assertEquals("rfm", c.fatMethod)
        assertEquals(14.0, c.fatRatio, 1e-9)
    }

    @Test fun adultBmiFormulaDoesNotRunForAge15() {
        assertNull(BodyFatCalculator.resolve(input().copy(
            age = 15, heightCm = 170, weightKg = 57.8, impedanceOhm = null,
        )))
        val c = BodyFatCalculator.resolve(input().copy(
            age = 16, heightCm = 170, weightKg = 57.8, impedanceOhm = null,
        ))!!
        assertEquals(11.48, c.fatRatio, 1e-9)
        assertEquals("", c.bodyType)
        assertEquals(0, c.bodyScore)
    }

    @Test fun outOfDomainAgeAndBmiProduceOnlyRawMeasurement() {
        for (sample in listOf(input().copy(age = 90), input().copy(weightKg = 150.0))) {
            val c = BodyFatCalculator.resolve(sample)!!
            assertEquals(0.0, c.fatRatio, 0.0)
            assertEquals(536, c.impedance)
        }
        assertNull(BodyFatCalculator.resolve(input().copy(age = 90, impedanceOhm = null)))
    }

    @Test fun rfmAgeDomainIsSeparateFromBmiDomain() {
        for (age in listOf(19, 70)) {
            assertEquals("deurenberg", BodyFatCalculator.resolve(input().copy(
                age = age, waistCm = 100.0, waistMeasuredAt = time,
            ))!!.fatMethod)
        }
    }

    @Test fun missingOrInvalidProfileIsRejected() {
        for (sample in listOf(input().copy(sexMale = null), input().copy(age = 0),
            input().copy(age = 101), input().copy(heightCm = 0), input().copy(heightCm = 251))) {
            assertNull(BodyFatCalculator.resolve(sample))
        }
    }

    @Test fun nonfiniteAndInvalidWeightCannotCrashOrReachJson() {
        for (w in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 0.0, -1.0, 401.0)) {
            assertNull(BodyFatCalculator.resolve(input().copy(weightKg = w)))
        }
    }

    @Test fun malformedOptionalInputsAreIgnoredAndArchiveRemainsSerializable() {
        for (v in listOf(Double.NaN, Double.POSITIVE_INFINITY, -1.0)) {
            val c = BodyFatCalculator.resolve(input().copy(
                impedanceOhm = v, scaleFatRatio = v, scaleWaterRatio = v,
                scaleMuscleRatio = v, scaleBoneMass = v, waistCm = v,
            ))!!
            assertNotNull(BodyCompositionJson.decode(BodyCompositionJson.encode(c)))
            assertEquals("deurenberg", c.fatMethod)
            assertEquals(0, c.impedance)
        }
    }

    @Test fun fatAbove60IsNotClippedAndMassBalanceIsExact() {
        val c = BodyFatCalculator.resolve(input().copy(weightKg = 120.0, scaleFatRatio = 65.5))!!
        assertEquals(65.5, c.fatRatio, 0.0)
        assertEquals(41.4, c.ffm, 1e-9)
        assertEquals(c.fatRatio, (1-c.ffm/120.0)*100, 1e-9)
    }

    @Test fun waterOutsideOldDisplayBandIsNotClipped() {
        val c = BodyFatCalculator.resolve(input().copy(scaleFatRatio = 10.0, scaleWaterRatio = 70.0))!!
        assertEquals(70.0, c.waterRatio, 0.0)
    }

    @Test fun muscleRatioAndMassAlwaysAgree() {
        val c = BodyFatCalculator.resolve(input().copy(scaleFatRatio = 10.0, scaleMuscleRatio = 64.6))!!
        assertEquals(64.6, c.muscleRatio, 0.0)
        assertEquals(64.6, c.muscleMass/c.inputs!!.weightKg*100.0, 1e-9)
    }

    @Test fun conflictingNativeComponentsAreRejected() {
        val c = BodyFatCalculator.resolve(input().copy(
            scaleFatRatio = 80.0, scaleWaterRatio = 60.0, scaleMuscleRatio = 60.0, scaleBoneMass = 30.0,
        ))!!
        assertEquals(0.0, c.waterRatio, 0.0)
        assertEquals(0.0, c.muscleMass, 0.0)
        assertEquals(0.0, c.boneMass, 0.0)
    }

    @Test fun unsupportedSyntheticIndicatorsAreAbsentForEveryMethod() {
        val samples = listOf(input(), input().copy(scaleFatRatio = 20.0),
            input().copy(waistCm = 100.0, waistMeasuredAt = time),
            input().copy(biaMethod = BiaMethod.WRIST_TO_ANKLE_50KHZ))
        for (sample in samples) {
            val c = BodyFatCalculator.resolve(sample)!!
            assertEquals(0, c.bodyScore)
            assertEquals("", c.bodyType)
            assertEquals(0, c.visceralFatLevel)
            assertEquals(0.0, c.proteinRatio, 0.0)
            assertEquals(0.0, c.subcutaneousFatRatio, 0.0)
            assertEquals(0.0, c.skeletalMuscleRatio, 0.0)
        }
    }

    @Test fun versionAndOriginalProfileRoundTripWithMeasurement() {
        val original = input().copy(waistCm = 100.0, waistMeasuredAt = time)
        val c = BodyFatCalculator.resolve(original)!!
        val restored = BodyCompositionJson.decode(BodyCompositionJson.encode(c))!!
        assertEquals(2, restored.algorithmVersion)
        assertEquals(original, restored.inputs)
        assertEquals(c, restored)
    }

    @Test fun veryLowSunFatIsNotClippedToThreePercent() {
        val c = BodyFatCalculator.resolve(input().copy(
            heightCm = 175, weightKg = 70.0, impedanceOhm = 370.0,
            biaMethod = BiaMethod.WRIST_TO_ANKLE_50KHZ,
        ))!!
        assertEquals("sun2003", c.fatMethod)
        assertTrue(c.fatRatio < 3.0)
        assertEquals(c.fatRatio, (1-c.ffm/70.0)*100, 1e-9)
    }
}
