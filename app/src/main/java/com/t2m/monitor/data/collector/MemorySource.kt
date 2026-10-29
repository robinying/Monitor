package com.t2m.monitor.data.collector

import android.app.ActivityManager
import android.content.Context
import com.t2m.monitor.data.model.MemorySnapshot
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** 内存数据来源，便于测试替换。 */
interface MemorySource {

    fun read(): MemorySnapshot?
}

/** 生产实现：读取 [ActivityManager.MemoryInfo]。 */
class AndroidMemorySource @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : MemorySource {

    override fun read(): MemorySnapshot? {
        val activityManager = context.getSystemService(ActivityManager::class.java) ?: return null
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        return MemorySnapshot(
            totalBytes = memoryInfo.totalMem,
            availBytes = memoryInfo.availMem,
            lowMemory = memoryInfo.lowMemory,
        )
    }
}
