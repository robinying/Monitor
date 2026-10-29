package com.t2m.monitor.core

import android.util.Log

/**
 * 轻量日志抽象。
 *
 * 采集异常必须记录日志（规范要求 catch 块不得为空），单元测试注入假实现以避免依赖 Android。
 */
interface MonitorLogger {

    fun warn(message: String, throwable: Throwable? = null)
}

/** 生产实现：统一打 tag，release 版本由 R8 移除调试级日志。 */
class AndroidMonitorLogger : MonitorLogger {

    override fun warn(message: String, throwable: Throwable?) {
        Log.w(TAG, message, throwable)
    }

    private companion object {
        const val TAG = "T2mMonitor"
    }
}
