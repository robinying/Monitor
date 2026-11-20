package com.t2m.monitor.data.collector

import com.t2m.monitor.data.model.AppStorageUsage
import com.t2m.monitor.data.model.BatterySnapshot
import com.t2m.monitor.data.model.BatteryStatus
import com.t2m.monitor.data.model.FpsSnapshot
import com.t2m.monitor.data.model.PlugState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 采集结果的类型化映射：正确类型保留，错误类型/空存储返回 null。
 */
class MetricValueTest {

    @Test
    fun `maps supported collector values`() {
        val battery = BatterySnapshot(
            percent = 80,
            temperatureC = 30.0,
            currentMicroAmps = -100_000,
            voltageMv = 4_000,
            status = BatteryStatus.DISCHARGING,
            plugged = PlugState.NONE,
            health = 2,
        )
        val fps = FpsSnapshot(fps = 60f)
        val apps = listOf(AppStorageUsage("pkg", "App", 1024L))

        assertEquals(MetricValue.Battery(battery), MetricValue.from(CollectorId.BATTERY, battery))
        assertEquals(MetricValue.Fps(fps), MetricValue.from(CollectorId.FPS, fps))
        assertEquals(MetricValue.AppStorage(apps), MetricValue.from(CollectorId.APP_STORAGE, apps))
    }

    @Test
    fun `rejects a value of the wrong type`() {
        assertNull(MetricValue.from(CollectorId.BATTERY, FpsSnapshot(fps = 60f)))
        assertNull(MetricValue.from(CollectorId.CPU, "not a cpu snapshot"))
    }

    @Test
    fun `rejects an empty app storage list`() {
        assertNull(MetricValue.from(CollectorId.APP_STORAGE, emptyList<AppStorageUsage>()))
    }
}
