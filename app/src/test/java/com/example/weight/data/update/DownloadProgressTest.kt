package com.example.weight.data.update

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadProgressTest {

    @Test
    fun `formatBytes 边界与单位换算`() {
        assertEquals("0 B", formatBytes(0))
        assertEquals("0 B", formatBytes(-1))
        assertEquals("512 B", formatBytes(512))
        assertEquals("1.0 KB", formatBytes(1024))
        assertEquals("1.5 KB", formatBytes(1536))
        assertEquals("1.0 MB", formatBytes(1024L * 1024))
        assertEquals("1.0 GB", formatBytes(1024L * 1024 * 1024))
        // 超出 GB 不再进位，停在最后一个单位
        assertEquals("1024.0 GB", formatBytes(1024L * 1024 * 1024 * 1024))
    }

    @Test
    fun `formatSpeed 追加每秒后缀`() {
        assertEquals("0 B/s", formatSpeed(0))
        assertEquals("2.0 MB/s", formatSpeed(2 * 1024 * 1024))
    }

    @Test
    fun `formatRemaining 未知秒数提示计算中`() {
        assertEquals("计算中…", formatRemaining(-1))
    }

    @Test
    fun `formatRemaining 秒与分秒`() {
        assertEquals("剩余 30秒", formatRemaining(30))
        assertEquals("剩余 59秒", formatRemaining(59))
        assertEquals("剩余 1分20秒", formatRemaining(80))
        assertEquals("剩余 3分0秒", formatRemaining(180))
    }
}
