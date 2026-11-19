package com.t2m.monitor.data.collector

import com.t2m.monitor.data.model.ThermalStatusLevel
import com.t2m.monitor.data.model.ThermalZoneKind
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
    fun `classifies thermal zones by purpose`() {
        assertEquals(ThermalZoneKind.CPU, ThermalParser.classifyZone("cpu-1-3-1"))
        assertEquals(ThermalZoneKind.GPU, ThermalParser.classifyZone("gpu-0-0"))
        assertEquals(ThermalZoneKind.BATTERY, ThermalParser.classifyZone("battery"))
        assertEquals(ThermalZoneKind.SKIN, ThermalParser.classifyZone("skin-temp"))
        assertEquals(ThermalZoneKind.OTHER, ThermalParser.classifyZone("charger"))
    }

    @Test
    fun `maps thermal status and rejects unknown values`() {
        assertEquals(ThermalStatusLevel.NONE, ThermalParser.parseStatus(0))
        assertEquals(ThermalStatusLevel.MODERATE, ThermalParser.parseStatus(2))
        assertEquals(ThermalStatusLevel.SHUTDOWN, ThermalParser.parseStatus(6))
        assertNull(ThermalParser.parseStatus(-1))
        assertNull(ThermalParser.parseStatus(7))
    }

    @Test
    fun `thermal headroom rejects missing HAL values`() {
        assertEquals(0.42f, ThermalParser.classifyHeadroom(0.42f)!!, 0.01f)
        assertNull(ThermalParser.classifyHeadroom(Float.NaN))
        assertNull(ThermalParser.classifyHeadroom(Float.POSITIVE_INFINITY))
        assertNull(ThermalParser.classifyHeadroom(-0.1f))
    }

    @Test
    fun `rejects invalid readings`() {
        assertNull(ThermalParser.normalize(0f))
        assertNull(ThermalParser.normalize(-5f))
        assertNull(ThermalParser.normalize(200_000f))
    }
}
