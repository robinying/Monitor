package com.t2m.monitor.data.privileged

import javax.inject.Inject

/** 系统帧率数据来源，便于测试替换。 */
interface SystemFpsSource {

    fun isAvailable(): Boolean

    fun readFps(): Float?
}

/** 生产实现：Shizuku + `dumpsys SurfaceFlinger`。 */
class ShizukuFpsSource @Inject constructor(
    private val shell: ShizukuShell,
) : SystemFpsSource {

    override fun isAvailable(): Boolean = shell.isGranted()

    override fun readFps(): Float? {
        val layers = shell.exec("dumpsys", "SurfaceFlinger", "--list") ?: return null
        val layer = FpsParser.selectLayer(layers) ?: return null
        val latency = shell.exec("dumpsys", "SurfaceFlinger", "--latency", layer) ?: return null
        return FpsParser.parseLatency(latency, System.nanoTime())
    }
}
