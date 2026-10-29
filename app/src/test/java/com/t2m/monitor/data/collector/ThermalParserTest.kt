package com.t2m.monitor.data.collector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 温区原始值归一化：毫摄氏度、十分之一度、摄氏度与无效值。
 */
class ThermalParserTest {

    @Test
    fun `normalizes millidegrees`() {
        assertEquals(45f, ThermalParser.normalize(45_000f)!!, 0.01f)
        assertEquals(36.5f, ThermalParser.normalize(36_500f)!!, 0.01f)
    }

    @Test
    fun `normalizes celsius and tenths`() {
        assertEquals(45f, ThermalParser.normalize(45f)!!, 0.01f)
        assertEquals(45f, ThermalParser.normalize(450f)!!, 0.01f)
    }

    @Test
    fun `rejects invalid readings`() {
        assertNull(ThermalParser.normalize(0f))
        assertNull(ThermalParser.normalize(-5f))
        assertNull(ThermalParser.normalize(200_000f))
    }
}
