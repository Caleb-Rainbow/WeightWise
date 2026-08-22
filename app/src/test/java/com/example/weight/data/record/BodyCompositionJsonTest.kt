package com.example.weight.data.record

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BodyCompositionJsonTest {

    @Test
    fun `编解码往返`() {
        val c = BodyComposition(fatRatio = 22.5, waterRatio = 55.1, muscleRatio = 45.0, impedance = 512)
        val decoded = BodyCompositionJson.decode(BodyCompositionJson.encode(c))
        assertEquals(c, decoded)
    }

    @Test
    fun `旧记录空串与null返回null`() {
        assertNull(BodyCompositionJson.decode(""))
        assertNull(BodyCompositionJson.decode(null))
    }

    @Test
    fun `旧JSON缺键字段取默认零值`() {
        // 早期版本只存 fatRatio 的 JSON：新字段反序列化为 0，不抛异常
        val decoded = BodyCompositionJson.decode("""{"fatRatio":20.0}""")
        assertEquals(20.0, decoded!!.fatRatio, 0.001)
        assertEquals(0, decoded.impedance)
        assertTrue(decoded.hasAny)
    }

    @Test
    fun `坏JSON返回null不抛异常`() {
        assertNull(BodyCompositionJson.decode("not-json{"))
    }

    @Test
    fun `全零hasAny为false`() {
        assertFalse(BodyComposition().hasAny)
    }
}
