package com.robin.monitor.data.collector

import com.robin.monitor.data.model.BatteryStatus
import com.robin.monitor.data.model.PlugState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 电池读数解析：覆盖比例换算、无效温度、状态与接入方式映射。
 */
class BatteryReadingParserTest {

    private fun reading(
        level: Int = 385,
        scale: Int = 500,
        temperature: Int? = 364,
        voltageMv: Int? = 4200,
        currentUa: Int? = -820_000,
        status: Int = 3,
        plugged: Int = 0,
        health: Int? = 2,
        technology: String? = "Li-ion",
        cycleCount: Int? = 321,
        designCapacityMah: Int? = 4500,
    ) = BatteryReading(
        level = level,
        scale = scale,
        temperatureTenthsCelsius = temperature,
        voltageMv = voltageMv,
        currentMicroAmps = currentUa,
        status = status,
        plugged = plugged,
        health = health,
        technology = technology,
        cycleCount = cycleCount,
        designCapacityMah = designCapacityMah,
    )

    @Test
    fun `converts level and temperature`() {
        val snapshot = BatteryReadingParser.parse(reading())
        assertEquals(77, snapshot.percent)
        assertEquals(36.4, snapshot.temperatureC!!, 0.001)
        assertEquals(-820_000, snapshot.currentMicroAmps)
        assertEquals(4200, snapshot.voltageMv)
        assertEquals(-3.444, snapshot.powerWatts!!, 0.001)
        assertEquals("Li-ion", snapshot.technology)
        assertEquals(321, snapshot.cycleCount)
        assertEquals(4500, snapshot.designCapacityMah)
    }

    @Test
    fun `returns null percent when scale is invalid`() {
        assertNull(BatteryReadingParser.parse(reading(scale = 0)).percent)
        assertNull(BatteryReadingParser.parse(reading(level = -1)).percent)
    }

    @Test
    fun `treats non positive temperature as unavailable`() {
        assertNull(BatteryReadingParser.parse(reading(temperature = 0)).temperatureC)
        assertNull(BatteryReadingParser.parse(reading(temperature = -5)).temperatureC)
        assertNull(BatteryReadingParser.parse(reading(temperature = null)).temperatureC)
    }

    @Test
    fun `power is null when current or voltage is unavailable`() {
        assertNull(BatteryReadingParser.parse(reading(currentUa = null)).powerWatts)
        assertNull(BatteryReadingParser.parse(reading(voltageMv = null)).powerWatts)
    }

    @Test
    fun `maps battery status values`() {
        assertEquals(BatteryStatus.CHARGING, BatteryReadingParser.parse(reading(status = 2)).status)
        assertEquals(BatteryStatus.DISCHARGING, BatteryReadingParser.parse(reading(status = 3)).status)
        assertEquals(BatteryStatus.NOT_CHARGING, BatteryReadingParser.parse(reading(status = 4)).status)
        assertEquals(BatteryStatus.FULL, BatteryReadingParser.parse(reading(status = 5)).status)
        assertEquals(BatteryStatus.UNKNOWN, BatteryReadingParser.parse(reading(status = 1)).status)
    }

    @Test
    fun `maps plug state from bitmask`() {
        assertEquals(PlugState.NONE, BatteryReadingParser.parse(reading(plugged = 0)).plugged)
        assertEquals(PlugState.AC, BatteryReadingParser.parse(reading(plugged = 1)).plugged)
        assertEquals(PlugState.USB, BatteryReadingParser.parse(reading(plugged = 2)).plugged)
        assertEquals(PlugState.WIRELESS, BatteryReadingParser.parse(reading(plugged = 4)).plugged)
        assertEquals(PlugState.DOCK, BatteryReadingParser.parse(reading(plugged = 8)).plugged)
        assertEquals(PlugState.USB, BatteryReadingParser.parse(reading(plugged = 3)).plugged)
    }
}
