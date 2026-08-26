package com.example.weight.data.health

import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class HealthConnectImportWindowTest {

    private val now = Instant.parse("2026-08-26T08:00:00Z")

    @Test
    fun `首次同步读取最近30天`() {
        assertEquals(
            now.minus(Duration.ofDays(30)),
            HealthConnectManager.importWindowStart(lastSyncAt = 0, now = now),
        )
    }

    @Test
    fun `增量同步从上次成功时间前移一天`() {
        val lastSync = Instant.parse("2026-08-25T07:30:00Z")
        assertEquals(
            lastSync.minus(Duration.ofDays(1)),
            HealthConnectManager.importWindowStart(lastSyncAt = lastSync.toEpochMilli(), now = now),
        )
    }
}
