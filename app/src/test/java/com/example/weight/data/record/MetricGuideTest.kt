package com.example.weight.data.record

import org.junit.Assert.*
import org.junit.Test

class MetricGuideTest {
    @Test fun fatReferenceBoundariesAreConsistent() {
        for ((male, low, normal, high) in listOf(
            listOf(1.0,10.0,20.0,25.0), listOf(0.0,18.0,28.0,33.0),
        )) {
            val sex = male == 1.0
            assertEquals(MetricGuide.Status.LOW, MetricGuide.info("fatRatio",low-0.1,sex)!!.status)
            assertEquals(MetricGuide.Status.NORMAL, MetricGuide.info("fatRatio",low,sex)!!.status)
            assertEquals(MetricGuide.Status.NORMAL, MetricGuide.info("fatRatio",normal,sex)!!.status)
            assertEquals(MetricGuide.Status.HIGH, MetricGuide.info("fatRatio",normal+0.1,sex)!!.status)
            assertEquals(MetricGuide.Status.HIGH, MetricGuide.info("fatRatio",high,sex)!!.status)
            assertEquals(MetricGuide.Status.VERY_HIGH, MetricGuide.info("fatRatio",high+0.1,sex)!!.status)
        }
    }

    @Test fun fatReferenceIsNotAnObesityDiagnosis() {
        val info = MetricGuide.info("fatRatio",22.0,true)!!
        assertEquals(MetricGuide.Status.HIGH, info.status)
        assertTrue(info.rangeText.contains("不是诊断"))
        assertFalse(info.rangeText.contains("属肥胖"))
    }

    @Test fun historicalOrChildDataIsNotClassifiedUsingCurrentProfile() {
        for (c in listOf(BodyComposition(fatRatio = 22.0),
            BodyComposition(fatRatio = 22.0,algorithmVersion = 2, inputs = CompositionInputs(true,15,170,70.0)))) {
            val info = MetricGuide.info("fatRatio",22.0,true,c)!!
            assertNull(info.status)
            assertNull(info.bar)
        }
    }

    @Test fun currentSourceUsesArchivedProfileAndExplainsItsMethod() {
        val c = BodyComposition(fatRatio = 22.0,algorithmVersion = 2,fatMethod = "rfm",
            inputs = CompositionInputs(false,30,165,60.0))
        val info = MetricGuide.info("fatRatio",22.0,false,c)!!
        assertTrue(info.description.contains("腰围"))
        assertTrue(info.rangeText.contains("女 18-28"))
    }

    @Test fun noSyntheticOrDeviceMetricsCreateClinicalBands() {
        for (key in listOf("waterRatio","muscleMass","boneMass","ffm","impedance",
            "visceralFatLevel","subcutaneousFatRatio","proteinRatio","skeletalMuscleRatio","bodyScore","bodyType")) {
            val info = MetricGuide.info(key,10.0,true)!!
            assertNull(info.status)
            assertNull(info.bar)
        }
    }

    @Test fun fullPercentRangeDoesNotClipLegitimateFatValue() {
        val bar = MetricGuide.info("fatRatio",65.5,true)!!.bar!!
        assertEquals(100.0,bar.max,0.0)
        assertEquals(65.5,bar.value,0.0)
        assertEquals(10.0,bar.normalStart,0.0)
        assertEquals(20.0,bar.normalEnd,0.0)
        assertEquals(0.0,bar.segments.first().start,0.0)
        assertEquals(100.0,bar.segments.last().end,0.0)
    }

    @Test fun badOrMissingValuesDoNotGetAnInterpretation() {
        for (v in listOf(Double.NaN,Double.POSITIVE_INFINITY,0.0,-1.0,100.0)) {
            assertNull(MetricGuide.info("fatRatio",v,true))
        }
        assertNull(MetricGuide.info("fatRatio",null,true))
        assertNull(MetricGuide.info("unknown",1.0,true))
    }
}
