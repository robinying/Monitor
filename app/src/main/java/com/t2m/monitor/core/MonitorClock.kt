package com.t2m.monitor.core

import android.os.SystemClock

/**
 * 时钟抽象。
 *
 * 采样周期与时间戳统一从该接口取，便于单元测试注入虚拟时间。
 */
interface MonitorClock {

    /** 单调递增的毫秒时间，用于计算采样间隔。 */
    fun elapsedRealtimeMillis(): Long

    /** 墙上时钟毫秒，用于数据时间戳与展示。 */
    fun currentTimeMillis(): Long
}

/** 生产实现：分别对应 [SystemClock.elapsedRealtime] 与 [System.currentTimeMillis]。 */
class SystemMonitorClock : MonitorClock {

    override fun elapsedRealtimeMillis(): Long = SystemClock.elapsedRealtime()

    override fun currentTimeMillis(): Long = System.currentTimeMillis()
}
