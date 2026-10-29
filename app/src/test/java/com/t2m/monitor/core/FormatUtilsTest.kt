package com.t2m.monitor.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 格式化工具的单测：覆盖正常值、边界值与不可用值。
 */
class FormatUtilsTest {

    @Test
    fun `formats bytes with binary units`() {
        assertEquals("0 B", FormatUtils.formatBytes(0L))
        assertEquals("1.5 KB", FormatUtils.formatBytes(1536L))
        assertEquals("5.0 GB", FormatUtils.formatBytes(5L * 1024 * 1024 * 1024))
        assertEquals("--", FormatUtils.formatBytes(null))
        assertEquals("--", FormatUtils.formatBytes(-1L))
    }

    @Test
    fun `formats percent with rounding`() {
        assertEquals("62%", FormatUtils.formatPercent(62.4f))
        assertEquals("100%", FormatUtils.formatPercent(100f))
        assertEquals("--", FormatUtils.formatPercent(null))
    }

    @Test
    fun `formats temperature in celsius`() {
        assertEquals("36.4 ℃", FormatUtils.formatTemperature(36.44))
        assertEquals("--", FormatUtils.formatTemperature(null))
    }

    @Test
    fun `formats current keeping sign`() {
        assertEquals("-820 mA", FormatUtils.formatCurrent(-820_000))
        assertEquals("0 mA", FormatUtils.formatCurrent(0))
        assertEquals("--", FormatUtils.formatCurrent(null))
    }

    @Test
    fun `formats voltage in volts`() {
        assertEquals("4.20 V", FormatUtils.formatVoltage(4200))
        assertEquals("--", FormatUtils.formatVoltage(null))
    }

    @Test
    fun `formats frequency switching unit by magnitude`() {
        assertEquals("2.80 GHz", FormatUtils.formatFrequency(2_800_000))
        assertEquals("800 MHz", FormatUtils.formatFrequency(800_000))
        assertEquals("--", FormatUtils.formatFrequency(0))
        assertEquals("--", FormatUtils.formatFrequency(null))
    }
    @Test
    fun `formats compact temperature for overlay bubble`() {
        assertEquals("36°", FormatUtils.formatTemperatureCompact(36.4))
        assertEquals("--", FormatUtils.formatTemperatureCompact(null))
    }

    @Test
    fun `formats time of day and treats zero as unavailable`() {
        assertEquals("--", FormatUtils.formatTimeOfDay(0L))
        assertEquals("--", FormatUtils.formatTimeOfDay(-1L))
        assertTrue(
            Regex("\\d{2}:\\d{2}:\\d{2}")
                .matches(FormatUtils.formatTimeOfDay(1_700_000_000_000L)),
        )
    }
    @Test
    fun `formats fps`() {
        assertEquals("60 fps", FormatUtils.formatFps(59.6f))
        assertEquals("--", FormatUtils.formatFps(null))
    }
}
