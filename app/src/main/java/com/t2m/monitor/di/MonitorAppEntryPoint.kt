package com.t2m.monitor.di

import com.t2m.monitor.core.MonitorRuntime
import com.t2m.monitor.data.prefs.MonitorPreferences
import com.t2m.monitor.overlay.OverlayController
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * 应用级只读入口。
 *
 * 供仪器测试获取单例运行时状态；生产代码不直接使用，避免 Service/UI 绕过 Hilt 注入。
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface MonitorAppEntryPoint {
    fun runtime(): MonitorRuntime
    fun preferences(): MonitorPreferences
    fun overlayController(): OverlayController
}
