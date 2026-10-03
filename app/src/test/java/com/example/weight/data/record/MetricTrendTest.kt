package com.example.weight.data.record

import com.example.weight.data.scale.BodyFatCalculator
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

class MetricTrendTest {
    private fun time(day: String, hour: Int = 8, minute: Int = 0): Long =
        LocalDateTime.parse("${day}T${"%02d".format(hour)}:${"%02d".format(minute)}:00")
            .toInstant(ZoneOffset.ofHours(8)).toEpochMilli()
    private val input = CompositionInputs(true,30,170,70.0,1_700_000_000_000L,
        impedanceOhm = 500.0, biaMethod = BiaMethod.FOOT_TO_FOOT, sourceId = "scale-A",
        scaleFatRatio = 20.0, scaleWaterRatio = 50.0, scaleMuscleRatio = 40.0, scaleBoneMass = 3.0)
    private val sample get() = BodyFatCalculator.resolve(input)!!
    private fun raw(ts: Long,c: BodyComposition) = RecordCompositionRaw(ts,BodyCompositionJson.encode(c))
    private fun point(day: Int,c: BodyComposition = sample) =
        MetricPoint("2026-08-${"%02d".format(day)}",time("2026-08-${"%02d".format(day)}"),c)

    @Test fun sameDayKeepsLastSnapshot() {
        val points = dailyLastCompositions(listOf(
            raw(time("2026-08-20",8),sample.copy(fatRatio = 21.0)),
            raw(time("2026-08-20",21),sample.copy(fatRatio = 20.0))))
        assertEquals(1,points.size)
        assertEquals(20.0,points[0].composition.fatRatio,0.0)
    }

    @Test fun beijingMidnightAndMissingDaysRemainSeparate() {
        val points = dailyLastCompositions(listOf(
            raw(time("2026-08-20",23,59),sample),
            raw(time("2026-08-21",0,1),sample),
            raw(time("2026-08-23"),sample)))
        assertEquals(listOf("2026-08-20","2026-08-21","2026-08-23"),points.map { it.day })
    }

    @Test fun corruptAndEmptySnapshotsAreSkipped() {
        assertTrue(dailyLastCompositions(listOf(RecordCompositionRaw(1L,"oops"),
            RecordCompositionRaw(2L,""),raw(3L,BodyComposition()))).isEmpty())
        assertTrue(dailyLastCompositions(emptyList()).isEmpty())
    }

    @Test fun supportedMetricSetMatchesGrid() {
        assertEquals(sample.metricItems().map { it.key },TrendMetric.entries.map { it.key })
        for (metric in TrendMetric.entries) {
            assertEquals(metric,TrendMetric.fromKey(metric.key))
            assertNotNull(metric.valueOf(sample))
            assertNull(metric.valueOf(BodyComposition()))
        }
        assertNull(TrendMetric.fromKey("bodyScore"))
        assertNull(TrendMetric.fromKey("visceralFatLevel"))
    }

    @Test fun deprecatedLegacyEstimatesDoNotAppearInFatTrend() {
        val legacy = BodyComposition(fatRatio = 43.8,impedance = 536,fatMethod = "sun2003")
        assertNull(TrendMetric.FAT_RATIO.valueOf(legacy))
        assertEquals(536.0,TrendMetric.IMPEDANCE.valueOf(legacy)!!,0.0)
        assertTrue(comparableMetricSeries(listOf(point(20,legacy)),TrendMetric.FAT_RATIO).isEmpty())
    }

    @Test fun differentMethodsAndVersionsCannotBeJoined() {
        val a = point(18,sample.copy(fatMethod = "rfm"))
        val b = point(19,sample.copy(algorithmVersion = 3))
        val c = point(20)
        val d = point(21)
        assertEquals(listOf(c,d),comparableMetricSeries(listOf(a,b,c,d),TrendMetric.FAT_RATIO))
    }

    @Test fun deviceSwitchAndReturningToOriginalDeviceDoNotBridge() {
        val a = point(18)
        val b = point(19,sample.copy(inputs = input.copy(sourceId = "scale-B")))
        val c = point(20)
        assertEquals(listOf(c),comparableMetricSeries(listOf(a,b,c),TrendMetric.FAT_RATIO))
        assertEquals(listOf(c),comparableMetricSeries(listOf(a,b,c),TrendMetric.IMPEDANCE))
    }

    @Test fun changedHistoricalProfileSplitsSeries() {
        for (changed in listOf(input.copy(sexMale = false),input.copy(heightCm = 180),input.copy(age = 31))) {
            val a = point(19,sample.copy(inputs = changed))
            val b = point(20)
            assertEquals(listOf(b),comparableMetricSeries(listOf(a,b),TrendMetric.FAT_RATIO))
        }
    }

    @Test fun hiddenInvalidMethodIsStillABarrierBetweenValidRuns() {
        val a = point(18)
        val hidden = point(19, BodyComposition(fatRatio = 43.8, impedance = 536, fatMethod = "sun2003"))
        val b = point(20)
        assertEquals(listOf(b), comparableMetricSeries(listOf(a,hidden,b),TrendMetric.FAT_RATIO))
    }

    @Test fun nativeWaterDoesNotChangeMethodWhenFatFallbackChanges() {
        val a = point(19,sample.copy(fatMethod = "rfm"))
        val b = point(20,sample.copy(fatMethod = "deurenberg"))
        assertEquals(listOf(a,b),comparableMetricSeries(listOf(a,b),TrendMetric.WATER))
    }

    @Test fun unknownLegacySourceShowsOnlyLatestPoint() {
        val a = point(19,BodyComposition(fatRatio = 20.0,fatMethod = "scale_reported"))
        val b = point(20,BodyComposition(fatRatio = 21.0,fatMethod = "scale_reported"))
        assertEquals(listOf(b),comparableMetricSeries(listOf(a,b),TrendMetric.FAT_RATIO))
    }

    @Test fun unavailableMetricProducesNoSeries() {
        assertTrue(comparableMetricSeries(listOf(point(20,BodyComposition(impedance = 500))),
            TrendMetric.FAT_RATIO).isEmpty())
        assertEquals("20.0",TrendMetric.FAT_RATIO.formatValue(20.0))
        assertEquals("500",TrendMetric.IMPEDANCE.formatValue(500.0))
    }
}
