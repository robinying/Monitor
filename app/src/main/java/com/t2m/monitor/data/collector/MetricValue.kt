package com.t2m.monitor.data.collector

import com.t2m.monitor.data.model.AppStorageUsage
import com.t2m.monitor.data.model.BatterySnapshot
import com.t2m.monitor.data.model.CpuSnapshot
import com.t2m.monitor.data.model.FpsSnapshot
import com.t2m.monitor.data.model.MemorySnapshot
import com.t2m.monitor.data.model.StorageSnapshot
import com.t2m.monitor.data.model.ThermalSnapshot
import com.t2m.monitor.data.model.UsageSnapshot

/**
 * 采集结果的类型化表示。
 *
 * 引擎只接收 [MetricValue]，不再从 `Map<CollectorId, Any>` 做一排 `as?`；类型转换集中
 * 在 [from] 一处，无法识别的返回值由调度器标记为失败，避免“值被静默丢掉但状态仍是 OK”。
 */
sealed interface MetricValue {

    data class Battery(val value: BatterySnapshot) : MetricValue

    data class Cpu(val value: CpuSnapshot) : MetricValue

    data class Memory(val value: MemorySnapshot) : MetricValue

    data class Storage(val value: StorageSnapshot) : MetricValue

    data class Thermal(val value: ThermalSnapshot) : MetricValue

    data class Usage(val value: UsageSnapshot) : MetricValue

    data class Fps(val value: FpsSnapshot) : MetricValue

    data class AppStorage(val value: List<AppStorageUsage>) : MetricValue

    companion object {
        fun from(id: CollectorId, value: Any): MetricValue? = when (id) {
            CollectorId.BATTERY -> (value as? BatterySnapshot)?.let(::Battery)
            CollectorId.CPU -> (value as? CpuSnapshot)?.let(::Cpu)
            CollectorId.MEMORY -> (value as? MemorySnapshot)?.let(::Memory)
            CollectorId.STORAGE -> (value as? StorageSnapshot)?.let(::Storage)
            CollectorId.THERMAL -> (value as? ThermalSnapshot)?.let(::Thermal)
            CollectorId.USAGE -> (value as? UsageSnapshot)?.let(::Usage)
            CollectorId.FPS -> (value as? FpsSnapshot)?.let(::Fps)
            CollectorId.APP_STORAGE -> (value as? List<*>)
                ?.filterIsInstance<AppStorageUsage>()
                ?.takeIf { apps -> apps.isNotEmpty() }
                ?.let(::AppStorage)
        }
    }
}
