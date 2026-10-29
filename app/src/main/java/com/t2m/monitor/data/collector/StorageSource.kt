package com.t2m.monitor.data.collector

import android.os.StatFs
import com.t2m.monitor.data.model.StorageSnapshot
import java.io.File
import javax.inject.Inject

/** 存储数据来源，便于测试替换。 */
interface StorageSource {

    fun read(): StorageSnapshot?
}

/**
 * 生产实现：统计应用数据分区（`/data`）的容量。
 *
 * 不申请存储权限，`StatFs` 只读取容量信息。
 */
class AndroidStorageSource @Inject constructor() : StorageSource {

    override fun read(): StorageSnapshot? = runCatching {
        val statFs = StatFs(DATA_DIRECTORY)
        StorageSnapshot(
            totalBytes = statFs.blockCountLong * statFs.blockSizeLong,
            availableBytes = statFs.availableBlocksLong * statFs.blockSizeLong,
        )
    }.getOrNull()

    private companion object {
        val DATA_DIRECTORY: String = File("/data").absolutePath
    }
}
