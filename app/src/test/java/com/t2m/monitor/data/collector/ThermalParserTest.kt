package com.t2m.monitor.data.collector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
    fun `excludes non temperature thermal zones`() {
        assertTrue(ThermalParser.isTemperatureZone("cpu-1-3-1"))
        assertTrue(ThermalParser.isTemperatureZone("battery"))
        assertFalse(ThermalParser.isTemperatureZone("vbat"))
        assertFalse(ThermalParser.isTemperatureZone("ibat"))
        assertFalse(ThermalParser.isTemperatureZone("pmxr2230-bcl-lvl0"))
        assertFalse(ThermalParser.isTemperatureZone("battery_current"))
    }

    @Test
    fun `rejects invalid readings`() {
        assertNull(ThermalParser.normalize(0f))
        assertNull(ThermalParser.normalize(-5f))
        assertNull(ThermalParser.normalize(200_000f))
    }
}
